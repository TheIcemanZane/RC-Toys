package rctoys.client.input;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ControllerManager {
    private ControllerManager() {}

    public record ControllerInfo(int jid, String name, String guid, boolean isGamepad) {}

    public static List<ControllerInfo> listConnected() {
        List<ControllerInfo> out = new ArrayList<>();
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_16; jid++) {
            if (!GLFW.glfwJoystickPresent(jid)) continue;
            String name = GLFW.glfwGetJoystickName(jid);
            String guid = GLFW.glfwGetJoystickGUID(jid);
            boolean gamepad = GLFW.glfwJoystickIsGamepad(jid);
            out.add(new ControllerInfo(
                    jid,
                    name == null ? ("Joystick " + jid) : name,
                    guid == null ? "" : guid,
                    gamepad
            ));
        }
        return out;
    }

    public static Optional<ControllerInfo> getByJid(int jid) {
        if (jid < GLFW.GLFW_JOYSTICK_1 || jid > GLFW.GLFW_JOYSTICK_16) return Optional.empty();
        if (!GLFW.glfwJoystickPresent(jid)) return Optional.empty();
        String name = GLFW.glfwGetJoystickName(jid);
        String guid = GLFW.glfwGetJoystickGUID(jid);
        boolean gamepad = GLFW.glfwJoystickIsGamepad(jid);
        return Optional.of(new ControllerInfo(
                jid,
                name == null ? ("Joystick " + jid) : name,
                guid == null ? "" : guid,
                gamepad
        ));
    }

    public static Optional<Integer> findJidByGuid(String guid) {
        if (guid == null || guid.isBlank()) return Optional.empty();
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_16; jid++) {
            if (!GLFW.glfwJoystickPresent(jid)) continue;
            String g = GLFW.glfwGetJoystickGUID(jid);
            if (guid.equals(g)) return Optional.of(jid);
        }
        return Optional.empty();
    }

    public static Optional<Integer> firstConnected() {
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_16; jid++) {
            if (GLFW.glfwJoystickPresent(jid)) return Optional.of(jid);
        }
        return Optional.empty();
    }
}