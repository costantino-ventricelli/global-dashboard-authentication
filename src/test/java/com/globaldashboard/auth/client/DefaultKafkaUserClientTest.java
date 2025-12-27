package com.globaldashboard.auth.client;

import com.globaldashboard.auth.event.user.UserEvent;
import com.globaldashboard.auth.event.user.UserFindRequest;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultKafkaUserClientTest {

    @Mock
    UserRequestProducer producer;

    @Mock
    PendingRequestsRegistry registry;

    @InjectMocks
    DefaultKafkaUserClient client;

    @Test
    void testFindUser() {
        String username = "testuser";
        CompletableFuture<UserEvent> future = new CompletableFuture<>();
        when(registry.register(username)).thenReturn(future);

        CompletableFuture<UserEvent> result = client.findUser(username);

        Assertions.assertEquals(future, result);
        verify(registry).register(username);
        verify(producer).sendFindRequest(eq(username), any(UserFindRequest.class));
    }
}
