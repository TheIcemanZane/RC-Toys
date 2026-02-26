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
    // Custom category (newer MC uses a Category object, not a String)
    public static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(RCToysMod.MOD_ID, "controls"));

    // Mirrors old behavior by default (WASD/Space/Shift) but now configurable.
    public static KeyMapping FORWARD;
    public static KeyMapping BACK;
    public static KeyMapping LEFT;
    public static KeyMapping RIGHT;
    public static KeyMapping JUMP;
    public static KeyMapping SHIFT;

    // Replaces hardcoded V
    public static KeyMapping TOGGLE_FPV;

    private RCToyKeybinds() {}

    public static void register() {
        FORWARD = register("key.rctoys.forward", GLFW.GLFW_KEY_W);
        BACK    = register("key.rctoys.back",    GLFW.GLFW_KEY_S);
        LEFT    = register("key.rctoys.left",    GLFW.GLFW_KEY_A);
        RIGHT   = register("key.rctoys.right",   GLFW.GLFW_KEY_D);
        JUMP    = register("key.rctoys.jump",    GLFW.GLFW_KEY_SPACE);
        SHIFT   = register("key.rctoys.shift",   GLFW.GLFW_KEY_LEFT_SHIFT);

        TOGGLE_FPV = register("key.rctoys.toggle_fpv", GLFW.GLFW_KEY_V);
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