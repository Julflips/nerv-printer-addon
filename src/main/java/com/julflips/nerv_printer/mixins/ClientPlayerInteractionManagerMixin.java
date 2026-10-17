package com.julflips.nerv_printer.mixins;

import com.julflips.nerv_printer.interfaces.IClientPlayerInteractionManager;
import com.julflips.nerv_printer.modules.SuppressionPrinter;
import com.julflips.nerv_printer.utils.Utils;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(value = ClientPlayerInteractionManager.class, priority = 1002)
public abstract class ClientPlayerInteractionManagerMixin implements IClientPlayerInteractionManager {
    @Shadow
    public abstract void clickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player);

    @Inject(method = "clickSlot", at = @At("HEAD"))
    private void onClickSlotHead(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        // Spoof not moving
        Modules modules = Modules.get();
        if (modules == null) return;

        SuppressionPrinter spoof = modules.get(SuppressionPrinter.class);
        if (spoof != null && spoof.isActive() && Utils.sendingInput()) {
            if (mc.player.isSprinting()) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            }

            PlayerInput stop = new PlayerInput(false, false, false, false, false, false, false);
            mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(stop));
        }
    }

    @Inject(method = "clickSlot", at = @At("RETURN"))
    private void onClickSlotReturn(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        // Revert spoofing of input
        Modules modules = Modules.get();
        if (modules == null) return;

        SuppressionPrinter spoof = modules.get(SuppressionPrinter.class);
        if (spoof != null && spoof.isActive() && Utils.sendingInput()) {

            if (mc.player.isSprinting()) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            }

            mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
        }
    }
}
