package com.globaldashboard.auth.grpc;

import com.globaldashboard.auth.client.KafkaUserClient;
import com.globaldashboard.auth.event.user.UserEvent;
import com.globaldashboard.auth.proto.AuthServiceGrpc;
import com.globaldashboard.auth.proto.LoginRequest;
import com.globaldashboard.auth.proto.LoginResponse;
import com.globaldashboard.auth.proto.VerifyRequest;
import com.globaldashboard.auth.proto.VerifyResponse;
import com.globaldashboard.auth.service.SessionService;
import io.grpc.stub.StreamObserver;
import io.micronaut.grpc.annotation.GrpcService;
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator;
import io.micronaut.security.token.jwt.validator.JwtTokenValidator;
import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

@GrpcService
public class AuthEndpoint extends AuthServiceGrpc.AuthServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(AuthEndpoint.class);

    private final KafkaUserClient userClient;
    private final SessionService sessionService;
    private final JwtTokenGenerator tokenGenerator;
    private final JwtTokenValidator tokenValidator;

    @Inject
    public AuthEndpoint(KafkaUserClient userClient,
            SessionService sessionService,
            JwtTokenGenerator tokenGenerator,
            JwtTokenValidator tokenValidator) {
        this.userClient = userClient;
        this.sessionService = sessionService;
        this.tokenGenerator = tokenGenerator;
        this.tokenValidator = tokenValidator;
    }

    @Override
    public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
        try {
            // 1. Fetch user from DB via Kafka
            // Timeout 5 seconds
            UserEvent userEvent = userClient.findUser(request.getUsername())
                    .get(5, TimeUnit.SECONDS);

            if (userEvent.type() == UserEvent.EventType.NOT_FOUND || userEvent.password() == null) {
                responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
                        .withDescription("Invalid credentials (User not found)").asRuntimeException());
                return;
            }

            // 2. Verify Password
            if (!BCrypt.checkpw(request.getPassword(), userEvent.password())) {
                responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
                        .withDescription("Invalid credentials (Bad password)").asRuntimeException());
                return;
            }

            // 3. Create Session in Redis
            String sessionId = sessionService.createSession(request.getUsername(), String.valueOf(userEvent.id()));

            // 4. Generate JWT with JTI = SessionID
            Map<String, Object> claims = new HashMap<>();
            claims.put("jti", sessionId);
            claims.put("sub", request.getUsername());
            claims.put("roles", Collections.singletonList("USER")); // Default role for now

            Optional<String> tokenOpt = tokenGenerator.generateToken(claims);
            if (tokenOpt.isPresent()) {
                LoginResponse response = LoginResponse.newBuilder()
                        .setAccessToken(tokenOpt.get())
                        .setExpiresIn(1800)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(io.grpc.Status.INTERNAL
                        .withDescription("Failed to generate token").asRuntimeException());
            }

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOG.error("Login failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Login failed: " + e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void verify(VerifyRequest request, StreamObserver<VerifyResponse> responseObserver) {
        // 1. Parse JWT
        // Uses Micronaut Security Validator which verifies Signature & Expiration
        org.reactivestreams.Publisher<Authentication> authenticationPublisher = tokenValidator
                .validateToken(request.getToken(), null);

        Mono.from(authenticationPublisher).subscribe(
                auth -> {
                    // 2. Extract JTI (Session ID)
                    Object jtiObj = auth.getAttributes().get("jti");
                    if (jtiObj == null) {
                        sendInvalid(responseObserver, "Missing JTI");
                        return;
                    }
                    String sessionId = jtiObj.toString();

                    // 3. Check Redis
                    if (sessionService.validateSession(sessionId)) {
                        // Valid
                        VerifyResponse response = VerifyResponse.newBuilder()
                                .setValid(true)
                                .setUsername(auth.getName())
                                .setUserId(sessionId) // Or map userId if we stored it in claims
                                .addAllRoles(auth.getRoles())
                                .build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                    } else {
                        sendInvalid(responseObserver, "Session expired or revoked");
                    }
                },
                error -> sendInvalid(responseObserver, "Invalid Token: " + error.getMessage()));
    }

    private void sendInvalid(StreamObserver<VerifyResponse> observer, String reason) {
        LOG.warn("Token verification failed: {}", reason);
        // We can return valid=false OR gRPC error. Analysis says valid=false or error.
        // Let's return valid=false to keep it clean for BFF logic.
        VerifyResponse response = VerifyResponse.newBuilder()
                .setValid(false)
                .build();
        observer.onNext(response);
        observer.onCompleted();
    }

    @Override
    public void register(com.globaldashboard.auth.proto.RegisterRequest request,
            StreamObserver<com.globaldashboard.auth.proto.RegisterResponse> responseObserver) {
        try {
            // 1. Hash Password
            String hashedPassword = BCrypt.hashpw(request.getPassword(), BCrypt.gensalt());

            // 2. Call Kafka Client (Async Request-Reply)
            UserEvent result = userClient.createUser(request.getUsername(), request.getEmail(), hashedPassword)
                    .get(5, TimeUnit.SECONDS);

            // 3. Handle Result
            if (result.type() == UserEvent.EventType.ERROR) {
                responseObserver.onError(io.grpc.Status.ALREADY_EXISTS
                        .withDescription("User creation failed: " + result.message()).asRuntimeException());
                return;
            }

            // 4. Success
            com.globaldashboard.auth.proto.RegisterResponse response = com.globaldashboard.auth.proto.RegisterResponse
                    .newBuilder()
                    .setUserId(String.valueOf(result.id()))
                    .setUsername(result.username())
                    .setStatus("CREATED")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOG.error("Registration failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Registration failed: " + e.getMessage()).asRuntimeException());
        }
    }
}
