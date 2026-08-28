package dev.gaminggeek.locolourtor.auth;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

//#if FABRIC && MC <= 12111
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.encryption.PlayerKeyPair;
import net.minecraft.network.encryption.PlayerPublicKey;
//#else
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.world.entity.player.ProfilePublicKey;
//$$ import net.minecraft.world.entity.player.ProfileKeyPair;
//#endif

public final class MojangAuth {

    public record VerificationPayload(
            String uuid,
            String username,
            long timestamp,
            String publicKey,
            long expiresAt,
            String keySignature,
            String actionSignature) {
    }

    private MojangAuth() {
    }

    public static CompletableFuture<VerificationPayload> createVerificationPayload(
            // #if FABRIC && MC <= 12111
            MinecraftClient client
            // #else
            // $$ Minecraft client
            // #endif
    ) {
        return client.getProfileKeys().fetchKeyPair().thenApply(optKeyPair -> {
            if (optKeyPair.isEmpty()) {
                throw new AuthException("No Mojang profile key found");
            }

            // #if FABRIC && MC <= 12111
            PlayerKeyPair keyPair = optKeyPair.get();
            PlayerPublicKey.PublicKeyData keyData = keyPair.publicKey().data();
            UUID uuid = client.getSession().getUuidOrNull();
            String username = client.getSession().getUsername();
            // #else
            // $$ ProfileKeyPair keyPair = optKeyPair.get();
            // $$ ProfilePublicKey.Data keyData = keyPair.publicKey().data();
            // $$ UUID uuid = client.getUser().getProfileId();
            // $$ String username = client.getUser().getName();
            // #endif

            if (uuid == null) {
                throw new AuthException("Cannot authenticate in offline mode (missing player UUID).");
            }

            long timestamp = System.currentTimeMillis();
            long expiresAt = keyData.expiresAt().toEpochMilli();
            String publicKeyB64 = Base64.getEncoder().encodeToString(keyData.key().getEncoded());
            String keySignatureB64 = Base64.getEncoder().encodeToString(keyData.keySignature());

            String challenge = "locolourtor-auth:" + uuid + ":" + timestamp;
            String actionSignatureB64;
            try {
                Signature signature = Signature.getInstance("SHA256withRSA");
                signature.initSign(keyPair.privateKey());
                signature.update(challenge.getBytes(StandardCharsets.UTF_8));
                actionSignatureB64 = Base64.getEncoder().encodeToString(signature.sign());
            } catch (Exception e) {
                throw new AuthException("Failed to sign authentication challenge", e);
            }

            return new VerificationPayload(
                    uuid.toString(),
                    username,
                    timestamp,
                    publicKeyB64,
                    expiresAt,
                    keySignatureB64,
                    actionSignatureB64);
        });
    }

    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }

        public AuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
