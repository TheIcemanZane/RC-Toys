package rctoys.client.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.BufferUtils.createByteBuffer;

public final class GamepadMappings {
    private static boolean loaded = false;
    private static boolean initialized = false;

    private GamepadMappings() {}

    /** Call this from a client tick AFTER the window exists. */
    public static void ensureLoadedOnce() {
        if (loaded) return;

        // Window exists => GLFW should be initialized.
        if (Minecraft.getInstance() == null || Minecraft.getInstance().getWindow() == null) return;

        loaded = true;

        // 1) Optional user-provided mappings
        Path file = Path.of("config", "rctoys-gamepad-mappings.txt");
        if (Files.isRegularFile(file)) {
            try {
                ByteBuffer buf = fileToNullTerminatedBuffer(file);
                if (buf != null) GLFW.glfwUpdateGamepadMappings(buf);
            } catch (IOException e) {
                System.err.println("[rctoys] Failed to read mappings file: " + file + " (" + e + ")");
            }
        }

        // 2) Print any GLFW error (useful!)
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var ptr = stack.mallocPointer(1);
            int code = GLFW.glfwGetError(ptr);
            if (code != 0) {
                long msgPtr = ptr.get(0);
                String msg = msgPtr == 0L ? "" : MemoryUtil.memUTF8(msgPtr);
                System.err.println("[rctoys] GLFW error after mappings: 0x" + Integer.toHexString(code) + " " + msg);
            }
        }

        // 3) Debug: show whether it became a gamepad
        int jid = ControllerSupport.JOYSTICK_ID;
        System.out.println("[rctoys] Joystick present: " + GLFW.glfwJoystickPresent(jid));
        System.out.println("[rctoys] Is gamepad: " + GLFW.glfwJoystickIsGamepad(jid));
        System.out.println("[rctoys] Name: " + GLFW.glfwGetJoystickName(jid));
        System.out.println("[rctoys] GUID: " + GLFW.glfwGetJoystickGUID(jid));
    }

    private static ByteBuffer fileToNullTerminatedBuffer(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer buf = createByteBuffer(bytes.length + 1);
        buf.put(bytes);
        buf.put((byte) 0);
        buf.flip();
        return buf;
    }

    public static void ensureConfigExists() {
        if (initialized) return;
        initialized = true;

        // Minecraft root directory
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();

        // config folder (already exists)
        Path configDir = gameDir.resolve("config");

        // your file
        Path file = configDir.resolve("rctoys-gamepad-mappings.txt");

        if (!Files.exists(file)) {
            try {
                Files.createDirectories(configDir);

                // Default template
                String template = """
                        # RC Toys controller mappings
                        # Paste SDL_GameControllerDB lines here.
                        #
                        # Example:
                        # 03000000...,Controller Name,a:b0,b:b1,x:b2,y:b3,leftx:a0,lefty:a1,...
                        #
                        # You can copy from:
                        # https://github.com/gabomdq/SDL_GameControllerDB
                        """;

                Files.writeString(file, template);

                System.out.println("[RCToys] Created default controller mapping file: " + file);
            } catch (IOException e) {
                System.err.println("[RCToys] Failed to create controller config!");
                e.printStackTrace();
            }
        }
    }
}