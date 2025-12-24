package com.globaldashboard.auth.service;

public interface SessionService {
    String createSession(String username, String userId);
    boolean validateSession(String sessionId);
    void invalidateSession(String sessionId);
}
