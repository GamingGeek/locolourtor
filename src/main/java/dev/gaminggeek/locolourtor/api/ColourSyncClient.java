package dev.gaminggeek.locolourtor.api;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import dev.gaminggeek.locolourtor.auth.AuthSession;
import dev.gaminggeek.locolourtor.cache.ColourCache;

public final class ColourSyncClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("locolourtor");
    private static final Gson GSON = new Gson();
    private static final Type UUID_COLOUR_MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    public static final ColourSyncClient INSTANCE = new ColourSyncClient();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    private final AtomicBoolean hasLoggedApiError = new AtomicBoolean(false);

    private ColourSyncClient() {
    }

    public void refreshColours(Collection<UUID> uuids) {
        if (uuids.isEmpty())
            return;

        List<UUID> uuidList = new java.util.ArrayList<>(uuids);
        int batchSize = 50;
        for (int i = 0; i < uuidList.size(); i += batchSize) {
            List<UUID> batch = uuidList.subList(i, Math.min(i + batchSize, uuidList.size()));
            JsonObject body = new JsonObject();
            com.google.gson.JsonArray array = new com.google.gson.JsonArray();
            for (UUID uuid : batch)
                array.add(uuid.toString());
            body.add("uuids", array);

            String url = AuthSession.API_BASE + "/colours";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            handleBatchFailure("HTTP " + response.statusCode(), null);
                            for (UUID uuid : batch)
                                ColourCache.INSTANCE.putDefault(uuid);
                            return;
                        }

                        Map<String, String> colourMap = GSON.fromJson(response.body(), UUID_COLOUR_MAP_TYPE);
                        for (UUID uuid : batch) {
                            String hex = colourMap != null ? colourMap.get(uuid.toString()) : null;
                            if (hex != null) {
                                try {
                                    int argb = hexToArgb(hex);
                                    ColourCache.INSTANCE.put(uuid, argb);
                                } catch (Exception e) {
                                    ColourCache.INSTANCE.putDefault(uuid);
                                }
                            } else {
                                ColourCache.INSTANCE.putDefault(uuid);
                            }
                        }
                        LOGGER.debug("[Locolourtor] Cached colours for {} players", batch.size());
                    })
                    .exceptionally(e -> {
                        handleBatchFailure(e.getMessage(), e);
                        for (UUID uuid : batch)
                            ColourCache.INSTANCE.putDefault(uuid);
                        return null;
                    });
        }
    }

    private void handleBatchFailure(String reason, Throwable error) {
        if (hasLoggedApiError.compareAndSet(false, true)) {
            if (error != null) {
                LOGGER.warn(
                        "[Locolourtor] Could not reach colour worker (will not log again until next successful fetch): {}",
                        reason, error);
            } else {
                LOGGER.warn(
                        "[Locolourtor] Could not reach colour worker (will not log again until next successful fetch): {}",
                        reason);
            }
        } else {
            LOGGER.debug("[Locolourtor] Encountered error when fetching colours: {}", reason);
        }
    }

    public CompletableFuture<String> verify(dev.gaminggeek.locolourtor.auth.MojangAuth.VerificationPayload payload) {
        String url = AuthSession.API_BASE + "/auth/verify";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        String message = extractErrorMessage(response.body(), response.statusCode());
                        throw new SyncException(response.statusCode(), message);
                    }
                    JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                    return json.get("token").getAsString();
                });
    }

    public CompletableFuture<Void> updateColour(String hexColour) {
        JsonObject body = new JsonObject();
        body.addProperty("colour", hexColour);

        String url = AuthSession.API_BASE + "/colour";
        String token = AuthSession.get().getToken();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .timeout(TIMEOUT)
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 401) {
                        throw new SyncException(401, "UNAUTHORIZED");
                    }
                    if (response.statusCode() != 200) {
                        String message = extractErrorMessage(response.body(), response.statusCode());
                        throw new SyncException(response.statusCode(), message);
                    }
                    return null;
                });
    }

    public CompletableFuture<Void> resetColour() {
        String url = AuthSession.API_BASE + "/colour";
        String token = AuthSession.get().getToken();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .DELETE()
                .header("Authorization", "Bearer " + token)
                .timeout(TIMEOUT)
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 401) {
                        throw new SyncException(401, "UNAUTHORIZED");
                    }
                    if (response.statusCode() != 200) {
                        String message = extractErrorMessage(response.body(), response.statusCode());
                        throw new SyncException(response.statusCode(), message);
                    }
                    return null;
                });
    }

    private static String extractErrorMessage(String responseBody, int statusCode) {
        try {
            JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
            if (json != null && json.has("error")) {
                return json.get("error").getAsString();
            }
        } catch (Exception ignored) {
        }

        return switch (statusCode) {
            case 401 -> "Authentication expired or invalid.";
            case 429 -> "Rate limit exceeded. Please wait a moment before trying again.";
            case 502 -> "Could not reach Mojang authentication servers.";
            default -> "HTTP " + statusCode;
        };
    }

    private static int hexToArgb(String hex) {
        String stripped = hex.startsWith("#") ? hex.substring(1) : hex;
        if (stripped.length() != 6)
            throw new IllegalArgumentException("Expected 6-digit hex: " + hex);
        int rgb = Integer.parseInt(stripped, 16);
        return 0xFF000000 | rgb;
    }

    public static final class SyncException extends RuntimeException {
        private final int statusCode;

        public SyncException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public boolean isUnauthorized() {
            return statusCode == 401;
        }

        public boolean isForbidden() { return statusCode == 403; }

        public boolean isInternalError() { return statusCode == 500; }
    }
}
