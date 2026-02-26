package rctoys.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import rctoys.RCToysMod;

public class CarEntity extends AbstractRCEntity
{
	// Legacy digital controls: -1/0/+1
	private int throttle;
	private int steering;
	private boolean boost;

	// Analog controls: -1..1 (throttle/steering)
	private float throttleAnalog;
	private float steeringAnalog;

	public CarEntity(EntityType<?> entityType, Level world)
	{
		super(entityType, world);
	}

	@Override
	public Item asItem()
	{
		return RCToysMod.CAR_ITEM;
	}

	@Override
	public int getDefaultColor()
	{
		return -48340;
	}

	@Override
	public void tickPhysics()
	{
		if(!isEnabled())
		{
			throttle = 0;
			steering = 0;
			boost = false;

			throttleAnalog = 0.0f;
			steeringAnalog = 0.0f;
		}

		// If analog hasn't been updated recently, decay it to zero so you don't get "stuck"
		if (this.ctrlTicksSinceUpdate > 50)
		{
			throttleAnalog *= 0.85f;
			steeringAnalog *= 0.85f;

			if (Math.abs(throttleAnalog) < 0.001f) throttleAnalog = 0.0f;
			if (Math.abs(steeringAnalog) < 0.001f) steeringAnalog = 0.0f;
		}

		float throttleInput  = Mth.clamp((float)throttle + throttleAnalog, -1.0f, 1.0f);
		float steeringInput  = Mth.clamp((float)steering + steeringAnalog, -1.0f, 1.0f);
		float boostFactor    = boost ? 2.0f : 1.0f;

		// Keep whatever HUD/logic uses setThrottle in sync with combined input
		setThrottle(throttleInput * boostFactor);

		if(onGround())
		{
			// Drag: stronger when you’re not actively throttling
			boolean idle = Math.abs(throttleInput) < 0.01f;
			setDeltaMovement(getDeltaMovement().scale(idle ? 0.9 : 0.99));

			Vector3f velocity = getDeltaMovement().toVector3f();
			Vector3f horizontalVelocity = new Vector3f(velocity.x(), 0.0f, velocity.z());

			Vector3f forward = new Vector3f(0.0f, 0.0f, -1.0f)
					.rotateY(getYRot() * -Mth.DEG_TO_RAD + Mth.PI);

			float forwardMagnitude = horizontalVelocity.dot(forward);
			Vector3f forwardVelocity = new Vector3f(forward).mul(forwardMagnitude);
			Vector3f lateralVelocity = new Vector3f(horizontalVelocity).sub(forwardVelocity);

			// Forward Acceleration (analog-friendly)
			float acc = throttleInput * 0.02f * boostFactor;
			forwardVelocity.add(new Vector3f(forward).mul(acc));

			// Lateral Friction
			float lateralFriction = 0.6f;
			lateralVelocity.mul(lateralFriction);

			// New Velocity
			Vector3f newVelocity = new Vector3f(forwardVelocity).add(lateralVelocity);
			setDeltaMovement(newVelocity.x(), velocity.y(), newVelocity.z());

			// Steering (analog-friendly)
			float turnSpeed = -12.0f / (1.0f + forwardMagnitude * 2.0f);
			setYRot(getYRot() + steeringInput * turnSpeed);
		}
		else
		{
			// Pitch with vertical velocity.
			setXRot((float) (-getDeltaMovement().y() * 100.0));

			// Apply Gravity
			applyGravity();
		}

		// Extra drag in water.
		if(isInWater())
			setDeltaMovement(getDeltaMovement().multiply(0.8f, 0.5f, 0.8f));

		// Move and Jump
		double previousY = getY();
		move(MoverType.SELF, getDeltaMovement());
		double deltaY = getY() - previousY;

		if(deltaY > 0.1 && verticalCollision)
		{
			double speed = Math.hypot(getDeltaMovement().x(), getDeltaMovement().z());
			double jump = 0.1 + Math.min(speed, 1.0);
			push(0.0, jump, 0.0);
		}
	}

	@Override
	public Quaternionf updateQuaternion()
	{
		Quaternionf quaternion = new Quaternionf();
		quaternion.rotateY(getYRot() * -Mth.DEG_TO_RAD + Mth.PI);
		quaternion.rotateX(getXRot() * -Mth.DEG_TO_RAD);
		return quaternion;
	}

	@Override
	public void remoteControlInput(boolean[] inputArray)
	{
		throttle = 0;
		steering = 0;
		boost = false;

		// Accelerate Forwards
		if(inputArray[0])
			throttle++;

		// Accelerate Backwards
		if(inputArray[1])
			throttle--;

		// Turn Left
		if(inputArray[2])
			steering++;

		// Turn Right
		if(inputArray[3])
			steering--;

		// Boost
		if(inputArray[4])
			boost = true;

		// Keep throttle display roughly consistent with what tickPhysics uses
		setThrottle((float)throttle * (boost ? 2.0f : 1.0f));
	}

	@Override
	public void remoteControlAnalogInput(float lx, float ly, float rx, float ry, float l2, float r2, boolean r1, boolean l1, boolean r3, boolean l3, boolean buttonA, boolean buttonB, boolean buttonX, boolean buttonY, boolean buttonStart, boolean buttonSelect, boolean padUp, boolean padDown, boolean padLeft, boolean padRight)
	{
		// IMPORTANT: do NOT call super.remoteControlAnalogInput(...) here,
		// because it converts analog -> legacy booleans and calls remoteControlInput(),
		// which fights our real analog controls.
		storeAnalogInput(lx, ly, rx, ry, l2, r2, r1, l1, r3, l3, buttonA, buttonB, buttonX, buttonY, buttonStart, buttonSelect, padUp, padDown, padLeft, padRight);

		// Throttle: prefer dedicated throttle axis, fall back to pitch if that’s how it’s mapped
		float t = -ly;

		// Steering: prefer yaw axis, fall back to roll if that’s how it’s mapped
		float s = -rx;

		this.throttleAnalog = Mth.clamp(t, -1.0f, 1.0f);
		this.steeringAnalog = Mth.clamp(s, -1.0f, 1.0f);

		// Optional: treat brake as “no boost” (and you can expand this later if you want actual braking)
		// For now we leave boost purely on the digital boost button.
	}

	@Override
	public boolean canSpawnSprintParticle()
	{
		return getDeltaMovement().length() > 0.25;
	}
}