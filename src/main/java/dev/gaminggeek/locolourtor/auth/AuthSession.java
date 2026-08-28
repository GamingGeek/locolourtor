package dev.gaminggeek.locolourtor.auth;

import java.util.UUID;

public final class AuthSession {

    public static final String API_BASE = "https://locolourtor.gaminggeek.dev";
    private static final AuthSession INSTANCE = new AuthSession();
    private UUID uuid = null;
    private String token = null;
    private long tokenExpiry = 0;

    private AuthSession() {
    }

    public static AuthSession get() {
        return INSTANCE;
    }

    public synchronized String getToken() {
        return token;
    }

    public synchronized boolean hasValidToken(UUID currentUuid) {
        return token != null
                && currentUuid != null
                && currentUuid.equals(this.uuid)
                && System.currentTimeMillis() / 1000L < tokenExpiry;
    }

    public synchronized void setToken(UUID uuid, String jwt, long expiry) {
        this.uuid = uuid;
        this.token = jwt;
        this.tokenExpiry = expiry;
    }

    public synchronized void clearToken() {
        this.uuid = null;
        this.token = null;
        this.tokenExpiry = 0;
    }
}
