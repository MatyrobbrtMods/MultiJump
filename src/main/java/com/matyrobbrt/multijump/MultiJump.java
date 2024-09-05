package com.matyrobbrt.multijump;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.RegistryPatchGenerator;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.DamageItem;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Mod(MultiJump.MOD_ID)
public class MultiJump {
    public static final String MOD_ID = "multijump";
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    private static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    private static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> ENCHANTMENT_COMPONENTS = DeferredRegister.create(Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE, MOD_ID);

    public static final Holder<Attribute> EXTRA_JUMPS = ATTRIBUTES.register("extra_jumps", () -> new RangedAttribute("multijump.extra_jumps", 0D, 0D, 64D) {
        @Override
        public double sanitizeValue(double value) {
            return Math.floor(super.sanitizeValue(value));
        }
    }.setSyncable(true));
    public static final Supplier<AttachmentType<Integer>> USED_EXTRA_JUMPS = ATTACHMENTS.register("used_extra_jumps", () -> AttachmentType.builder(() -> 0)
            .serialize(Codec.INT).build());

    public static final Holder<MobEffect> MULTI_JUMP = MOB_EFFECTS.register(
            "multi_jump",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x00EADE) {
                {
                    addAttributeModifier(
                            EXTRA_JUMPS, ResourceLocation.fromNamespaceAndPath(MOD_ID, "effect.multi_jump"), AttributeModifier.Operation.ADD_VALUE, key -> key + 1
                    );
                }
            }
    );

    public static final Holder<Potion> MULTI_JUMP_POTION = register("multi_jump", () -> new Potion(new MobEffectInstance(MULTI_JUMP, 3600)));
    public static final Holder<Potion> LONG_MULTI_JUMP_POTION = register(
            "long_multi_jump", () -> new Potion("multi_jump", new MobEffectInstance(MULTI_JUMP, 9600))
    );
    public static final Holder<Potion> STRONG_MULTI_JUMP_POTION = register(
            "strong_multi_jump", () -> new Potion("multi_jump", new MobEffectInstance(MULTI_JUMP, 1800, 1))
    );

    public static final ResourceKey<Enchantment> MULTI_JUMP_ENCHANTMENT = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(MOD_ID, "multi_jump"));
    public static final TagKey<Item> SUPPORTS_MULTI_JUMP = ItemTags.create(ResourceLocation.fromNamespaceAndPath(MOD_ID, "enchantable/multi_jump"));

    public static final DataComponentType<List<ConditionalEffect<EnchantmentEntityEffect>>> EXTRA_JUMP = register(
            "extra_jump", builder -> builder.persistent(ConditionalEffect.codec(EnchantmentEntityEffect.CODEC, LootContextParamSets.ENCHANTED_ENTITY).listOf())
    );

    public MultiJump(IEventBus bus) {
        ATTRIBUTES.register(bus);
        ATTACHMENTS.register(bus);
        MOB_EFFECTS.register(bus);
        ENCHANTMENT_COMPONENTS.register(bus);
        POTIONS.register(bus);

        bus.addListener((final RegisterPayloadHandlersEvent event) -> event.registrar("1.0.0")
                .playToServer(
                        ExtraJumpPacket.TYPE,
                        StreamCodec.unit(ExtraJumpPacket.INSTANCE),
                        ExtraJumpPacket::handle
                ));
        bus.addListener((final EntityAttributeModificationEvent event) -> event.add(EntityType.PLAYER, EXTRA_JUMPS));
        bus.addListener((final RegisterEvent event) -> event.register(Registries.ENCHANTMENT_LEVEL_BASED_VALUE_TYPE, ResourceLocation
                .fromNamespaceAndPath(MOD_ID, "chance"), () -> ChanceBasedValue.CODEC));

        NeoForge.EVENT_BUS.addListener((final RegisterBrewingRecipesEvent event) -> {
            var builder = event.getBuilder();
            builder.addMix(Potions.LEAPING, Items.RABBIT_FOOT, MULTI_JUMP_POTION);
            builder.addMix(MULTI_JUMP_POTION, Items.REDSTONE, LONG_MULTI_JUMP_POTION);
            builder.addMix(MULTI_JUMP_POTION, Items.GLOWSTONE_DUST, STRONG_MULTI_JUMP_POTION);
            builder.addMix(Potions.LONG_LEAPING, Items.RABBIT_FOOT, LONG_MULTI_JUMP_POTION);
            builder.addMix(Potions.STRONG_LEAPING, Items.RABBIT_FOOT, STRONG_MULTI_JUMP_POTION);
        });

        NeoForge.EVENT_BUS.addListener((final PlayerTickEvent.Post event) -> {
            if (event.getEntity() instanceof ServerPlayer sp) {
                if (sp.onGround()) {
                    sp.setData(USED_EXTRA_JUMPS, 0);
                }
            }
        });

        if (!ModList.get().isLoaded("enchdesc")) {
            NeoForge.EVENT_BUS.addListener((final ItemTooltipEvent event) -> {
                if (event.getContext().level() != null && event.getItemStack().is(Items.ENCHANTED_BOOK) && event.getItemStack().get(DataComponents.STORED_ENCHANTMENTS).getLevel(event.getContext().level().holderOrThrow(MULTI_JUMP_ENCHANTMENT)) > 0) {
                    event.getToolTip().add(Component.translatable("enchantment.multijump.multi_jump.desc").withStyle(ChatFormatting.GRAY));
                }
            });
        }

        bus.addListener((final GatherDataEvent event) -> {
            var patchedRegs = RegistryPatchGenerator.createLookup(
                    event.getLookupProvider(),
                    new RegistrySetBuilder()
                            .add(Registries.ENCHANTMENT, context -> {
                                context.register(MULTI_JUMP_ENCHANTMENT, Enchantment.enchantment(
                                                Enchantment.definition(
                                                        context.lookup(Registries.ITEM).getOrThrow(SUPPORTS_MULTI_JUMP),
                                                        context.lookup(Registries.ITEM).getOrThrow(SUPPORTS_MULTI_JUMP),
                                                        1,
                                                        5,
                                                        Enchantment.dynamicCost(15, 10),
                                                        Enchantment.dynamicCost(30, 10),
                                                        10,
                                                        EquipmentSlotGroup.FEET
                                                )
                                        )
                                        .withEffect(
                                                EnchantmentEffectComponents.ATTRIBUTES,
                                                new EnchantmentAttributeEffect(
                                                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "enchantment.multi_jump"),
                                                        EXTRA_JUMPS,
                                                        LevelBasedValue.perLevel(1F),
                                                        AttributeModifier.Operation.ADD_VALUE
                                                )
                                        )
                                        .withEffect(
                                                EXTRA_JUMP,
                                                new DamageItem(new ChanceBasedValue(
                                                        LevelBasedValue.perLevel(.25f, .5f),
                                                        LevelBasedValue.constant(1f),
                                                        LevelBasedValue.constant(0f)
                                                ))
                                        ).build(ResourceLocation.fromNamespaceAndPath(MOD_ID, "multi_jump")));
                            })
            );
            event.getGenerator().addProvider(event.includeServer(), new DatapackBuiltinEntriesProvider(
                    event.getGenerator().getPackOutput(), patchedRegs, Set.of(MOD_ID)
            ));
            event.getGenerator().addProvider(event.includeServer(), new TagsProvider<Enchantment>(event.getGenerator().getPackOutput(), Registries.ENCHANTMENT, patchedRegs.thenApply(RegistrySetBuilder.PatchedRegistries::patches)) {
                @Override
                protected void addTags(HolderLookup.Provider provider) {
                    tag(EnchantmentTags.TREASURE).add(MULTI_JUMP_ENCHANTMENT);
                    tag(EnchantmentTags.TRADEABLE).add(MULTI_JUMP_ENCHANTMENT);
                    tag(EnchantmentTags.ON_TRADED_EQUIPMENT).add(MULTI_JUMP_ENCHANTMENT);
                    tag(EnchantmentTags.ON_RANDOM_LOOT).add(MULTI_JUMP_ENCHANTMENT);
                }
            });
            event.getGenerator().addProvider(event.includeServer(), new TagsProvider<Item>(event.getGenerator().getPackOutput(), Registries.ITEM, event.getLookupProvider()) {
                @Override
                protected void addTags(HolderLookup.Provider provider) {
                    tag(SUPPORTS_MULTI_JUMP).addOptionalTag(ItemTags.FOOT_ARMOR_ENCHANTABLE).addOptional(ResourceLocation.parse("mekanism:free_runners"))
                            .addOptional(ResourceLocation.parse("mekanism:free_runners_armored"));
                }
            });
        });
    }

    private static <T> DataComponentType<T> register(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        var type = builder.apply(new DataComponentType.Builder<>()).build();
        ENCHANTMENT_COMPONENTS.register(name, () -> type);
        return type;
    }

    private static Holder<Potion> register(String name, Supplier<Potion> potion) {
        return POTIONS.register(name, potion);
    }
}
