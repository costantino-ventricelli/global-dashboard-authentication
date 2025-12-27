package com.globaldashboard.auth.client;

import com.globaldashboard.auth.event.user.UserEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PendingRequestsRegistryTest {

    @Test
    void testRegisterAndComplete() throws ExecutionException, InterruptedException, TimeoutException {
        PendingRequestsRegistry registry = new PendingRequestsRegistry();
        String username = "testuser";

        CompletableFuture<UserEvent> future = registry.register(username);
        Assertions.assertFalse(future.isDone());

        UserEvent event = new UserEvent(1L, username, "email", "pass", UserEvent.EventType.FOUND, "msg");
        registry.complete(username, event);

        Assertions.assertTrue(future.isDone());
        Assertions.assertEquals(event, future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void testCompleteUnknownUser() {
        PendingRequestsRegistry registry = new PendingRequestsRegistry();
        // Should not throw exception
        registry.complete("unknown", new UserEvent(1L, "u", "e", "p", UserEvent.EventType.FOUND, "m"));
    }
}
