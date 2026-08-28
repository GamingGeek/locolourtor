package dev.gaminggeek.locolourtor.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.gaminggeek.locolourtor.Locolourtor;
import dev.gaminggeek.locolourtor.api.ColourSyncClient;
import dev.gaminggeek.locolourtor.auth.AuthSession;
import dev.gaminggeek.locolourtor.auth.MojangAuth;
import dev.gaminggeek.locolourtor.cache.ColourCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//#if NEOFORGE
//$$ import net.minecraft.commands.CommandSourceStack;
//$$ import static net.minecraft.commands.Commands.argument;
//$$ import static net.minecraft.commands.Commands.literal;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.client.Minecraft;
//#elseif FABRIC && MC <= 12111
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
//#elseif FABRIC
//$$ import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
//$$ import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
//$$ import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
//$$ import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.network.chat.Component;
//#endif

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LocolourtorCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("locolourtor");

    private LocolourtorCommand() {
    }

    public static void register(
            //#if NEOFORGE
            //$$ CommandDispatcher<CommandSourceStack> dispatcher
            //#elseif FABRIC
            CommandDispatcher<FabricClientCommandSource> dispatcher
            //#endif
    ) {
        var root = literal("locolourtor")
                .then(buildSetCommand())
                .then(buildResetCommand())
                .then(buildRefreshCommand())
                .then(buildStatusCommand())
                .executes(ctx -> {
                    sendMessage(ctx,
                            "§6Locolourtor §7— Commands: §f/locolourtor set <colour>§7, §f/locolourtor reset§7, §f/locolourtor refresh [player]§7, §f/locolourtor status");
                    return 1;
                });

        var registered = dispatcher.register(root);

        dispatcher.register(literal("locolour").redirect(registered));
        dispatcher.register(literal("locolor").redirect(registered));
    }

    private static LiteralArgumentBuilder<
            //#if NEOFORGE
            //$$ CommandSourceStack
            //#elseif FABRIC
            FabricClientCommandSource
            //#endif
            > buildSetCommand() {
        return literal("set")
                // /locolourtor set <hex>
                .then(argument("hex", StringArgumentType.word())
                        .executes(ctx -> executeSetHex(ctx, StringArgumentType.getString(ctx, "hex"))))
                // /locolourtor set rgb <r> <g> <b>
                .then(literal("rgb").then(argument("r", IntegerArgumentType.integer(0, 255))
                        .then(argument("g", IntegerArgumentType.integer(0, 255))
                                .then(argument("b", IntegerArgumentType.integer(0, 255)).executes(ctx -> {
                                    int r = IntegerArgumentType.getInteger(ctx, "r");
                                    int g = IntegerArgumentType.getInteger(ctx, "g");
                                    int b = IntegerArgumentType.getInteger(ctx, "b");
                                    return executeSetHex(ctx, rgbToHex(r, g, b));
                                })))))
                // /locolourtor set cmyk <c> <m> <y> <k>
                .then(literal("cmyk").then(argument("c", IntegerArgumentType.integer(0, 100))
                        .then(argument("m", IntegerArgumentType.integer(0, 100))
                                .then(argument("y", IntegerArgumentType.integer(0, 100))
                                        .then(argument("k", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                                            int c = IntegerArgumentType.getInteger(ctx, "c");
                                            int m = IntegerArgumentType.getInteger(ctx, "m");
                                            int y = IntegerArgumentType.getInteger(ctx, "y");
                                            int k = IntegerArgumentType.getInteger(ctx, "k");
                                            return executeSetHex(ctx, cmykToHex(c, m, y, k));
                                        }))))));
    }

    private static int executeSetHex(
            //#if NEOFORGE
            //$$ CommandContext<CommandSourceStack> ctx,
            //#elseif FABRIC
            CommandContext<FabricClientCommandSource> ctx,
            //#endif
            String rawHex) {
        String hex;
        try {
            hex = normaliseHex(rawHex);
        } catch (IllegalArgumentException e) {
            sendMessage(ctx, "§cInvalid colour: " + e.getMessage());
            return 0;
        }

        sendMessage(ctx, "§7Setting your locator bar colour to §r" + hex + "§7...");

        var session = AuthSession.get();
        //#if FABRIC && MC <= 12111
        var client = ctx.getSource().getClient();
        UUID selfUuid = client.getSession().getUuidOrNull();
        //#else
        //$$ var client = Minecraft.getInstance();
        //$$ UUID selfUuid = client.getUser().getProfileId();
        //#endif

        if (selfUuid == null) {
            sendMessage(ctx, "§cCannot set colour, unable to read your UUID!");
            return 0;
        }

        if (session.hasValidToken(selfUuid)) {
            ColourSyncClient.INSTANCE.updateColour(hex).thenRun(() -> {
                applyLocally(client, hex);
                sendMessage(ctx, "§aColour updated to §r" + hex + "§a!");
            }).exceptionally(e -> {
                Throwable cause = getRootCause(e);
                LOGGER.error("[Locolourtor] Encountered error during colour update:", cause);
                if (cause instanceof ColourSyncClient.SyncException apiEx && apiEx.isUnauthorized()) {
                    session.clearToken();
                    triggerAuthThenSet(ctx, client, selfUuid, hex);
                } else {
                    sendMessage(ctx, "§cFailed to update colour: " + cause.getMessage());
                }
                return null;
            });
        } else {
            triggerAuthThenSet(ctx, client, selfUuid, hex);
        }

        return 1;
    }

    private static void triggerAuthThenSet(
            //#if NEOFORGE
            //$$ CommandContext<CommandSourceStack> ctx,
            //$$ Minecraft client,
            //#elseif FABRIC
            CommandContext<FabricClientCommandSource> ctx,
            //#if MC <= 12111
            MinecraftClient client,
            //#else
            //$$ Minecraft client,
            //#endif
            //#endif
            UUID selfUuid,
            String hex) {

        MojangAuth.createVerificationPayload(client).thenCompose(ColourSyncClient.INSTANCE::verify)
                .thenCompose(token -> {
                    long expiry = parseJwtExpiry(token);
                    AuthSession.get().setToken(selfUuid, token, expiry);
                    return ColourSyncClient.INSTANCE.updateColour(hex);
                }).thenRun(() -> {
                    applyLocally(client, hex);
                    sendMessage(ctx, "§aColour set to §r" + hex + "§a!");
                }).exceptionally(e -> {
                    Throwable cause = getRootCause(e);
                    LOGGER.error("[Locolourtor] Encountered error during colour update:", cause);
                    sendMessage(ctx, "§cFailed to update colour: " + cause.getMessage());
                    return null;
                });
    }

    private static LiteralArgumentBuilder<
            //#if NEOFORGE
            //$$ CommandSourceStack
            //#elseif FABRIC
            FabricClientCommandSource
            //#endif
            > buildResetCommand() {
        return literal("reset").executes(ctx -> {
            var session = AuthSession.get();
            //#if FABRIC && MC <= 12111
            var client = ctx.getSource().getClient();
            UUID selfUuid = client.getSession().getUuidOrNull();
            //#else
            //$$ var client = Minecraft.getInstance();
            //$$ UUID selfUuid = client.getUser().getProfileId();
            //#endif

            if (selfUuid == null) {
                sendMessage(ctx, "§cCannot reset colour, unable to read your UUID!");
                return 0;
            }

            if (session.hasValidToken(selfUuid)) {
                executeReset(ctx, client, selfUuid);
            } else {
                triggerAuthThenReset(ctx, client, selfUuid);
            }
            return 1;
        });
    }

    private static void triggerAuthThenReset(
            //#if NEOFORGE
            //$$ CommandContext<CommandSourceStack> ctx,
            //$$ Minecraft client,
            //#elseif FABRIC
            CommandContext<FabricClientCommandSource> ctx,
            //#if MC <= 12111
            MinecraftClient client,
            //#else
            //$$ Minecraft client,
            //#endif
            //#endif
            UUID selfUuid) {
        MojangAuth.createVerificationPayload(client).thenCompose(ColourSyncClient.INSTANCE::verify)
                .thenAccept(token -> {
                    long expiry = parseJwtExpiry(token);
                    AuthSession.get().setToken(selfUuid, token, expiry);
                    executeReset(ctx, client, selfUuid);
                }).exceptionally(e -> {
                    Throwable cause = getRootCause(e);
                    LOGGER.error("[Locolourtor] Encountered error during authentication:", cause);
                    return null;
                });
    }

    private static void executeReset(
            //#if NEOFORGE
            //$$ CommandContext<CommandSourceStack> ctx,
            //$$ Minecraft client,
            //#elseif FABRIC
            CommandContext<FabricClientCommandSource> ctx,
            //#if MC <= 12111
            MinecraftClient client,
            //#else
            //$$ Minecraft client,
            //#endif
            //#endif
            UUID selfUuid) {
        ColourSyncClient.INSTANCE.resetColour().thenRun(() -> {
            ColourCache.INSTANCE.remove(selfUuid);
            sendMessage(ctx, "§aColour reset to default");
        }).exceptionally(e -> {
            Throwable cause = getRootCause(e);
            LOGGER.error("[Locolourtor] Failed to reset colour:", cause);
            if (cause instanceof ColourSyncClient.SyncException apiEx && apiEx.isUnauthorized()) {
                AuthSession.get().clearToken();
                triggerAuthThenReset(ctx, client, selfUuid);
            } else {
                sendMessage(ctx, "§cFailed to reset colour: " + cause.getMessage());
            }
            return null;
        });
    }

    private static LiteralArgumentBuilder<
            //#if NEOFORGE
            //$$ CommandSourceStack
            //#elseif FABRIC
            FabricClientCommandSource
            //#endif
            > buildRefreshCommand() {
        return literal("refresh").executes(LocolourtorCommand::executeRefreshAll)
                .then(argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOnlinePlayers(builder))
                        .executes(ctx -> executeRefreshPlayer(ctx, StringArgumentType.getString(ctx, "player"))));
    }

    private static int executeRefreshAll(
            //#if NEOFORGE
            //$$ CommandContext<CommandSourceStack> ctx
            //#elseif FABRIC
            CommandContext<FabricClientCommandSource> ctx
            //#endif
    ) {
        //#if FABRIC && MC <= 12111
        var client = ctx.getSource().getClient();
        //#else
        //$$ var client = Minecraft.getInstance();
        //#endif

        ColourCache.INSTANCE.clear();
        Locolourtor.refreshColoursForWorldPlayers(client);
        sendMessage(ctx, "§aRefreshing locator colours for all players in the world...");
        return 1;
    }

    private static int executeRefreshPlayer(
            //#if NEOFORGE
            //$$ CommandContext<CommandSourceStack> ctx,
            //#elseif FABRIC
            CommandContext<FabricClientCommandSource> ctx,
            //#endif
            String targetNameOrUuid) {
        //#if FABRIC && MC <= 12111
        var client = ctx.getSource().getClient();
        if (client.getNetworkHandler() == null) {
            // idk if there's a scenario where this can actually happen
            sendMessage(ctx, "§cYou don't seem to be in a world or server...");
            return 0;
        }
        var entry = client.getNetworkHandler().getPlayerList().stream()
                .filter(p -> p.getProfile().name().equalsIgnoreCase(targetNameOrUuid)
                        || p.getProfile().id().toString().equalsIgnoreCase(targetNameOrUuid))
                .findFirst().orElse(null);
        if (entry == null) {
            sendMessage(ctx, "§cPlayer '" + targetNameOrUuid + "' not found in player list.");
            return 0;
        }
        UUID targetUuid = entry.getProfile().id();
        String targetName = entry.getProfile().name();
        //#else
        //$$ var client = Minecraft.getInstance();
        //$$ if (client.getConnection() == null) {
        //$$     sendMessage(ctx, "§cYou don't seem to be in a world or server...");
        //$$     return 0;
        //$$ }
        //$$ var entry = client.getConnection().getOnlinePlayers().stream()
        //$$         .filter(p -> p.getProfile().name().equalsIgnoreCase(targetNameOrUuid)
        //$$                   || p.getProfile().id().toString().equalsIgnoreCase(targetNameOrUuid))
        //$$         .findFirst().orElse(null);
        //$$ if (entry == null) {
        //$$     sendMessage(ctx, "§cPlayer '" + targetNameOrUuid + "' not found in player list.");
        //$$     return 0;
        //$$ }
        //$$ UUID targetUuid = entry.getProfile().id();
        //$$ String targetName = entry.getProfile().name();
        //#endif

        ColourCache.INSTANCE.remove(targetUuid);
        ColourSyncClient.INSTANCE.refreshColours(List.of(targetUuid));
        sendMessage(ctx, "§aRefreshing locator colour for §f" + targetName + "§a...");
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestOnlinePlayers(SuggestionsBuilder builder) {
        //#if FABRIC && MC <= 12111
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler != null) {
            for (var entry : handler.getPlayerList()) {
                String name = entry.getProfile().name();
                if (name.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(name);
                }
            }
        }
        //#else
        //$$ var conn = Minecraft.getInstance().getConnection();
        //$$ if (conn != null) {
        //$$     for (var entry : conn.getOnlinePlayers()) {
        //$$         String name = entry.getProfile().name();
        //$$         if (name.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
        //$$             builder.suggest(name);
        //$$         }
        //$$     }
        //$$ }
        //#endif
        return builder.buildFuture();
    }

    private static LiteralArgumentBuilder<
            //#if NEOFORGE
            //$$ CommandSourceStack
            //#elseif FABRIC
            FabricClientCommandSource
            //#endif
            > buildStatusCommand() {
        return literal("status").executes(ctx -> {
            //#if FABRIC && MC <= 12111
            UUID selfUuid = ctx.getSource().getClient().getSession().getUuidOrNull();
            //#else
            //$$ UUID selfUuid = Minecraft.getInstance().getUser().getProfileId();
            //#endif

            var cachedColour = ColourCache.INSTANCE.get(selfUuid);
            String colourStr = cachedColour.isPresent() ? String.format("#%06X", cachedColour.getAsInt() & 0xFFFFFF)
                    : "§8default, based on your UUID";

            sendMessage(ctx, "§6Locolourtor Status");
            sendMessage(ctx, "§7Your locator bar colour: §r" + colourStr);
            return 1;
        });
    }

    private static String normaliseHex(String input) {
        String stripped = input.startsWith("#") ? input.substring(1) : input;
        if (!stripped.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("'" + input + "' is not a valid 6-character hex colour.");
        }
        return "#" + stripped.toLowerCase();
    }

    private static String rgbToHex(int r, int g, int b) {
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private static String cmykToHex(int c, int m, int y, int k) {
        double kFactor = 1.0 - k / 100.0;
        int r = (int) Math.round(255 * (1.0 - c / 100.0) * kFactor);
        int g = (int) Math.round(255 * (1.0 - m / 100.0) * kFactor);
        int b = (int) Math.round(255 * (1.0 - y / 100.0) * kFactor);
        return rgbToHex(r, g, b);
    }

    private static Throwable getRootCause(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }

    private static void applyLocally(
            //#if FABRIC && MC <= 12111
            MinecraftClient client,
            //#else
            //$$ Minecraft client,
            //#endif
            String hex) {
        //#if FABRIC && MC <= 12111
        UUID selfUuid = client.getSession().getUuidOrNull();
        //#else
        //$$ UUID selfUuid = client.getUser().getProfileId();
        //#endif
        if (selfUuid != null) {
            String stripped = hex.startsWith("#") ? hex.substring(1) : hex;
            int argb = 0xFF000000 | Integer.parseInt(stripped, 16);
            ColourCache.INSTANCE.put(selfUuid, argb);
        }
    }

    private static long parseJwtExpiry(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2)
                return 0;
            String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            com.google.gson.JsonObject obj = new com.google.gson.Gson().fromJson(payloadJson,
                    com.google.gson.JsonObject.class);
            return obj.has("exp") ? obj.get("exp").getAsLong() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static void sendMessage(
            //#if NEOFORGE
            //$$ CommandContext<CommandSourceStack> ctx,
            //#elseif FABRIC
            CommandContext<FabricClientCommandSource> ctx,
            //#endif
            String message) {
        //#if NEOFORGE
        //$$ ctx.getSource().sendSystemMessage(Component.literal(message));
        //#elseif FABRIC && MC <= 12111
        ctx.getSource().sendFeedback(Text.literal(message));
        //#elseif FABRIC
        //$$ ctx.getSource().sendFeedback(Component.literal(message));
        //#endif
    }
}
