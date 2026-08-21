package dev.gaminggeek.locolourtor.auth;

public final class AuthSession {

    public static final String API_BASE = "https://locolourtor.gaminggeek.dev";
    private static final AuthSession INSTANCE = new AuthSession();
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

    public synchronized boolean hasValidToken() {
        return token != null && System.currentTimeMillis() / 1000L < tokenExpiry;
    }

    public synchronized void setToken(String jwt, long expiry) {
        this.token = jwt;
        this.tokenExpiry = expiry;
    }

    public synchronized void clearToken() {
        this.token = null;
        this.tokenExpiry = 0;
    }
}
