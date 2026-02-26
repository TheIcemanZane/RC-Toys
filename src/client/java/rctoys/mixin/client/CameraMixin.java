package rctoys.mixin.client;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import rctoys.client.RCToysModClient;
import rctoys.entity.AbstractRCEntity;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class CameraMixin
{
    @Shadow private Level level;
    @Shadow private Entity entity;
    @Shadow private float xRot;
    @Shadow private float yRot;
    @Shadow private boolean detached;
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private static Vector3f FORWARDS;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private static Vector3f UP;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private static Vector3f LEFT;
    @Shadow @Final private Vector3f left;

    @Shadow
    protected abstract void setRotation(float f, float g);

    @Shadow
    protected abstract void setPosition(double d, double e, double f);

    @Shadow
    protected abstract void move(float f, float g, float h);

    @Shadow
    protected abstract float getMaxZoom(float f);

    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private void setupInject(Level level, Entity entity, boolean bl, boolean bl2, float f, CallbackInfo info) {
        Minecraft client = Minecraft.getInstance();
        this.level = level;
        this.entity = entity;
        this.detached = bl;

        if(RCToysModClient.fpvUUID != null && client.level != null) {
            Entity fpvEntity = client.level.getEntity(RCToysModClient.fpvUUID);

            if(fpvEntity instanceof AbstractRCEntity rc) {

                boolean useRearThirdPerson = bl && RCToysModClient.REAR_CAMERA_ENABLED;

                if (useRearThirdPerson) {
                    // Third-person FPV camera follows RC orientation and is tilted so it aims slightly above the vehicle.
                    float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

                    Quaternionf q = rc.getLerpedQuaternion(partialTicks);

                    // Apply pitch offset (degrees -> radians).
                    float offRad = RCToysModClient.REAR_CAMERA_PITCH_OFFSET_DEG * Mth.DEG_TO_RAD;
                    q = new Quaternionf(q).rotateX(offRad);

                    this.rotation.set(q);
                    FORWARDS.rotate(this.rotation, this.forwards);
                    UP.rotate(this.rotation, this.up);
                    LEFT.rotate(this.rotation, this.left);

                    // Do NOT apply bl2 "front view" flip; rear cam is forced
                } else if (bl) {
                    // Normal third-person behavior based on the player view
                    this.setRotation(entity.getViewYRot(f), entity.getViewXRot(f));

                    if(bl2)
                        this.setRotation(this.yRot + 180.0f, -this.xRot);
                } else {
                    // First-person FPV mode: camera points forward with the RC quaternion
                    float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                    Quaternionf quaternion = rc.getLerpedQuaternion(partialTicks);

                    this.rotation.set(quaternion);
                    FORWARDS.rotate(this.rotation, this.forwards);
                    UP.rotate(this.rotation, this.up);
                    LEFT.rotate(this.rotation, this.left);
                }

                // Track the RC entity being controlled.
                this.setPosition(
                        Mth.lerp(f, fpvEntity.xo, fpvEntity.getX()),
                        Mth.lerp(f, fpvEntity.yo, fpvEntity.getY()) + fpvEntity.getEyeHeight(),
                        Mth.lerp(f, fpvEntity.zo, fpvEntity.getZ())
                );

                if(bl) {
                    float i = 4.0f;
                    float j = 1.0f;
                    this.move(-this.getMaxZoom(i * j), 0.0f, 0.0f);
                }

                info.cancel();
            }
        }
    }
}