package com.globaldashboard.auth.client;

import com.globaldashboard.auth.event.user.UserEvent;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.Topic;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@KafkaListener(groupId = "global-dashboard-auth-consumer")
public class UserReplyConsumer {

    private final PendingRequestsRegistry registry;

    public UserReplyConsumer(PendingRequestsRegistry registry) {
        this.registry = registry;
    }

    @Topic("persistence.users.events") // Assuming DB replies here? Yes, checked UserService
    public void receive(UserEvent event) {
        log.info("Received UserEvent for: {}", event.username());
        registry.complete(event.username(), event);
    }
}
