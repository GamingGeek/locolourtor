package dev.gaminggeek.locolourtor.mixin;

import dev.gaminggeek.locolourtor.cache.ColourCache;
//#if FABRIC && MC <= 12111
import net.minecraft.client.gui.hud.bar.LocatorBar;
//#elseif MC < 260200
//$$ import net.minecraft.client.gui.contextualbar.LocatorBarRenderer;
//#else
//$$ import net.minecraft.client.gui.contextualbar.LocatorBar;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

//#if FABRIC && MC <= 12111
@Mixin(LocatorBar.class)
//#elseif MC < 260200
//$$ @Mixin(LocatorBarRenderer.class)
//#else
//$$ @Mixin(LocatorBar.class)
//#endif
public abstract class LocatorBarMixin {

    //#if FABRIC && MC <= 12111
    @Inject(method = "method_70872", at = @At("HEAD"), cancellable = true)
    private static void locolourtor$overrideUuidColor(UUID uuid, CallbackInfoReturnable<Integer> cir) {
        ColourCache.INSTANCE.get(uuid).ifPresent(cir::setReturnValue);
    }
    //#elseif MC <= 12111
    //$$ @Inject(method = "lambda$render$2", at = @At("HEAD"), cancellable = true, remap = false)
    //$$ private static void locolourtor$overrideUuidColor(UUID uuid, CallbackInfoReturnable<Integer> cir) {
    //$$     ColourCache.INSTANCE.get(uuid).ifPresent(cir::setReturnValue);
    //$$ }
    //#else
    //$$ @Inject(method = "lambda$extractRenderState$4", at = @At("HEAD"), cancellable = true, remap = false)
    //$$ private static void locolourtor$overrideUuidColor(UUID uuid, CallbackInfoReturnable<Integer> cir) {
    //$$     ColourCache.INSTANCE.get(uuid).ifPresent(cir::setReturnValue);
    //$$ }
    //#endif
}
