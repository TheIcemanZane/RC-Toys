package rctoys;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import rctoys.entity.AbstractRCEntity;
import rctoys.entity.CarEntity;
import rctoys.entity.PlaneEntity;
import rctoys.item.RCToyItem;
import rctoys.item.RemoteItem;
import rctoys.item.RemoteLinkComponent;
import rctoys.network.c2s.MotorSoundS2CPacket;
import rctoys.network.c2s.RemoteControlC2SPacket;
import rctoys.network.c2s.RemoteControlAnalogC2SPacket;
import rctoys.network.c2s.TrackingPlayerC2SPacket;

public class RCToysMod implements ModInitializer
{
	public static final String MOD_ID = "rctoys";

	public static final EntityType<CarEntity> CAR = registerEntity("rc_car", EntityType.Builder.of(CarEntity::new, MobCategory.MISC).sized(0.4f, 0.25f).eyeHeight(0.15F).clientTrackingRange(32));
	public static final EntityType<PlaneEntity> PLANE = registerEntity("rc_plane", EntityType.Builder.of(PlaneEntity::new, MobCategory.MISC).sized(0.75f, 0.25f).eyeHeight(0.15F).clientTrackingRange(32));

	public static final Item REMOTE = registerItem("remote", settings -> new RemoteItem(settings));
	public static final Item CAR_ITEM = registerItem("rc_car", settings -> new RCToyItem(CAR, settings));
	public static final Item PLANE_ITEM = registerItem("rc_plane", settings -> new RCToyItem(PLANE, settings));
	public static final Item RESONATING_CIRCUIT = registerItem("resonating_circuit", settings -> new Item(settings));
	public static final Item MOTOR = registerItem("motor", settings -> new Item(settings));
    public static final Item WHEELS = registerItem("wheels", settings -> new Item(settings));
    public static final Item PROPELLER = registerItem("propeller", settings -> new Item(settings));
    public static final Item AERO_SURFACE = registerItem("aero_surface", settings -> new Item(settings));

	public static final CreativeModeTab RC_TOYS_ITEM_GROUP = registerItemGroup("rc_toys", CAR_ITEM);

	public static final DataComponentType<RemoteLinkComponent> REMOTE_LINK = registerItemComponent("remote_link", builder -> builder.persistent(RemoteLinkComponent.CODEC).networkSynchronized(RemoteLinkComponent.PACKET_CODEC));

	public static final SoundEvent REMOTE_LINK_SOUND = Registry.register(BuiltInRegistries.SOUND_EVENT, Identifier.fromNamespaceAndPath(MOD_ID, "remote_link"), SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MOD_ID, "remote_link")));
	public static final SoundEvent CAR_LOOP_SOUND = Registry.register(BuiltInRegistries.SOUND_EVENT, Identifier.fromNamespaceAndPath(MOD_ID, "car_loop"), SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MOD_ID, "car_loop")));
    public static final SoundEvent PLANE_LOOP_SOUND = Registry.register(BuiltInRegistries.SOUND_EVENT, Identifier.fromNamespaceAndPath(MOD_ID, "plane_loop"), SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MOD_ID, "plane_loop")));

	@Override
	public void onInitialize()
	{
	    PayloadTypeRegistry.playS2C().register(MotorSoundS2CPacket.ID, MotorSoundS2CPacket.CODEC);
	
	    PayloadTypeRegistry.playC2S().register(RemoteControlC2SPacket.ID, RemoteControlC2SPacket.CODEC);
	    PayloadTypeRegistry.playC2S().register(RemoteControlAnalogC2SPacket.ID, RemoteControlAnalogC2SPacket.CODEC); // <-- ADD
	
	    PayloadTypeRegistry.playC2S().register(TrackingPlayerC2SPacket.ID, TrackingPlayerC2SPacket.CODEC);
	
	    ServerPlayNetworking.registerGlobalReceiver(RemoteControlC2SPacket.ID, (payload, context) -> AbstractRCEntity.receiveControl(payload, context));
	    ServerPlayNetworking.registerGlobalReceiver(RemoteControlAnalogC2SPacket.ID, (payload, context) -> AbstractRCEntity.receiveAnalogControl(payload, context)); // <-- ADD
	    ServerPlayNetworking.registerGlobalReceiver(TrackingPlayerC2SPacket.ID, (payload, context) -> AbstractRCEntity.receiveTrackingPlayer(payload, context));
	}

	private static <T extends Entity> EntityType<T> registerEntity(String id, EntityType.Builder<T> type)
	{
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, id));
		EntityType<T> entityType = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type.build(key));
		return entityType;
	}

	public static Item registerItem(String id, Function<Item.Properties, Item> factory)
	{
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, id));
		Item item = factory.apply(new Item.Properties().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static <T> DataComponentType<T> registerItemComponent(String id, UnaryOperator<DataComponentType.Builder<T>> builderOperator)
	{
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, id), builderOperator.apply(DataComponentType.builder()).build());
	}

    public static CreativeModeTab registerItemGroup(String id, ItemLike icon)
    {
        ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MOD_ID, id));
        CreativeModeTab.DisplayItemsGenerator collector = (displayContext, entries) -> BuiltInRegistries.ITEM.forEach(item -> {
            if(BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(MOD_ID))
                entries.accept(item);
        });
        CreativeModeTab itemGroup = FabricItemGroup.builder().icon(() -> new ItemStack(icon)).title(Component.translatable("rctoys.itemGroup")).displayItems(collector).build();
        return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, itemGroup);
    }
}