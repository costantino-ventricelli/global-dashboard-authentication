package com.globaldashboard.auth.client;

import com.globaldashboard.auth.event.user.UserEvent;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class PendingRequestsRegistry {
    private final Map<String, CompletableFuture<UserEvent>> pendingRequests = new ConcurrentHashMap<>();

    public CompletableFuture<UserEvent> register(String username) {
        CompletableFuture<UserEvent> future = new CompletableFuture<>();
        pendingRequests.put(username, future);
        return future;
    }

    public void complete(String username, UserEvent event) {
        CompletableFuture<UserEvent> future = pendingRequests.remove(username);
        if (future != null) {
            future.complete(event);
        } else {
            // This is expected if multiple instances or if it's an event not meant for us (broadcast)
            // But since we use username as key, it is specific.
            log.debug("Received event for {} but no pending request found.", username);
        }
    }
}
