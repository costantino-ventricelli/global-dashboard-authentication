package com.globaldashboard.auth.event.user;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record UserCreateRequest(String username, String email, String passwordHash) {
}
