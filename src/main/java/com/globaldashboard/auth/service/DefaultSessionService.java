package com.globaldashboard.auth.service;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.inject.Singleton;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DefaultSessionService implements SessionService {

    private final StatefulRedisConnection<String, String> connection;

    public DefaultSessionService(StatefulRedisConnection<String, String> connection) {
        this.connection = connection;
    }

    @Override
    public String createSession(String username, String userId) {
        String sessionId = UUID.randomUUID().toString();
        RedisCommands<String, String> commands = connection.sync();
        
        // Key: session:<uuid> -> userId
        String key = "session:" + sessionId;
        commands.setex(key, 1800, userId); // 30 mins TTL
        
        log.info("Created session {} for user {}", sessionId, username);
        return sessionId;
    }

    @Override
    public boolean validateSession(String sessionId) {
        RedisCommands<String, String> commands = connection.sync();
        String key = "session:" + sessionId;
        String userId = commands.get(key);
        
        if (userId != null) {
            commands.expire(key, 1800); // Sliding session
            return true;
        }
        return false;
    }

    @Override
    public void invalidateSession(String sessionId) {
        RedisCommands<String, String> commands = connection.sync();
        commands.del("session:" + sessionId);
        log.info("Invalidated session {}", sessionId);
    }
}
