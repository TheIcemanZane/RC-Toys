package rctoys.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import rctoys.client.input.ControllerSupport.AnalogState;
import rctoys.RCToysMod;
import rctoys.client.command.RCToysClientCommands;
import rctoys.client.input.ControllerSupport;
import rctoys.client.input.GamepadMappings;
import rctoys.client.input.RCToyKeybinds;
import rctoys.client.render.entity.CarEntityRenderer;
import rctoys.client.render.entity.PlaneEntityRenderer;
import rctoys.client.render.entity.model.CarEntityModel;
import rctoys.client.render.entity.model.PlaneEntityModel;
import rctoys.client.sound.DynamicSoundManager;
import rctoys.entity.AbstractRCEntity;
import rctoys.item.RemoteLinkComponent;
import rctoys.network.c2s.MotorSoundS2CPacket;
import rctoys.network.c2s.RemoteControlAnalogC2SPacket;
import rctoys.network.c2s.RemoteControlC2SPacket;
import rctoys.network.c2s.TrackingPlayerC2SPacket;

import java.util.UUID;

public class RCToysModClient implements ClientModInitializer
{
	public static final ModelLayerLocation MODEL_CAR_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(RCToysMod.MOD_ID, "rc_car"), "main");
	public static final ModelLayerLocation MODEL_PLANE_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(RCToysMod.MOD_ID, "rc_plane"), "main");

	private static final int BIT_UP    = 0;
	private static final int BIT_DOWN  = 1;
	private static final int BIT_LEFT  = 2;
	private static final int BIT_RIGHT = 3;
	private static final int BIT_JUMP  = 4;
	private static final int BIT_SHIFT = 5;

	private static int lastInput = -1;
	private static ControllerSupport.AnalogState lastAnalog = new AnalogState();
	public static UUID fpvUUID;

	private static boolean controllerInit = false;
	private static rctoys.client.input.ControllerConfig controllerConfig;

	// Toggles
	public static boolean FACE_VEHICLE_ENABLED = false;
	public static boolean REAR_CAMERA_ENABLED = true;

	// NEW: rear cam pitch offset (negative = look up, positive = look down).
	// You asked for "slightly above the plane" -> we look a bit UP so the view centers above it.
	public static float REAR_CAMERA_PITCH_OFFSET_DEG = -12.0f;

	@Override
	public void onInitializeClient()
	{
		RCToysClientCommands.register();
		RCToyKeybinds.register();

		ClientPlayNetworking.registerGlobalReceiver(MotorSoundS2CPacket.ID, (payload, context) -> DynamicSoundManager.receiveSoundPacket(payload, context));
		EntityRendererRegistry.register(RCToysMod.CAR, (context) -> new CarEntityRenderer(context));
		EntityRendererRegistry.register(RCToysMod.PLANE, (context) -> new PlaneEntityRenderer(context));
		EntityModelLayerRegistry.registerModelLayer(MODEL_CAR_LAYER, CarEntityModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(MODEL_PLANE_LAYER, PlaneEntityModel::getTexturedModelData);

		ItemTooltipCallback.EVENT.register((stack, world, ctx, lines) -> {
			RemoteLinkComponent link = stack.get(RCToysMod.REMOTE_LINK);
			if(link != null && !link.name().isEmpty())
				lines.add(Component.translatable("Linked to %s", link.name()).withStyle(ChatFormatting.GRAY));
		});

		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			GamepadMappings.ensureLoadedOnce();
			GamepadMappings.ensureConfigExists();

			if (!controllerInit) {
				controllerInit = true;
				controllerConfig = rctoys.client.input.ControllerConfig.load();
			}

			// resolve selection each tick (handles disconnect/reconnect)
			var cfg = rctoys.client.input.ControllerConfig.load();

			// Apply invert config to ControllerSupport each tick (hot-edit friendly)
			ControllerSupport.INVERT_LX  = cfg.invertRoll;
			ControllerSupport.INVERT_LY = cfg.invertPitch;

			int jid = rctoys.client.input.ControllerManager.findJidByGuid(cfg.selectedGuid)
					.orElseGet(() -> rctoys.client.input.ControllerManager.firstConnected()
							.orElse(org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1));
			rctoys.client.input.ControllerSupport.JOYSTICK_ID = jid;

			if(client.player != null && client.level != null && client.player.getMainHandItem().getComponents().has(RCToysMod.REMOTE_LINK))
			{
				UUID uuid = client.player.getMainHandItem().getComponents().get(RCToysMod.REMOTE_LINK).uuid();
				Entity e = client.level.getEntity(uuid);

				if(e instanceof AbstractRCEntity entity && entity.isEnabled())
				{
					KeyMapping.setAll();

					// --- legacy digital input (keyboard keybinds) ---
					int input = 0;
					if (RCToyKeybinds.FORWARD.isDown()) input |= (1 << BIT_UP);
					if (RCToyKeybinds.BACK.isDown())    input |= (1 << BIT_DOWN);
					if (RCToyKeybinds.LEFT.isDown())    input |= (1 << BIT_LEFT);
					if (RCToyKeybinds.RIGHT.isDown())   input |= (1 << BIT_RIGHT);
					if (RCToyKeybinds.JUMP.isDown())    input |= (1 << BIT_JUMP);
					if (RCToyKeybinds.SHIFT.isDown())   input |= (1 << BIT_SHIFT);

					// --- analog controller input ---
					ControllerSupport.AnalogState a = ControllerSupport.readAnalog();
					if (a.present) {
						ClientPlayNetworking.send(new RemoteControlAnalogC2SPacket(
						a.lx,
						a.ly,
						a.rx,
						a.ry,
						a.l2,
						a.r2,
						a.r1,
						a.l1,
						a.r3,
						a.l3,
						a.buttonA,
						a.buttonB,
						a.buttonX,
						a.buttonY,
						a.buttonStart,
						a.buttonSelect,
						a.padUp,
						a.padDown,
						a.padLeft,
						a.padRight
						));
					}

					// Block vanilla movement while holding remote
					client.options.keyUp.setDown(false);
					client.options.keyDown.setDown(false);
					client.options.keyLeft.setDown(false);
					client.options.keyRight.setDown(false);
					client.options.keyJump.setDown(false);
					client.options.keyShift.setDown(false);

					if(lastInput != input) {
						ClientPlayNetworking.send(new RemoteControlC2SPacket(input));
						lastInput = input;
					}

					// Toggle FPV
					while (RCToyKeybinds.TOGGLE_FPV.consumeClick())
					{
						if(fpvUUID == null)
							fpvUUID = uuid;
						else
							fpvUUID = null;

						ClientPlayNetworking.send(new TrackingPlayerC2SPacket(entity.getId(), fpvUUID != null));
					}

					// Toggle "face vehicle"
					while (RCToyKeybinds.TOGGLE_FACE_VEHICLE.consumeClick()) {
						FACE_VEHICLE_ENABLED = !FACE_VEHICLE_ENABLED;
						client.player.displayClientMessage(
								Component.literal("[rctoys] Face vehicle: " + (FACE_VEHICLE_ENABLED ? "ON" : "OFF"))
										.withStyle(FACE_VEHICLE_ENABLED ? ChatFormatting.GREEN : ChatFormatting.RED),
								true
						);
					}

					// Toggle "rear camera in FPV third-person"
					while (RCToyKeybinds.TOGGLE_REAR_CAMERA.consumeClick()) {
						REAR_CAMERA_ENABLED = !REAR_CAMERA_ENABLED;
						client.player.displayClientMessage(
								Component.literal("[rctoys] Rear camera (FPV+F5): " + (REAR_CAMERA_ENABLED ? "ON" : "OFF"))
										.withStyle(REAR_CAMERA_ENABLED ? ChatFormatting.GREEN : ChatFormatting.RED),
								true
						);
					}

					// Apply "face vehicle" every tick while controlling (yaw + pitch)
					if (FACE_VEHICLE_ENABLED) {
						facePlayerToEntity(client, entity);
					}

					return;
				}
			}

			// Stop tracking if no longer controlling
			if(fpvUUID != null && client.level != null) {
				Entity fpvEntity = client.level.getEntity(fpvUUID);
				if (fpvEntity != null)
					ClientPlayNetworking.send(new TrackingPlayerC2SPacket(fpvEntity.getId(), false));
			}

			lastInput = -1;
			lastAnalog = new AnalogState(); // reset
			fpvUUID = null;
		});
	}

	private static void facePlayerToEntity(Minecraft client, Entity target) {
		if (client.player == null) return;

		// aim from player eye -> target eye-ish (target center + eyeHeight)
		Vec3 from = client.player.getEyePosition();
		Vec3 to = target.position().add(0.0, target.getEyeHeight(), 0.0);

		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;

		double horiz = Math.sqrt(dx * dx + dz * dz);
		if (horiz < 1.0e-6) return;

		// yaw: atan2(z, x) - 90
		float yaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;

		// pitch: -atan2(dy, horiz) (MC pitch: +down, -up)
		float pitch = (float)(-Mth.atan2(dy, horiz) * (180.0 / Math.PI));

		client.player.setYRot(yaw);
		client.player.setYHeadRot(yaw);
		client.player.setYBodyRot(yaw);

		client.player.setXRot(pitch);
	}
}