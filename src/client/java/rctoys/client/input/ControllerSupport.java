package rctoys.client.input;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

public final class ControllerSupport {

	public static boolean ENABLED = true;

	// Which controller to read (GLFW_JOYSTICK_1..16)
	public static int JOYSTICK_ID = GLFW.GLFW_JOYSTICK_1;

	// Deadzone for sticks (applied after inversion)
	public static float DEADZONE = 0.12f;

	// Sticks
	public static int AXIS_LX = GLFW.GLFW_GAMEPAD_AXIS_LEFT_X;
	public static int AXIS_LY = GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y;
	public static int AXIS_RX = GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X;
	public static int AXIS_RY = GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y;

	public static int BUTTON_R3 = GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB;
	public static int BUTTON_L3 = GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB;

	// Triggers
	public static int AXIS_L2 = GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER;
	public static int AXIS_R2 = GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER;
	public static int BUTTON_L1 = GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER;
	public static int BUTTON_R1 = GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER;

	// Inversion toggles
	public static boolean INVERT_LX = false;
	public static boolean INVERT_LY = false;
	public static boolean INVERT_RX = false;
	public static boolean INVERT_RY = false;

	// D-Pad
	public static int DPAD_UP = GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP;
	public static int DPAD_DOWN = GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN;
	public static int DPAD_LEFT = GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT;
	public static int DPAD_RIGHT = GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT;

	// Buttons
	public static int BUTTON_A  = GLFW.GLFW_GAMEPAD_BUTTON_A;
	public static int BUTTON_B = GLFW.GLFW_GAMEPAD_BUTTON_B;
	public static int BUTTON_X = GLFW.GLFW_GAMEPAD_BUTTON_X;
	public static int BUTTON_Y = GLFW.GLFW_GAMEPAD_BUTTON_Y;
	public static int BUTTON_START = GLFW.GLFW_GAMEPAD_BUTTON_START;
	public static int BUTTON_SELECT = GLFW.GLFW_GAMEPAD_BUTTON_BACK;

	private static final GLFWGamepadState STATE = GLFWGamepadState.create();

	private ControllerSupport() {}

	public static AnalogState readAnalog() {
		AnalogState out = new AnalogState();

		if (!ENABLED) return out;
		if (!GLFW.glfwJoystickPresent(JOYSTICK_ID)) return out;
		if (!GLFW.glfwJoystickIsGamepad(JOYSTICK_ID)) return out;
		if (!GLFW.glfwGetGamepadState(JOYSTICK_ID, STATE)) return out;

		out.present = true;

		float lx = STATE.axes(AXIS_LX);
		float ly = STATE.axes(AXIS_LY);
		float rx = STATE.axes(AXIS_RX);
		float ry = STATE.axes(AXIS_RY);
		float l2 = STATE.axes(AXIS_L2);
		float r2 = STATE.axes(AXIS_R2);

		if(INVERT_LX) lx = -lx;
		if(INVERT_LY) ly = -ly;
		if(INVERT_RX) rx = -rx;
		if(INVERT_RY) ry = -ry;

		out.lx = applyDeadzone(lx);
		out.ly = applyDeadzone(ly);
		out.rx = applyDeadzone(rx);
		out.ry = applyDeadzone(ry);

		out.buttonStart = STATE.buttons(BUTTON_START) == GLFW.GLFW_PRESS;
		out.buttonSelect = STATE.buttons(BUTTON_SELECT) == GLFW.GLFW_PRESS;

		out.buttonA = STATE.buttons(BUTTON_A) == GLFW.GLFW_PRESS;
		out.buttonB = STATE.buttons(BUTTON_B) == GLFW.GLFW_PRESS;
		out.buttonX = STATE.buttons(BUTTON_X) == GLFW.GLFW_PRESS;
		out.buttonY = STATE.buttons(BUTTON_Y) == GLFW.GLFW_PRESS;

		out.l1 = STATE.buttons(BUTTON_L1) == GLFW.GLFW_PRESS;
		out.r1 = STATE.buttons(BUTTON_R1) == GLFW.GLFW_PRESS;

		out.l3 = STATE.buttons(BUTTON_L3) == GLFW.GLFW_PRESS;
		out.r3 = STATE.buttons(BUTTON_R3) == GLFW.GLFW_PRESS;

		out.padUp = STATE.buttons(DPAD_UP) == GLFW.GLFW_PRESS;
		out.padDown = STATE.buttons(DPAD_DOWN) == GLFW.GLFW_PRESS;
		out.padLeft = STATE.buttons(DPAD_LEFT) == GLFW.GLFW_PRESS;
		out.padRight = STATE.buttons(DPAD_RIGHT) == GLFW.GLFW_PRESS;

		return out;
	}

	private static float applyDeadzone(float v) {
		if (Math.abs(v) < DEADZONE) return 0.0f;
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
		public float lx = 0.0f;
		public float ly = 0.0f;
		public float rx = 0.0f;
		public float ry = 0.0f;
		public float l2 = 0.0f;
		public float r2 = 0.0f;
		public boolean r1 = false;
		public boolean l1 = false;
		public boolean r3 = false;
		public boolean l3 = false;
		public boolean buttonA = false;
		public boolean buttonB = false;
		public boolean buttonX = false;
		public boolean buttonY = false;
		public boolean buttonStart = false;
		public boolean buttonSelect = false;
		public boolean padUp = false;
		public boolean padDown = false;
		public boolean padLeft = false;
		public boolean padRight = false;
	}
}