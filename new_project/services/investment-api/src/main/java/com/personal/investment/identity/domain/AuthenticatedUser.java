package com.personal.investment.identity.domain;

public record AuthenticatedUser(String userId, Role role, long permissionVersion) {
}
