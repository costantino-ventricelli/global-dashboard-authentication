package com.globaldashboard.auth.grpc;

import com.globaldashboard.auth.client.KafkaUserClient;
import com.globaldashboard.auth.client.UserReplyConsumer;
import com.globaldashboard.auth.event.user.UserEvent;
import com.globaldashboard.auth.event.user.UserEvent.EventType;
import com.globaldashboard.auth.proto.LoginRequest;
import com.globaldashboard.auth.proto.LoginResponse;
import com.globaldashboard.auth.proto.VerifyRequest;
import com.globaldashboard.auth.proto.VerifyResponse;
import com.globaldashboard.auth.service.SessionService;
import io.grpc.stub.StreamObserver;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator;
import io.micronaut.security.token.jwt.validator.JwtTokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthEndpointTest {

    @Mock
    private KafkaUserClient userClient;

    @Mock
    private SessionService sessionService;

    @Mock
    private JwtTokenGenerator tokenGenerator;

    @Mock
    private JwtTokenValidator tokenValidator;

    @Mock
    private StreamObserver<LoginResponse> loginResponseObserver;

    @Mock
    private StreamObserver<VerifyResponse> verifyResponseObserver;

    private AuthEndpoint authEndpoint;

    @BeforeEach
    void setUp() {
        authEndpoint = new AuthEndpoint(userClient, sessionService, tokenGenerator, tokenValidator);
    }

    @Test
    void login_ShouldReturnToken_WhenCredentialsAreValid() throws Exception {
        String username = "validuser";
        String password = "password";
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        UserEvent userEvent = new UserEvent(1L, username, "email@test.com", hashedPassword, EventType.CREATED, null);

        LoginRequest request = LoginRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .build();

        CompletableFuture<UserEvent> future = CompletableFuture.completedFuture(userEvent);
        when(userClient.findUser(username)).thenReturn(future);
        when(sessionService.createSession(eq(username), anyString())).thenReturn("session-id");
        when(tokenGenerator.generateToken(anyMap())).thenReturn(Optional.of("access-token"));

        authEndpoint.login(request, loginResponseObserver);

        verify(loginResponseObserver).onNext(argThat(
                response -> response.getAccessToken().equals("access-token") && response.getExpiresIn() == 1800));
        verify(loginResponseObserver).onCompleted();
    }

    @Test
    void login_ShouldReturnError_WhenUserNotFound() throws Exception {
        String username = "unknown";
        LoginRequest request = LoginRequest.newBuilder().setUsername(username).setPassword("pwd").build();

        UserEvent notFound = new UserEvent(null, null, null, null, EventType.NOT_FOUND, null);
        CompletableFuture<UserEvent> future = CompletableFuture.completedFuture(notFound);
        when(userClient.findUser(username)).thenReturn(future);

        authEndpoint.login(request, loginResponseObserver);

        verify(loginResponseObserver).onError(argThat(t -> t.getMessage().contains("User not found")));
    }

    @Test
    void login_ShouldReturnError_WhenPasswordInvalid() throws Exception {
        String username = "validuser";
        String hashedPassword = BCrypt.hashpw("correct", BCrypt.gensalt());
        UserEvent userEvent = new UserEvent(1L, username, "email", hashedPassword, EventType.CREATED, null);

        LoginRequest request = LoginRequest.newBuilder().setUsername(username).setPassword("wrong").build();

        CompletableFuture<UserEvent> future = CompletableFuture.completedFuture(userEvent);
        when(userClient.findUser(username)).thenReturn(future);

        authEndpoint.login(request, loginResponseObserver);

        verify(loginResponseObserver).onError(argThat(t -> t.getMessage().contains("Bad password")));
    }

    @Test
    void verify_ShouldReturnValid_WhenTokenIsOpenAndSessionExists() {
        String token = "valid-token";
        String sessionId = "session-123";
        String username = "testuser";

        Authentication auth = Authentication.build(username, Collections.singletonList("USER"),
                Map.of("jti", sessionId));
        when(tokenValidator.validateToken(token, null)).thenReturn(Mono.just(auth));
        when(sessionService.validateSession(sessionId)).thenReturn(true);

        VerifyRequest request = VerifyRequest.newBuilder().setToken(token).build();

        authEndpoint.verify(request, verifyResponseObserver);

        verify(verifyResponseObserver)
                .onNext(argThat(response -> response.getValid() && response.getUsername().equals(username)));
        verify(verifyResponseObserver).onCompleted();
    }

    @Mock
    private StreamObserver<com.globaldashboard.auth.proto.RegisterResponse> registerResponseObserver;

    @Test
    void register_ShouldReturnSuccess_WhenUserCreated() throws Exception {
        String username = "newuser";
        String email = "new@test.com";
        String password = "password";

        com.globaldashboard.auth.proto.RegisterRequest request = com.globaldashboard.auth.proto.RegisterRequest
                .newBuilder()
                .setUsername(username)
                .setEmail(email)
                .setPassword(password)
                .build();

        UserEvent createdEvent = new UserEvent(100L, username, email, "hash", EventType.CREATED, null);
        CompletableFuture<UserEvent> future = CompletableFuture.completedFuture(createdEvent);

        when(userClient.createUser(eq(username), eq(email), anyString())).thenReturn(future);

        authEndpoint.register(request, registerResponseObserver);

        verify(registerResponseObserver).onNext(
                argThat(response -> response.getStatus().equals("CREATED") && response.getUserId().equals("100")));
        verify(registerResponseObserver).onCompleted();
    }

    @Test
    void register_ShouldReturnError_WhenUserAlreadyExists() throws Exception {
        String username = "existing";
        com.globaldashboard.auth.proto.RegisterRequest request = com.globaldashboard.auth.proto.RegisterRequest
                .newBuilder()
                .setUsername(username)
                .setEmail("ex@test.com")
                .setPassword("pwd")
                .build();

        UserEvent errorEvent = new UserEvent(null, username, null, null, EventType.ERROR, "Email exists");
        CompletableFuture<UserEvent> future = CompletableFuture.completedFuture(errorEvent);

        when(userClient.createUser(eq(username), anyString(), anyString())).thenReturn(future);

        authEndpoint.register(request, registerResponseObserver);

        verify(registerResponseObserver).onError(argThat(t -> t instanceof io.grpc.StatusRuntimeException &&
                ((io.grpc.StatusRuntimeException) t).getStatus().getCode() == io.grpc.Status.Code.ALREADY_EXISTS));
    }
}
