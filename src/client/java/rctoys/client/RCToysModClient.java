package rctoys.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import rctoys.RCToysMod;
import rctoys.client.input.ControllerSupport;
import rctoys.client.input.GamepadMappings;
import rctoys.client.input.RCToyKeybinds;
import rctoys.client.command.RCToysClientCommands;
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
import org.lwjgl.glfw.GLFW;

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
	private static AnalogSnapshot lastAnalog = new AnalogSnapshot();
	public static UUID fpvUUID;

	private static boolean controllerInit = false;
	private static rctoys.client.input.ControllerConfig controllerConfig;

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
			int jid = rctoys.client.input.ControllerManager.findJidByGuid(cfg.selectedGuid).orElseGet(() -> rctoys.client.input.ControllerManager.firstConnected().orElse(org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1));
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
					// We send analog if a controller is present AND there's meaningful analog movement,
					// OR if we sent analog previously (to keep updates flowing while you hold an input).
					ControllerSupport.AnalogState a = ControllerSupport.readAnalog();
					boolean controllerAnalogPresent = a.present;
					if (a.present) {
						AnalogSnapshot snap = AnalogSnapshot.from(a);

						if (snap.shouldSendComparedTo(lastAnalog)) {
							ClientPlayNetworking.send(new RemoteControlAnalogC2SPacket(
									snap.pitch,
									snap.roll,
									snap.yaw,
									snap.throttle,
									snap.brake
							));
							lastAnalog = snap;
							String msg = String.format(
								"proc P%.2f R%.2f Y%.2f T%.2f | dz=%.2f",
								snap.pitch, snap.roll, snap.yaw, snap.throttle,
								ControllerSupport.DEADZONE
							);
							client.player.displayClientMessage(net.minecraft.network.chat.Component.literal(msg), true);
						}
					}

					// Also allow digital from controller (dpad-ish fallback)
					ControllerSupport.DigitalPad pad = ControllerSupport.readDigital();
					if (pad.forward) input |= (1 << BIT_UP);
					if (pad.back)    input |= (1 << BIT_DOWN);
					if (pad.left)    input |= (1 << BIT_LEFT);
					if (pad.right)   input |= (1 << BIT_RIGHT);
					if (pad.jump)    input |= (1 << BIT_JUMP);
					if (pad.shift)   input |= (1 << BIT_SHIFT);

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

					// Toggle FPV (rebindable)
					while (RCToyKeybinds.TOGGLE_FPV.consumeClick())
					{
						if(fpvUUID == null)
							fpvUUID = uuid;
						else
							fpvUUID = null;

						ClientPlayNetworking.send(new TrackingPlayerC2SPacket(entity.getId(), fpvUUID != null));
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
			lastAnalog = new AnalogSnapshot(); // reset
			fpvUUID = null;
		});
	}

	/**
	 * Small helper to rate-limit analog packets.
	 */
	private static final class AnalogSnapshot {
		// Quantized values reduce packet spam
		private static final float STEP = 1.0f / 64.0f; // ~0.0156
		private static final float EPS_SEND = STEP * 0.5f;

		public float pitch = 0.0f;
		public float roll = 0.0f;
		public float yaw = 0.0f;
		public float throttle = 0.0f;
		public boolean brake = false;

		public static AnalogSnapshot from(ControllerSupport.AnalogState a) {
			AnalogSnapshot s = new AnalogSnapshot();
			s.pitch = quant(a.pitch);
			s.roll = quant(a.roll);
			s.yaw = quant(a.yaw);
			s.throttle = quant(a.throttle);
			s.brake = a.brake;
			return s;
		}

		public boolean shouldSendComparedTo(AnalogSnapshot prev) {
			// Only send if changed enough OR brake toggled
			if (this.brake != prev.brake) return true;

			return Math.abs(this.pitch - prev.pitch) > EPS_SEND
				|| Math.abs(this.roll - prev.roll) > EPS_SEND
				|| Math.abs(this.yaw - prev.yaw) > EPS_SEND
				|| Math.abs(this.throttle - prev.throttle) > EPS_SEND;
		}

		private static float quant(float v) {
			// clamp + snap to STEP
			if (v < -1.0f) v = -1.0f;
			if (v > 1.0f) v = 1.0f;
			return Math.round(v / STEP) * STEP;
		}
	}
}