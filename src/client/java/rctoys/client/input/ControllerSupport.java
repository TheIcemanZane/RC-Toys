package rctoys.client.input;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

public final class ControllerSupport {

	public static boolean ENABLED = true;

	// Which controller to read (GLFW_JOYSTICK_1..16)
	public static int JOYSTICK_ID = GLFW.GLFW_JOYSTICK_1;

	// Deadzone for sticks (applied after inversion)
	public static float DEADZONE = 0.12f;

	// Axes (defaults: left stick for roll/pitch)
	public static int AXIS_ROLL = GLFW.GLFW_GAMEPAD_AXIS_LEFT_X;   // left(-) / right(+)
	public static int AXIS_PITCH = GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y;  // up(-) / down(+)

	// Throttle: choose ONE approach.
	// Option A: right stick Y as throttle delta (simple, works everywhere)
	public static int AXIS_THROTTLE = GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y;

	// Option B (later): triggers as absolute throttle (needs more logic), keep for future:
	// public static int AXIS_TRIGGER_LEFT  = GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER;
	// public static int AXIS_TRIGGER_RIGHT = GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER;

	// Optional yaw (not used by plane physics yet, but packet supports it)
	public static int AXIS_YAW = GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X;

	// Inversion toggles
	public static boolean INVERT_PITCH = false;  // many people prefer stick up = pitch up
	public static boolean INVERT_ROLL = true;
	public static boolean INVERT_THROTTLE = true; // stick up usually negative

	// Buttons
	public static int BUTTON_BRAKE = GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER; // arbitrary default
	public static int BUTTON_JUMP  = GLFW.GLFW_GAMEPAD_BUTTON_A;
	public static int BUTTON_SHIFT = GLFW.GLFW_GAMEPAD_BUTTON_B;

	private static final GLFWGamepadState STATE = GLFWGamepadState.create();

	private ControllerSupport() {}

	public static AnalogState readAnalog() {
		AnalogState out = new AnalogState();

		if (!ENABLED) return out;
		if (!GLFW.glfwJoystickPresent(JOYSTICK_ID)) return out;
		if (!GLFW.glfwJoystickIsGamepad(JOYSTICK_ID)) return out;
		if (!GLFW.glfwGetGamepadState(JOYSTICK_ID, STATE)) return out;

		out.present = true;

		float roll = STATE.axes(AXIS_ROLL);
		float pitch = STATE.axes(AXIS_PITCH);
		float throttle = STATE.axes(AXIS_THROTTLE);
		float yaw = STATE.axes(AXIS_YAW);

		if (INVERT_ROLL) roll = -roll;
		if (INVERT_PITCH) pitch = -pitch;
		if (INVERT_THROTTLE) throttle = -throttle;

		out.roll = applyDeadzone(roll);
		out.pitch = applyDeadzone(pitch);
		out.throttle = applyDeadzone(throttle);
		out.yaw = applyDeadzone(yaw);

		out.brake = STATE.buttons(BUTTON_BRAKE) == GLFW.GLFW_PRESS;
		out.jump  = STATE.buttons(BUTTON_JUMP) == GLFW.GLFW_PRESS;
		out.shift = STATE.buttons(BUTTON_SHIFT) == GLFW.GLFW_PRESS;

		return out;
	}

	private static float applyDeadzone(float v) {
		if (Math.abs(v) < DEADZONE) return 0.0f;

		// Optional rescale so it smoothly starts after deadzone
		float sign = Math.signum(v);
		float mag = (Math.abs(v) - DEADZONE) / (1.0f - DEADZONE);
		return sign * clamp01(mag);
	}

	private static float clamp01(float v) {
		if (v < 0.0f) return 0.0f;
		if (v > 1.0f) return 1.0f;
		return v;
	}

	public static final class AnalogState {
		public boolean present = false;
		public float pitch = 0.0f;    // -1..1
		public float roll = 0.0f;     // -1..1
		public float yaw = 0.0f;      // -1..1
		public float throttle = 0.0f; // -1..1 (delta)
		public boolean brake = false;

		// optional digital buttons (if you want them)
		public boolean jump = false;
		public boolean shift = false;
	}

	public static final class AnalogDebugState {
	    public boolean present = false;
	
	    // raw values straight from GLFW state (no invert, no deadzone)
	    public float rawPitch, rawRoll, rawYaw, rawThrottle;
	
	    // processed values (invert + deadzone + rescale)
	    public float pitch, roll, yaw, throttle;
	
	    public boolean brake, jump, shift;
	
	    // extra info
	    public boolean isGamepad = false;
	    public int jid = GLFW.GLFW_JOYSTICK_1;
	}
	
	public static AnalogDebugState readAnalogDebug() {
	    AnalogDebugState out = new AnalogDebugState();
	    out.jid = JOYSTICK_ID;
	
	    if (!ENABLED) return out;
	    if (!GLFW.glfwJoystickPresent(JOYSTICK_ID)) return out;
	
	    out.isGamepad = GLFW.glfwJoystickIsGamepad(JOYSTICK_ID);
	    if (!out.isGamepad) return out;
	
	    if (!GLFW.glfwGetGamepadState(JOYSTICK_ID, STATE)) return out;
	
	    out.present = true;
	
	    // raw
	    float roll = STATE.axes(AXIS_ROLL);
	    float pitch = STATE.axes(AXIS_PITCH);
	    float throttle = STATE.axes(AXIS_THROTTLE);
	    float yaw = STATE.axes(AXIS_YAW);
	
	    out.rawRoll = roll;
	    out.rawPitch = pitch;
	    out.rawThrottle = throttle;
	    out.rawYaw = yaw;
	
	    // apply invert
	    if (INVERT_ROLL) roll = -roll;
	    if (INVERT_PITCH) pitch = -pitch;
	    if (INVERT_THROTTLE) throttle = -throttle;
	
	    // processed
	    out.roll = applyDeadzone(roll);
	    out.pitch = applyDeadzone(pitch);
	    out.throttle = applyDeadzone(throttle);
	    out.yaw = applyDeadzone(yaw);
	
	    out.brake = STATE.buttons(BUTTON_BRAKE) == GLFW.GLFW_PRESS;
	    out.jump  = STATE.buttons(BUTTON_JUMP) == GLFW.GLFW_PRESS;
	    out.shift = STATE.buttons(BUTTON_SHIFT) == GLFW.GLFW_PRESS;
	
	    return out;
	}
}