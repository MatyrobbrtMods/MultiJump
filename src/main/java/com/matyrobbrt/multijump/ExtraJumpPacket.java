package com.matyrobbrt.multijump;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ExtraJumpPacket implements CustomPacketPayload {
    public static final Type<ExtraJumpPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("multijump", "extra_jump"));
    public static final ExtraJumpPacket INSTANCE = new ExtraJumpPacket();

    public void handle(IPayloadContext context) {
        int used = context.player().getData(MultiJump.USED_EXTRA_JUMPS);
        if (used < context.player().getAttributeValue(MultiJump.EXTRA_JUMPS)) {
            context.player().setData(MultiJump.USED_EXTRA_JUMPS, used + 1);
            context.player().fallDistance = 0;
            context.player().jumpFromGround();
            EnchantmentHelper.runIterationOnEquipment(context.player(),
                    (enchantment, level, item) -> Enchantment.applyEffects(
                            enchantment.value().getEffects(MultiJump.EXTRA_JUMP),
                            Enchantment.entityContext((ServerLevel) context.player().level(), level, context.player(), context.player().position()),
                            effect -> effect.apply((ServerLevel) context.player().level(), level, item, context.player(), context.player().position())
                    ));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
