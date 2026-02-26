package rctoys.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import rctoys.RCToysMod;

/**
 * Rebindable RC controls that show up in Options -> Controls.
 */
public final class RCToyKeybinds {
    public static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(RCToysMod.MOD_ID, "controls"));

    public static KeyMapping FORWARD;
    public static KeyMapping BACK;
    public static KeyMapping LEFT;
    public static KeyMapping RIGHT;
    public static KeyMapping JUMP;
    public static KeyMapping SHIFT;

    public static KeyMapping TOGGLE_FPV;

    // NEW:
    public static KeyMapping TOGGLE_FACE_VEHICLE;
    public static KeyMapping TOGGLE_REAR_CAMERA;

    private RCToyKeybinds() {}

    public static void register() {
        FORWARD = register("key.rctoys.forward", GLFW.GLFW_KEY_W);
        BACK    = register("key.rctoys.back",    GLFW.GLFW_KEY_S);
        LEFT    = register("key.rctoys.left",    GLFW.GLFW_KEY_A);
        RIGHT   = register("key.rctoys.right",   GLFW.GLFW_KEY_D);
        JUMP    = register("key.rctoys.jump",    GLFW.GLFW_KEY_SPACE);
        SHIFT   = register("key.rctoys.shift",   GLFW.GLFW_KEY_LEFT_SHIFT);

        TOGGLE_FPV = register("key.rctoys.toggle_fpv", GLFW.GLFW_KEY_V);

        // Defaults chosen to not conflict with common movement:
        TOGGLE_FACE_VEHICLE = register("key.rctoys.toggle_face_vehicle", GLFW.GLFW_KEY_B);
        TOGGLE_REAR_CAMERA  = register("key.rctoys.toggle_rear_camera",  GLFW.GLFW_KEY_N);
    }

    private static KeyMapping register(String translationKey, int defaultKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
            translationKey,
            InputConstants.Type.KEYSYM,
            defaultKey,
            CATEGORY
        ));
    }
}