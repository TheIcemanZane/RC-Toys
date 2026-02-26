package rctoys.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import rctoys.RCToysMod;

public class PlaneEntity extends AbstractRCEntity
{
	// Legacy digital (bitmask) controls: -1/0/+1
	private int pitch;
	private int roll;
	private int throttleControl;

	// Analog controls: -1..1 (pitch/roll), -1..1 throttle delta
	private float pitchAnalog;
	private float rollAnalog;
	private float throttleControlAnalog;

	private float throttle;

	public PlaneEntity(EntityType<?> entityType, Level world)
	{
		super(entityType, world);
	}

	@Override
	public Item asItem()
	{
		return RCToysMod.PLANE_ITEM;
	}

	@Override
	public int getDefaultColor()
	{
		return -16201290;
	}

	@Override
	public SoundEvent getSoundLoop()
	{
		return RCToysMod.PLANE_LOOP_SOUND;
	}

	public float getMaximumThrust()
	{
		return 0.05f;
	}

	@Override
	protected double getDefaultGravity()
	{
		return 0.05;
	}

	@Override
	public void tickPhysics()
	{
		if(!isEnabled())
		{
			pitch = 0;
			roll = 0;
			throttleControl = 0;

			pitchAnalog = 0.0f;
			rollAnalog = 0.0f;
			throttleControlAnalog = 0.0f;

			throttle = 0.0f;
		}

		// If analog hasn't been updated recently, decay it to zero so you don't get "stuck"
		if (this.ctrlTicksSinceUpdate > 50) {
			pitchAnalog *= 0.85f;
			rollAnalog *= 0.85f;
			throttleControlAnalog *= 0.85f;
			if (Math.abs(pitchAnalog) < 0.001f) pitchAnalog = 0.0f;
			if (Math.abs(rollAnalog) < 0.001f) rollAnalog = 0.0f;
			if (Math.abs(throttleControlAnalog) < 0.001f) throttleControlAnalog = 0.0f;
		}

		float pitchInput = (float)pitch + pitchAnalog;
		float rollInput = (float)roll + rollAnalog;
		float throttleDelta = (float)throttleControl + throttleControlAnalog;

		pitchInput = Mth.clamp(pitchInput, -1.0f, 1.0f);
		rollInput = Mth.clamp(rollInput, -1.0f, 1.0f);
		throttleDelta = Mth.clamp(throttleDelta, -1.0f, 1.0f);

		throttle = Mth.clamp(throttle + throttleDelta * 0.1f, 0.0f, 1.0f);
		setThrottle(throttle);

		Quaternionf quaternion = getQuaternion();
		Quaternionf invQuaternion = new Quaternionf(quaternion).invert();

		Vector3f acc = new Vector3f(0.0f, 0.0f, -1.0f).rotate(quaternion).mul(getMaximumThrust() * throttle);
		Vector3f right = new Vector3f(-1.0f, 0.0f, 0.0f).rotate(quaternion);

		float wingSpan = 0.8f;
		float wingArea = 0.5f;
		float aspectRatio = (wingSpan * wingSpan) / wingArea;

		Vector3f velocity = getDeltaMovement().toVector3f();
		Vector3f localVelocity = new Vector3f(velocity).rotate(invQuaternion);

		float stallAngle = 0.25f;
		float angleOfAttack = (float) -Math.atan2(localVelocity.y(), -localVelocity.z());
		float inducedLift = angleOfAttack * (aspectRatio / (aspectRatio + 2.0f)) * Mth.PI * 2.0f;

		if(Math.abs(angleOfAttack) > stallAngle)
			inducedLift *= (float) Math.cos((Math.abs(angleOfAttack) - stallAngle) * (Mth.PI / 4.0f));

		float inducedDrag = (inducedLift * inducedLift) / (aspectRatio * Mth.PI);

		if(Math.abs(angleOfAttack) > stallAngle)
			inducedDrag += (Math.abs(angleOfAttack) - stallAngle) * 0.5f;

		if(velocity.lengthSquared() > 1e-8f)
		{
			float dynamicPressure = velocity.lengthSquared() * wingArea * 1.225f * 0.5f;
			Vector3f lift = new Vector3f(velocity).normalize().cross(right).mul(Mth.clamp(inducedLift * dynamicPressure, 0.0f, 16.0f));
			Vector3f drag = new Vector3f(velocity).normalize((inducedDrag + 0.1f) * dynamicPressure);

			if(lift.isFinite())
				acc.add(lift);

			if(drag.isFinite())
				acc.sub(drag);
		}

		push(acc.x(), acc.y(), acc.z());

		applyGravity();

		if(isInWater() || (onGround() && throttle == 0.0f))
			setDeltaMovement(getDeltaMovement().multiply(0.8f, 0.5f, 0.8f));

		move(MoverType.SELF, getDeltaMovement());
	}

	@Override
	public Quaternionf updateQuaternion()
	{
		Quaternionf quaternion = getQuaternion();
		Vector3f velocity = getDeltaMovement().toVector3f();

		float pitchInput = Mth.clamp((float)pitch + pitchAnalog, -1.0f, 1.0f);
		float rollInput  = Mth.clamp((float)roll  + rollAnalog,  -1.0f, 1.0f);

		if(velocity.lengthSquared() > 1e-8f)
		{
			Quaternionf invQuaternion = new Quaternionf(quaternion).invert();
			Vector3f localVelocity = new Vector3f(velocity).rotate(invQuaternion).normalize();

			quaternion.rotateX(localVelocity.y() * 0.1f);
			quaternion.rotateY(localVelocity.x()  * -0.1f);

			float speedFactor = Mth.clamp(velocity.length() * 0.05f, 0.0f, 0.1f);

			if(!onGround())
				quaternion.rotateZ(rollInput * speedFactor);

			quaternion.rotateX(pitchInput * speedFactor);
		}

		return quaternion.normalize();
	}

	@Override
	public void remoteControlInput(boolean[] inputArray)
	{
		pitch = 0;
		roll = 0;
		throttleControl = 0;

		// Pitch Down
		if(inputArray[0])
			pitch--;

		// Pitch Up
		if(inputArray[1])
			pitch++;

		// Roll Left
		if(inputArray[2])
			roll++;

		// Roll Right
		if(inputArray[3])
			roll--;

		// Throttle Up
		if(inputArray[4])
			throttleControl++;

		// Throttle Down
		if(inputArray[5])
			throttleControl--;
	}

	@Override
	public void remoteControlAnalogInput(float pitch, float roll, float yaw, float throttle, boolean brake)
	{
		// IMPORTANT: do NOT call super.remoteControlAnalogInput(...) here,
		// because it converts analog -> legacy booleans and calls remoteControlInput(),
		// which fights our real analog controls.
		storeAnalogInput(pitch, roll, yaw, throttle, brake);

		this.pitchAnalog = Mth.clamp(pitch, -1.0f, 1.0f);
		this.rollAnalog = Mth.clamp(roll, -1.0f, 1.0f);
		this.throttleControlAnalog = Mth.clamp(throttle, -1.0f, 1.0f);
	}

	@Override
	public boolean canSpawnSprintParticle()
	{
		return getDeltaMovement().length() > 0.25;
	}
}