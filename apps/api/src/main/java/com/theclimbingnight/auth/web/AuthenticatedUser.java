package com.theclimbingnight.auth.web;

import com.theclimbingnight.user.entity.Role;

public record AuthenticatedUser(long id, Role role) {
}
