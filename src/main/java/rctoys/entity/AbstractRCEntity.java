package rctoys.entity;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.CommonColors;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.joml.Quaternionf;
import rctoys.RCToysMod;
import rctoys.item.RemoteItem;
import rctoys.item.RemoteLinkComponent;
import rctoys.network.c2s.MotorSoundS2CPacket;
import rctoys.network.c2s.RemoteControlAnalogC2SPacket;
import rctoys.network.c2s.RemoteControlC2SPacket;
import rctoys.network.c2s.TrackingPlayerC2SPacket;

import java.util.UUID;

public abstract class AbstractRCEntity extends Entity
{
	private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(AbstractRCEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> ENABLED = SynchedEntityData.defineId(AbstractRCEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> QX = SynchedEntityData.defineId(AbstractRCEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> QY = SynchedEntityData.defineId(AbstractRCEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> QZ = SynchedEntityData.defineId(AbstractRCEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> QW = SynchedEntityData.defineId(AbstractRCEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> THROTTLE = SynchedEntityData.defineId(AbstractRCEntity.class, EntityDataSerializers.FLOAT);

	// --- Analog control state (server-side) ---
	protected float ctrlPitch;     // -1..1
	protected float ctrlRoll;      // -1..1
	protected float ctrlYaw;       // -1..1
	protected float ctrlThrottle;  // -1..1
	protected boolean ctrlBrake;
	protected int ctrlTicksSinceUpdate = 0;

	private final InterpolationHandler interpolator = new InterpolationHandler(this, 3);
	public Quaternionf clientQuaternion;
	public Quaternionf clientQuaternionPrevious;

	public FakePlayerRC fakePlayer;
	public ServerPlayer trackingPlayer;

	public AbstractRCEntity(EntityType<?> entityType, Level world)
	{
		super(entityType, world);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder)
	{
		builder.define(COLOR, Integer.valueOf(getDefaultColor()));
		builder.define(ENABLED, false);
		builder.define(QX, Float.valueOf(0.0f));
		builder.define(QY, Float.valueOf(0.0f));
		builder.define(QZ, Float.valueOf(0.0f));
		builder.define(QW, Float.valueOf(1.0f));
		builder.define(THROTTLE, Float.valueOf(0.0f));
	}

	public void setColor(int color)
	{
		this.entityData.set(COLOR, color);
	}

	public int getColor()
	{
		return this.entityData.get(COLOR).intValue();
	}

	public abstract int getDefaultColor();

	public void setEnabled(boolean enabled)
	{
		if(enabled && !isEnabled())
			this.playSound(RCToysMod.REMOTE_LINK_SOUND, 2.0f, 0.7f);

		this.entityData.set(ENABLED, enabled);
	}

	public boolean isEnabled()
	{
		return this.entityData.get(ENABLED).booleanValue();
	}

	public void setQuaternion(Quaternionf quaternion)
	{
		this.entityData.set(QX, quaternion.x());
		this.entityData.set(QY, quaternion.y());
		this.entityData.set(QZ, quaternion.z());
		this.entityData.set(QW, quaternion.w());
	}

	public Quaternionf getQuaternion()
	{
		return new Quaternionf(
				this.entityData.get(QX).floatValue(),
				this.entityData.get(QY).floatValue(),
				this.entityData.get(QZ).floatValue(),
				this.entityData.get(QW).floatValue()
		);
	}

	public Quaternionf getLerpedQuaternion(float tickProgress)
	{
		if(this.clientQuaternion == null)
			return new Quaternionf();
		else if(tickProgress == 1.0f || this.clientQuaternionPrevious == null)
			return this.clientQuaternion;

		return new Quaternionf(this.clientQuaternionPrevious).slerp(this.clientQuaternion, tickProgress);
	}

	public void setThrottle(float throttle)
	{
		this.entityData.set(THROTTLE, throttle);
	}

	public float getThrottle()
	{
		return this.entityData.get(THROTTLE).floatValue();
	}

	@Override
	public InterpolationHandler getInterpolation()
	{
		return this.interpolator;
	}

	@Override
	public void tick()
	{
		super.tick();
		this.interpolator.interpolate();
		Level level = level();

		if(level.isClientSide())
		{
			if(this.clientQuaternion != null)
			{
				this.clientQuaternionPrevious = new Quaternionf(this.clientQuaternion);
				this.clientQuaternion = new Quaternionf(this.clientQuaternion).slerp(getQuaternion(), 0.4f);
			}
			else
				this.clientQuaternion = getQuaternion();
		}
		else
		{
			if (this.ctrlTicksSinceUpdate < 1_000_000) this.ctrlTicksSinceUpdate++;

			tickPhysics();
			setQuaternion(updateQuaternion());

			for(Entity other : this.level().getEntities(this, this.getBoundingBox()))
				this.push(other);

			if(this.fakePlayer != null)
				moveFakePlayer();
		}
	}

	public abstract void tickPhysics();
	public abstract Quaternionf updateQuaternion();

	/**
	 * Legacy control: 6 booleans packed from the old bitmask.
	 * Indices: 0=up, 1=down, 2=left, 3=right, 4=jump, 5=shift
	 */
	public abstract void remoteControlInput(boolean[] inputArray);

	/**
	 * Store analog state + reset stale timer.
	 * Subclasses that implement real analog handling should call this,
	 * NOT the fallback mapping.
	 */
	protected final void storeAnalogInput(float pitch, float roll, float yaw, float throttle, boolean brake)
	{
		this.ctrlPitch = clamp11(pitch);
		this.ctrlRoll = clamp11(roll);
		this.ctrlYaw = clamp11(yaw);
		this.ctrlThrottle = clamp11(throttle);
		this.ctrlBrake = brake;
		this.ctrlTicksSinceUpdate = 0;
	}

	/**
	 * Analog control entrypoint (default behavior):
	 * Store state, then convert analog -> legacy 6 booleans and call remoteControlInput(...).
	 */
	public void remoteControlAnalogInput(float pitch, float roll, float yaw, float throttle, boolean brake)
	{
		storeAnalogInput(pitch, roll, yaw, throttle, brake);

		boolean[] input = new boolean[6];

		// NOTE: This mapping is "generic legacy" and may not match PlaneEntity's meaning.
		input[0] = this.ctrlPitch > 0.25f;   // up
		input[1] = this.ctrlPitch < -0.25f;  // down
		input[2] = this.ctrlRoll < -0.25f;   // left
		input[3] = this.ctrlRoll > 0.25f;    // right
		input[4] = this.ctrlThrottle > 0.25f;   // jump
		input[5] = this.ctrlThrottle < -0.25f;  // shift

		remoteControlInput(input);
	}

	protected static float clamp11(float v) {
		if (v < -1.0f) return -1.0f;
		if (v > 1.0f) return 1.0f;
		return v;
	}

	public abstract Item asItem();

	@Override
	protected double getDefaultGravity()
	{
		return 0.08;
	}

	@Override
	public boolean isPushable()
	{
		return true;
	}

	@Override
	public float maxUpStep()
	{
		return 0.5f;
	}

	@Override
	public boolean isPickable()
	{
		return !this.isRemoved();
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state)
	{
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand)
	{
		ItemStack stack = player.getItemInHand(hand);

		if(!stack.is(RCToysMod.REMOTE))
		{
			if(player.getScale() <= 0.31)
			{
				player.startRiding(this);
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
		}

		Level world = level();

		if(!world.isClientSide())
		{
			AbstractRCEntity previousRCEntity = RemoteItem.getRCEntity(stack, (ServerLevel) world);

			if(previousRCEntity != null)
			{
				cleanRemoteLinks(previousRCEntity.getUUID());
				previousRCEntity.setEnabled(false);
			}

			cleanRemoteLinks(getUUID());
			setEnabled(true);
			stack.set(RCToysMod.REMOTE_LINK, new RemoteLinkComponent(getUUID(), getName().getString()));
			player.displayClientMessage(Component.translatable("entity.rctoys.remote_linked"), false);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean hurtServer(ServerLevel world, DamageSource source, float amount)
	{
		this.kill(world);

		if(world.getGameRules().get(GameRules.ENTITY_DROPS))
		{
			ItemStack itemStack = new ItemStack(asItem());
			itemStack.set(DataComponents.CUSTOM_NAME, this.getCustomName());

			if(getColor() != getDefaultColor())
				itemStack.set(DataComponents.DYED_COLOR, new DyedItemColor(getColor()));

			this.spawnAtLocation(world, itemStack);
		}

		return true;
	}

	@Override
	public void onRemoval(Entity.RemovalReason reason)
	{
		Level world = level();

		if(world.isClientSide())
			return;

		cleanRemoteLinks(getUUID());
	}

	public SoundEvent getSoundLoop()
	{
		return RCToysMod.CAR_LOOP_SOUND;
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player)
	{
		ServerPlayNetworking.send(player, new MotorSoundS2CPacket(getId(), true, getSoundLoop().location()));
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player)
	{
		ServerPlayNetworking.send(player, new MotorSoundS2CPacket(getId(), false, getSoundLoop().location()));
	}

	private void moveFakePlayer() {
		ServerLevel serverLevel = (ServerLevel) this.level();
		this.fakePlayer.setOldPosAndRot();
		this.fakePlayer.setPos(this.position());
		serverLevel.getChunkSource().move(this.fakePlayer);
	}

	private void cleanRemoteLinks(UUID rcUUID)
	{
		for(ServerPlayer player : ((ServerLevel) level()).players())
		{
			for(ItemStack stack : player.getInventory())
			{
				if(stack.has(RCToysMod.REMOTE_LINK))
				{
					UUID foundUUID = stack.get(RCToysMod.REMOTE_LINK).uuid();

					if(rcUUID.equals(foundUUID))
						stack.set(RCToysMod.REMOTE_LINK, null);
				}
			}
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput view)
	{
		setColor(view.getIntOr("color", CommonColors.WHITE));
		setQuaternion(new Quaternionf(
				view.getFloatOr("qx", 0.0f),
				view.getFloatOr("qy", 0.0f),
				view.getFloatOr("qz", 0.0f),
				view.getFloatOr("qw", 0.0f)
		).normalize());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput view)
	{
		view.putInt("color", this.entityData.get(COLOR).intValue());
		view.putFloat("qx", this.entityData.get(QX).floatValue());
		view.putFloat("qy", this.entityData.get(QY).floatValue());
		view.putFloat("qz", this.entityData.get(QZ).floatValue());
		view.putFloat("qw", this.entityData.get(QW).floatValue());
	}

	// -------------------------
	// Network receivers
	// -------------------------

	public static void receiveControl(RemoteControlC2SPacket payload, ServerPlayNetworking.Context context)
	{
		int input = payload.input();

		context.server().execute(() -> {
			ServerPlayer player = context.player();
			ItemStack stack = player.getMainHandItem();

			if(stack.is(RCToysMod.REMOTE) && stack.has(RCToysMod.REMOTE_LINK))
			{
				UUID rcUUID = stack.get(RCToysMod.REMOTE_LINK).uuid();
				Entity entity = player.level().getEntity(rcUUID);

				if(entity instanceof AbstractRCEntity rc)
					rc.remoteControlInput(unpackInput(input));
			}
		});
	}

	public static void receiveAnalogControl(RemoteControlAnalogC2SPacket payload, ServerPlayNetworking.Context context)
	{
		context.server().execute(() -> {
			ServerPlayer player = context.player();
			ItemStack stack = player.getMainHandItem();

			if(stack.is(RCToysMod.REMOTE) && stack.has(RCToysMod.REMOTE_LINK))
			{
				UUID rcUUID = stack.get(RCToysMod.REMOTE_LINK).uuid();
				Entity entity = player.level().getEntity(rcUUID);

				if(entity instanceof AbstractRCEntity rc && rc.isEnabled())
				{
					rc.remoteControlAnalogInput(
							payload.pitch(),
							payload.roll(),
							payload.yaw(),
							payload.throttle(),
							payload.brake()
					);
				}
			}
		});
	}

	public static boolean[] unpackInput(int input)
	{
		boolean[] inputArray = new boolean[6];

		for(int i = 0; i < 6; i++)
			inputArray[i] = ((input >> i) & 1) == 1;

		return inputArray;
	}

	public static void receiveTrackingPlayer(TrackingPlayerC2SPacket payload, ServerPlayNetworking.Context context) {
		int entityID = payload.entityID();
		boolean enable = payload.enable();

		context.server().execute(() -> {
			ServerPlayer player = context.player();
			ServerLevel serverLevel = player.level();
			Entity entity = serverLevel.getEntity(entityID);

			if(entity instanceof AbstractRCEntity rcEntity) {
				if(enable) {
					rcEntity.trackingPlayer = player;

					if (rcEntity.fakePlayer == null) {
						GameProfile gameProfile = new GameProfile(UUID.randomUUID(), "RC_Toy");
						rcEntity.fakePlayer = new FakePlayerRC(serverLevel, gameProfile, rcEntity);
						serverLevel.addNewPlayer(rcEntity.fakePlayer);
					}
				}
				else {
					if(rcEntity.trackingPlayer != null) {
						rcEntity.trackingPlayer.setChunkTrackingView(ChunkTrackingView.EMPTY);
						rcEntity.trackingPlayer = null;
					}
					if(rcEntity.fakePlayer != null) {
						serverLevel.removePlayerImmediately(rcEntity.fakePlayer, RemovalReason.DISCARDED);
						rcEntity.fakePlayer = null;
					}
				}
			}
		});
	}
}