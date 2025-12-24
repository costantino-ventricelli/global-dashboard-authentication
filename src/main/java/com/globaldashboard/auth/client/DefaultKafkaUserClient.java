package com.globaldashboard.auth.client;

import com.globaldashboard.auth.event.user.UserEvent;
import com.globaldashboard.auth.event.user.UserFindRequest;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;

@Singleton
public class DefaultKafkaUserClient implements KafkaUserClient {

    private final UserRequestProducer producer;
    private final PendingRequestsRegistry registry;

    public DefaultKafkaUserClient(UserRequestProducer producer, PendingRequestsRegistry registry) {
        this.producer = producer;
        this.registry = registry;
    }

    @Override
    public CompletableFuture<UserEvent> findUser(String username) {
        CompletableFuture<UserEvent> future = registry.register(username);
        producer.sendFindRequest(username, new UserFindRequest(username));
        // Add timeout logic ideally, but keeping it simple for now
        return future;
    }
}
