package com.globaldashboard.auth.client;

import com.globaldashboard.auth.event.user.UserEvent;
import java.util.concurrent.CompletableFuture;

public interface KafkaUserClient {
    CompletableFuture<UserEvent> findUser(String username);
}
