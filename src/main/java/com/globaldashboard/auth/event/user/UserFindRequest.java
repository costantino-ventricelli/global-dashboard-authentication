package com.globaldashboard.auth.event.user;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record UserFindRequest(String username) {
}
