package com.globaldashboard.auth.service;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultSessionServiceTest {

    @Mock
    private StatefulRedisConnection<String, String> connection;

    @Mock
    private RedisCommands<String, String> commands;

    private DefaultSessionService sessionService;

    @BeforeEach
    void setUp() {
        when(connection.sync()).thenReturn(commands);
        sessionService = new DefaultSessionService(connection);
    }

    @Test
    void createSession_ShouldGenerateUuidAndStoreInRedis() {
        String username = "testuser";
        String userId = "user123";

        String sessionId = sessionService.createSession(username, userId);

        assertNotNull(sessionId);
        verify(commands).setex(eq("session:" + sessionId), eq(1800L), eq(userId));
    }

    @Test
    void validateSession_ShouldReturnTrue_WhenSessionExists() {
        String sessionId = "valid-session-id";
        String userId = "user123";

        when(commands.get("session:" + sessionId)).thenReturn(userId);

        boolean isValid = sessionService.validateSession(sessionId);

        assertTrue(isValid);
        verify(commands).expire(eq("session:" + sessionId), eq(1800L));
    }

    @Test
    void validateSession_ShouldReturnFalse_WhenSessionDoesNotExist() {
        String sessionId = "invalid-session-id";

        when(commands.get("session:" + sessionId)).thenReturn(null);

        boolean isValid = sessionService.validateSession(sessionId);

        assertFalse(isValid);
        verify(commands, never()).expire(anyString(), anyLong());
    }

    @Test
    void invalidateSession_ShouldDeleteKeyFromRedis() {
        String sessionId = "session-to-delete";

        sessionService.invalidateSession(sessionId);

        verify(commands).del("session:" + sessionId);
    }
}
