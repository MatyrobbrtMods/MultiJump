package com.matyrobbrt.multijump;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(value = MultiJump.MOD_ID, dist = Dist.CLIENT)
public class MultiJumpClient {
    private static final ModConfigSpec CONFIG_SPEC;
    private static final ModConfigSpec.EnumValue<HUDIndicatorDisplay> HUD_INDICATOR_DISPLAY;

    static {
        var builder = new ModConfigSpec.Builder();
        HUD_INDICATOR_DISPLAY = builder
                .comment("How to display the hud indicator for jumps remaining.")
                .comment("OFF = disable the indicator",
                        "COMPRESSED_WHEN_NEEDED = display the jumps similar to armour when there are 10 or less jumps in total, and compress when there are more than 10",
                        "ALWAYS_COMPRESSED = compress the jump amount into a number").defineEnum("hud_indicator", HUDIndicatorDisplay.COMPRESSED_WHEN_NEEDED);
        CONFIG_SPEC = builder.build();
    }

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MultiJump.MOD_ID, "textures/gui/extra_jump.png");
    private static final ResourceLocation TEXTURE_EMPTY = ResourceLocation.fromNamespaceAndPath(MultiJump.MOD_ID, "textures/gui/extra_jump_empty.png");

    private static int clientJumpCount;
    private static boolean jumpPressed;

    public MultiJumpClient(ModContainer container, IEventBus bus) {
        container.registerConfig(ModConfig.Type.CLIENT, CONFIG_SPEC, container.getModId() + "-client.toml");
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        NeoForge.EVENT_BUS.addListener((final PlayerTickEvent.Post event) -> {
            var player = Minecraft.getInstance().player;
            if (player == event.getEntity() && !player.getAbilities().flying) {
                if (player.onGround()) {
                    // If we hit the ground, reset the counter
                    clientJumpCount = 0;
                }

                if (player.input.jumping) {
                    // Only jump when the button is pressed the first tick (don't jump every tick space is held)
                    // Also, don't attempt to jump in water
                    if (!jumpPressed && !player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value())) {
                        var extra = (int)player.getAttributeValue(MultiJump.EXTRA_JUMPS);
                        if (extra > 0) {
                            // Let vanilla handle its own jump when on the ground (not mid-air jumps)
                            if ((clientJumpCount > 0 || !player.onGround()) && clientJumpCount <= extra) {
                                player.jumpFromGround();
                                // Make the server cause the player to jump serverside too
                                PacketDistributor.sendToServer(ExtraJumpPacket.INSTANCE);
                            }
                            clientJumpCount++;
                            if (clientJumpCount > extra + 1) clientJumpCount = extra + 1;
                        }
                    }

                    jumpPressed = true;
                } else {
                    jumpPressed = false;
                }
            }
        });

        bus.addListener((final RegisterGuiLayersEvent event) -> {
            event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, ResourceLocation.fromNamespaceAndPath(MultiJump.MOD_ID, "jumps"), (guiGraphics, deltaTracker) -> {
                Player player = Minecraft.getInstance().player;
                if (player != null) {
                    var configValue = HUD_INDICATOR_DISPLAY.get();
                    if (configValue == HUDIndicatorDisplay.OFF) return;
                    int jumps = ((int)player.getAttributeValue(MultiJump.EXTRA_JUMPS) + 1);
                    if (jumps == 1 || player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value())) return;

                    int y = guiGraphics.guiHeight() - 39 - 10;
                    if (Math.min(player.getAirSupply(), player.getMaxAirSupply()) < player.getMaxAirSupply()) {
                        y -= 10;
                    }

                    int x = guiGraphics.guiWidth() / 2 + 91;
                    if (configValue == HUDIndicatorDisplay.ALWAYS_COMPRESSED || jumps > 10) {
                        var text = Component.translatable("hud.multijump.compressed_jumps", clientJumpCount, jumps);
                        // We consider the width of the full amount of jumps to avoid dynamically moving the icon as 0/11 is smaller than 11/11
                        var width = Minecraft.getInstance().font.width(Component.translatable("hud.multijump.compressed_jumps", jumps, jumps));
                        guiGraphics.blit(clientJumpCount == jumps ? TEXTURE_EMPTY : TEXTURE, x - width - 9 - 1, y, 0, 0, 9, 9, 9, 9);
                        guiGraphics.drawString(Minecraft.getInstance().font, text, x - Minecraft.getInstance().font.width(text), y + 1, 0xffffff);
                    } else {
                        for (int i = 0; i < jumps; i++) {
                            guiGraphics.blit(clientJumpCount > i ? TEXTURE_EMPTY : TEXTURE, x - (jumps - i) * 9 + (jumps - i - 1), y, 0, 0, 9, 9, 9, 9);
                        }
                    }
                }
            });
        });
    }

    public enum HUDIndicatorDisplay {
        OFF,
        ALWAYS_COMPRESSED,
        COMPRESSED_WHEN_NEEDED
    }
}
