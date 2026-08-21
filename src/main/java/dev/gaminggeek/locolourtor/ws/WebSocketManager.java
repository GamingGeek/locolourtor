package dev.gaminggeek.locolourtor.ws;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import dev.gaminggeek.locolourtor.auth.AuthSession;
import dev.gaminggeek.locolourtor.cache.ColourCache;

public final class WebSocketManager implements WebSocket.Listener {

    private static final Logger LOGGER = LoggerFactory.getLogger("locolourtor");
    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    public static final WebSocketManager INSTANCE = new WebSocketManager();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Locolourtor-WS");
        t.setDaemon(true);
        return t;
    });
    private final StringBuilder messageBuffer = new StringBuilder();
    private final AtomicBoolean isWorldActive = new AtomicBoolean(false);
    private WebSocket webSocket = null;
    private int reconnectAttempts = 0;

    private WebSocketManager() {
    }

    public synchronized void connect() {
        isWorldActive.set(true);
        if (webSocket != null)
            return;

        String wsUrl = AuthSession.API_BASE
                .replace("https://", "wss://") + "/ws";

        LOGGER.debug("[Locolourtor] Connecting to real-time sync socket: {}", wsUrl);

        httpClient.newWebSocketBuilder()
                .connectTimeout(TIMEOUT)
                .buildAsync(URI.create(wsUrl), this)
                .whenComplete((ws, error) -> {
                    if (error != null) {
                        LOGGER.debug("[Locolourtor] WS connect error: {}", error.getMessage());
                        scheduleReconnect();
                    } else {
                        synchronized (this) {
                            this.webSocket = ws;
                            this.reconnectAttempts = 0;
                        }
                        LOGGER.info("[Locolourtor] Connected to real-time sync socket.");
                    }
                });
    }

    public synchronized void disconnect() {
        isWorldActive.set(false);
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "World disconnect");
            } catch (Exception ignored) {
            }
            webSocket = null;
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageBuffer.append(data);

        if (last) {
            String fullMessage = messageBuffer.toString();
            messageBuffer.setLength(0);
            handleIncomingMessage(fullMessage);
        }

        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        LOGGER.debug("[Locolourtor] WS closed: {} ({})", reason, statusCode);
        synchronized (this) {
            this.webSocket = null;
        }
        if (isWorldActive.get()) {
            scheduleReconnect();
        }
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        LOGGER.debug("[Locolourtor] WS error: {}", error.getMessage());
        synchronized (this) {
            this.webSocket = null;
        }
        if (isWorldActive.get()) {
            scheduleReconnect();
        }
    }

    private void handleIncomingMessage(String jsonStr) {
        try {
            JsonObject json = GSON.fromJson(jsonStr, JsonObject.class);
            if (json == null || !json.has("type"))
                return;

            String type = json.get("type").getAsString();
            if ("colour_update".equals(type) && json.has("uuid")) {
                UUID uuid = UUID.fromString(json.get("uuid").getAsString());
                if (json.has("colour") && !json.get("colour").isJsonNull()) {
                    String hex = json.get("colour").getAsString();
                    int argb = hexToArgb(hex);
                    ColourCache.INSTANCE.put(uuid, argb);
                    LOGGER.debug("[Locolourtor] Synced colour for {}: {}", uuid, hex);
                } else {
                    ColourCache.INSTANCE.remove(uuid);
                    LOGGER.debug("[Locolourtor] Synced reset for {}", uuid);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Locolourtor] Error processing sync message: {}", e.getMessage());
        }
    }

    private synchronized void scheduleReconnect() {
        if (!isWorldActive.get())
            return;

        reconnectAttempts++;
        long delaySec = Math.min((long) Math.pow(2, Math.min(reconnectAttempts, 5)), 30L);

        scheduler.schedule(() -> {
            synchronized (this) {
                if (isWorldActive.get() && webSocket == null) {
                    connect();
                }
            }
        }, delaySec, TimeUnit.SECONDS);
    }

    private static int hexToArgb(String hex) {
        String stripped = hex.startsWith("#") ? hex.substring(1) : hex;
        int rgb = Integer.parseInt(stripped, 16);
        return 0xFF000000 | rgb;
    }
}
