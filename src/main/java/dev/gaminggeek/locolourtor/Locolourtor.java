package dev.gaminggeek.locolourtor;

import dev.gaminggeek.locolourtor.api.ColourSyncClient;
import dev.gaminggeek.locolourtor.cache.ColourCache;
import dev.gaminggeek.locolourtor.command.LocolourtorCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//#if NEOFORGE
//$$ import net.neoforged.bus.api.IEventBus;
//$$ import net.neoforged.fml.common.Mod;
//$$ import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
//$$ import net.neoforged.neoforge.client.event.ClientTickEvent;
//$$ import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
//$$ import net.neoforged.neoforge.common.NeoForge;
//$$ import net.minecraft.client.Minecraft;
//#elseif FABRIC
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//#if MC <= 12111
import net.minecraft.client.MinecraftClient;
//#else
//$$ import net.minecraft.client.Minecraft;
//#endif
//#endif

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

//#if NEOFORGE
//$$ @Mod("locolourtor")
//$$ public class Locolourtor {
//#elseif FABRIC
public class Locolourtor implements ClientModInitializer {
//#endif

    public static final Logger LOGGER = LoggerFactory.getLogger("locolourtor");

    private static final int DISCOVERY_CHECK_INTERVAL_TICKS = 20;
    private static final int FULL_REFRESH_INTERVAL_TICKS = 20 * 60 * 10;

    private static int discoveryTickCounter = 0;
    private static int fullRefreshTickCounter = 0;

    //#if NEOFORGE
    //$$ public Locolourtor(IEventBus modEventBus) {
    //$$     LOGGER.info("[Locolourtor] Initialising on NeoForge...");
    //$$
    //$$     NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> {
    //$$         LocolourtorCommand.register(event.getDispatcher());
    //$$     });
    //$$
    //$$     NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> {
    //$$         ColourCache.INSTANCE.clear();
    //$$         refreshColoursForWorldPlayers(Minecraft.getInstance());
    //$$         dev.gaminggeek.locolourtor.ws.WebSocketManager.INSTANCE.connect();
    //$$     });
    //$$
    //$$     NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
    //$$         ColourCache.INSTANCE.clear();
    //$$         dev.gaminggeek.locolourtor.ws.WebSocketManager.INSTANCE.disconnect();
    //$$     });
    //$$
    //$$     NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
    //$$         onClientTick(Minecraft.getInstance());
    //$$     });
    //$$
    //$$     LOGGER.info("[Locolourtor] Ready.");
    //$$ }
    //#elseif FABRIC
    @Override
    public void onInitializeClient() {
        LOGGER.info("[Locolourtor] Initialising on Fabric...");

        try {
            System.setProperty("java.net.preferIPv4Addresses", "true");
        } catch (Throwable ignored) {
        }

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                LocolourtorCommand.register(dispatcher));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ColourCache.INSTANCE.clear();
            refreshColoursForWorldPlayers(client);
            dev.gaminggeek.locolourtor.ws.WebSocketManager.INSTANCE.connect();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ColourCache.INSTANCE.clear();
            dev.gaminggeek.locolourtor.ws.WebSocketManager.INSTANCE.disconnect();
        });

        ClientTickEvents.END_CLIENT_TICK.register(Locolourtor::onClientTick);

        LOGGER.info("[Locolourtor] Ready.");
    }
    //#endif

    private static void onClientTick(
            //#if FABRIC && MC <= 12111
            MinecraftClient client
            //#else
            //$$ Minecraft client
            //#endif
    ) {
        if (client.player == null) return;

        discoveryTickCounter++;
        if (discoveryTickCounter >= DISCOVERY_CHECK_INTERVAL_TICKS) {
            discoveryTickCounter = 0;
            checkForNewPlayers(client);
        }

        fullRefreshTickCounter++;
        if (fullRefreshTickCounter >= FULL_REFRESH_INTERVAL_TICKS) {
            fullRefreshTickCounter = 0;
            refreshColoursForWorldPlayers(client);
        }
    }

    private static void checkForNewPlayers(
            //#if FABRIC && MC <= 12111
            MinecraftClient client
            //#else
            //$$ Minecraft client
            //#endif
    ) {
        //#if FABRIC && MC <= 12111
        if (client.getNetworkHandler() == null) return;
        var entries = client.getNetworkHandler().getPlayerList();
        UUID selfUuid = client.getSession().getUuidOrNull();
        //#else
        //$$ if (client.getConnection() == null) return;
        //$$ var entries = client.getConnection().getOnlinePlayers();
        //$$ UUID selfUuid = client.getUser().getProfileId();
        //#endif

        if (entries == null || entries.isEmpty()) return;

        List<UUID> activeUuids = entries.stream()
                .map(entry -> entry.getProfile().id())
                .collect(Collectors.toList());

        ColourCache.INSTANCE.retainAll(activeUuids, selfUuid);

        List<UUID> missingUuids = activeUuids.stream()
                .filter(uuid -> !ColourCache.INSTANCE.contains(uuid))
                .collect(Collectors.toList());

        if (!missingUuids.isEmpty()) {
            LOGGER.debug("[Locolourtor] Discovered {} new player(s) to query", missingUuids.size());
            ColourSyncClient.INSTANCE.refreshColours(missingUuids);
        }
    }

    public static void refreshColoursForWorldPlayers(
            //#if FABRIC && MC <= 12111
            MinecraftClient client
            //#else
            //$$ Minecraft client
            //#endif
    ) {
        //#if FABRIC && MC <= 12111
        if (client.getNetworkHandler() == null) return;
        List<UUID> uuids = client.getNetworkHandler().getPlayerList()
                .stream()
                .map(entry -> entry.getProfile().id())
                .collect(Collectors.toList());
        //#else
        //$$ if (client.getConnection() == null) return;
        //$$ List<UUID> uuids = client.getConnection().getOnlinePlayers()
        //$$         .stream()
        //$$         .map(entry -> entry.getProfile().id())
        //$$         .collect(Collectors.toList());
        //#endif

        ColourSyncClient.INSTANCE.refreshColours(uuids);
    }
}
