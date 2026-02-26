package rctoys.client.input;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ControllerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "rctoys-controller.json";

    /** Empty = auto-pick first connected. */
    public String selectedGuid = "";

    /** New: per-axis inversion options. */
    public boolean invertPitch = false;
    public boolean invertRoll = false;

    public static ControllerConfig load() {
        Path file = filePath();
        if (Files.isRegularFile(file)) {
            try {
                ControllerConfig cfg = GSON.fromJson(Files.readString(file), ControllerConfig.class);
                return cfg == null ? new ControllerConfig() : cfg;
            } catch (Exception ignored) {}
        }
        return new ControllerConfig();
    }

    public void save() {
        Path file = filePath();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[rctoys] Failed to save controller config: " + e);
        }
    }

    private static Path filePath() {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        return gameDir.resolve("config").resolve(FILE_NAME);
    }
}