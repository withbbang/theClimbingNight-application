package com.theclimbingnight.auth.social;

public record SocialIdentity(SocialProvider provider, String providerUserId) {
}
