package rctoys.client.input;

import org.lwjgl.glfw.GLFW;

public final class ControllerFinder {
    private ControllerFinder() {}

    public static int findByNameContains(String needleLower) {
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_16; jid++) {
            if (!GLFW.glfwJoystickPresent(jid)) continue;
            String name = GLFW.glfwGetJoystickName(jid);
            if (name == null) continue;
            if (name.toLowerCase().contains(needleLower)) return jid;
        }
        return -1;
    }

    public static int firstConnected() {
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_16; jid++) {
            if (GLFW.glfwJoystickPresent(jid)) return jid;
        }
        return GLFW.GLFW_JOYSTICK_1;
    }
}