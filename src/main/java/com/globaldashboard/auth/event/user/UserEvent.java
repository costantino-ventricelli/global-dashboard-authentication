package com.globaldashboard.auth.event.user;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record UserEvent(Long id, String username, String email, String password, EventType type, String message) {
    public enum EventType {
        CREATED, FOUND, NOT_FOUND, ERROR
    }
}
