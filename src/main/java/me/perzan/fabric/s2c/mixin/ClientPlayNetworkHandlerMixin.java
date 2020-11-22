package me.perzan.fabric.s2c.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import me.perzan.fabric.s2c.util.ServerEventHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;

@Mixin(ClientPlayNetworkHandler.class)
class ClientPlayNetworkHandlerMixin {

    @Unique
    private final ServerEventHandler s2cHandler = new ServerEventHandler();

    @Inject(at = @At("RETURN"), method = "onGameJoin")
    private void onGameJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        s2cHandler.onGameJoin(packet);
    }
    
    @Inject(at = @At("RETURN"), method = "onDisconnect")
    private void onDisconnect(DisconnectS2CPacket packet, CallbackInfo ci) {
        s2cHandler.onDisconnect(packet);
    }

}
