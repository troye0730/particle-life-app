package com.particle_life.app;

import imgui.*;
import imgui.callback.ImStrConsumer;
import imgui.callback.ImStrSupplier;
import imgui.flag.*;
import org.lwjgl.glfw.*;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

public class ImGuiLayer {

    private long glfwWindow;

    // Mouse cursors provided by GLFW
    private final long[] mouseCursors = new long[ImGuiMouseCursor.COUNT];

    public List<GLFWMouseButtonCallbackI> mouseButtonCallbacks = new ArrayList<>();
    public List<GLFWCharCallbackI> charCallbacks = new ArrayList<>();
    public List<GLFWScrollCallbackI> scrollCallbacks = new ArrayList<>();
    public List<GLFWCursorPosCallbackI> cursorPosCallbacks = new ArrayList<>();
    public List<GLFWKeyCallbackI> keyCallbacks = new ArrayList<>();
    private ImGuiIO io;


    private final boolean[] mouseDown = new boolean[5];
    private final boolean[] pmouseDown = new boolean[5];// previous state

    private final int[] keyMap = new int[GLFW_KEY_LAST + 1];

    public ImGuiLayer(long glfwWindow) {
        this.glfwWindow = glfwWindow;
    }

    // Initialize Dear ImGui.
    public void initImGui() {
        // IMPORTANT!!
        // This line is critical for Dear ImGui to work.
        ImGui.createContext();

        // ------------------------------------------------------------
        // Initialize ImGuiIO config
        io = ImGui.getIO();

        io.setIniFilename(null); // We don't want to save .ini file
        io.setConfigFlags(ImGuiConfigFlags.NavEnableKeyboard); // Navigation with keyboard
        io.setBackendFlags(ImGuiBackendFlags.HasMouseCursors); // Mouse cursors to display while resizing windows etc.
        io.setBackendPlatformName("imgui_java_impl_glfw");

        // ------------------------------------------------------------
        // Keyboard mapping. ImGui will use those indices to peek into the io.KeysDown[] array.
        keyMap[GLFW_KEY_TAB]         = ImGuiKey.Tab;
        keyMap[GLFW_KEY_LEFT]        = ImGuiKey.LeftArrow;
        keyMap[GLFW_KEY_RIGHT]       = ImGuiKey.RightArrow;
        keyMap[GLFW_KEY_UP]          = ImGuiKey.UpArrow;
        keyMap[GLFW_KEY_DOWN]        = ImGuiKey.DownArrow;
        keyMap[GLFW_KEY_PAGE_UP]     = ImGuiKey.PageUp;
        keyMap[GLFW_KEY_PAGE_DOWN]   = ImGuiKey.PageDown;
        keyMap[GLFW_KEY_HOME]        = ImGuiKey.Home;
        keyMap[GLFW_KEY_END]         = ImGuiKey.End;
        keyMap[GLFW_KEY_INSERT]      = ImGuiKey.Insert;
        keyMap[GLFW_KEY_DELETE]      = ImGuiKey.Delete;
        keyMap[GLFW_KEY_BACKSPACE]   = ImGuiKey.Backspace;
        keyMap[GLFW_KEY_SPACE]       = ImGuiKey.Space;
        keyMap[GLFW_KEY_ENTER]       = ImGuiKey.Enter;
        keyMap[GLFW_KEY_ESCAPE]      = ImGuiKey.Escape;
        keyMap[GLFW_KEY_KP_ENTER]    = ImGuiKey.KeypadEnter;
        keyMap[GLFW_KEY_A]           = ImGuiKey.A;
        keyMap[GLFW_KEY_C]           = ImGuiKey.C;
        keyMap[GLFW_KEY_V]           = ImGuiKey.V;
        keyMap[GLFW_KEY_X]           = ImGuiKey.X;
        keyMap[GLFW_KEY_Y]           = ImGuiKey.Y;
        keyMap[GLFW_KEY_Z]           = ImGuiKey.Z;

        // ------------------------------------------------------------
        // Mouse cursors mapping
        mouseCursors[ImGuiMouseCursor.Arrow] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
        mouseCursors[ImGuiMouseCursor.TextInput] = glfwCreateStandardCursor(GLFW_IBEAM_CURSOR);
        mouseCursors[ImGuiMouseCursor.ResizeAll] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
        mouseCursors[ImGuiMouseCursor.ResizeNS] = glfwCreateStandardCursor(GLFW_VRESIZE_CURSOR);
        mouseCursors[ImGuiMouseCursor.ResizeEW] = glfwCreateStandardCursor(GLFW_HRESIZE_CURSOR);
        mouseCursors[ImGuiMouseCursor.ResizeNESW] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
        mouseCursors[ImGuiMouseCursor.ResizeNWSE] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
        mouseCursors[ImGuiMouseCursor.Hand] = glfwCreateStandardCursor(GLFW_HAND_CURSOR);
        mouseCursors[ImGuiMouseCursor.NotAllowed] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);

        // ------------------------------------------------------------
        // GLFW callbacks to handle user input

        glfwSetInputMode(glfwWindow, GLFW_STICKY_MOUSE_BUTTONS, GLFW_TRUE);
        glfwSetInputMode(glfwWindow, GLFW_STICKY_KEYS, GLFW_TRUE);

        glfwSetKeyCallback(glfwWindow, (w, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_UNKNOWN) return;
            boolean isDown = action == GLFW_PRESS;
            int imGuiKey = keyMap[key];
            if (imGuiKey != ImGuiKey.None) {
                io.addKeyEvent(imGuiKey, isDown);
            }

            io.addKeyEvent(ImGuiKey.ModCtrl, (mods & GLFW_MOD_CONTROL) != 0);
            io.addKeyEvent(ImGuiKey.ModShift, (mods & GLFW_MOD_SHIFT) != 0);
            io.addKeyEvent(ImGuiKey.ModAlt, (mods & GLFW_MOD_ALT) != 0);
            io.addKeyEvent(ImGuiKey.ModSuper, (mods & GLFW_MOD_SUPER) != 0);

            // dispatch to application
            if (!io.getWantTextInput()) {
                keyCallbacks.forEach(callback -> callback.invoke(w, key, scancode, action, mods));
            }
        });

        glfwSetCharCallback(glfwWindow, (w, c) -> {
            if (c != GLFW_KEY_DELETE) {
                io.addInputCharacter(c);
            }

            // dispatch to application
            if (!io.getWantTextInput()) {
                charCallbacks.forEach(callback -> callback.invoke(w, c));
            }
        });

        glfwSetScrollCallback(glfwWindow, (w, xOffset, yOffset) -> {
            io.setMouseWheelH(io.getMouseWheelH() + (float) xOffset);
            io.setMouseWheel(io.getMouseWheel() + (float) yOffset);

            // dispatch to application
            if (!io.getWantCaptureMouse()) {
                scrollCallbacks.forEach(callback -> callback.invoke(w, xOffset, yOffset));
            }
        });

        glfwSetCursorPosCallback(glfwWindow, (window, xpos, ypos) -> {
            // dispatch to application
            if (!io.getWantCaptureMouse()) {
                cursorPosCallbacks.forEach(callback -> callback.invoke(window, xpos, ypos));
            }
        });

        io.setSetClipboardTextFn(new ImStrConsumer() {
            @Override
            public void accept(final String s) {
                glfwSetClipboardString(glfwWindow, s);
            }
        });

        io.setGetClipboardTextFn(new ImStrSupplier() {
            @Override
            public String get() {
                final String clipboardString = glfwGetClipboardString(glfwWindow);
                if (clipboardString != null) {
                    return clipboardString;
                } else {
                    return "";
                }
            }
        });

        final ImFontAtlas fontAtlas = io.getFonts();
        final ImFontConfig fontConfig = new ImFontConfig(); // Natively allocated object, should be explicitly destroyed
        fontAtlas.addFontFromFileTTF(".internal/Futura Heavy font.ttf", 16, fontConfig);
        fontAtlas.addFontDefault(); // Add a default font, which is 'ProggyClean.ttf, 13px'
        fontConfig.destroy(); // After all fonts were added we don't need this config anymore

        // set style
        ImGuiStyle style = ImGui.getStyle();
        style.setFrameBorderSize(0);
        style.setWindowBorderSize(0);
        style.setFrameRounding(16);
        style.setFramePadding(12, 5);
        style.setPopupRounding(8);
        style.setGrabRounding(3);
        style.setScrollbarRounding(3);
        style.setWindowRounding(8);
        style.setWindowMenuButtonPosition(ImGuiDir.Right);
        style.setWindowTitleAlign(0.5f, 0.5f);
        ImGui.styleColorsDark(style);
        style.setColor(ImGuiCol.Button, 62,99,221, 255);
        style.setColor(ImGuiCol.ButtonHovered, 92, 115, 231, 255);
        style.setColor(ImGuiCol.ButtonActive, 168, 177, 255, 255);
        style.setColor(ImGuiCol.WindowBg, 32, 33, 39, 255);
        style.setColor(ImGuiCol.TitleBg, 32, 33, 39, 255);
        style.setColor(ImGuiCol.TitleBgActive, 32, 33, 39, 255);
        style.setColor(ImGuiCol.TitleBgCollapsed, 32, 33, 39, 255);
        style.setColor(ImGuiCol.FrameBg, 50,54,63, 255);
        style.setColor(ImGuiCol.FrameBgHovered, 65,72,83, 255);
        style.setColor(ImGuiCol.FrameBgActive, 81,92,103, 255);
        style.setColor(ImGuiCol.SliderGrab, 62,99,221, 255);
        style.setColor(ImGuiCol.SliderGrabActive, 168, 177, 255, 255);
        style.setColor(ImGuiCol.CheckMark, 62,99,221, 255);
        style.setColor(ImGuiCol.HeaderHovered, 62,99,221, 255);
        style.setColor(ImGuiCol.HeaderActive, 92, 115, 231, 255);
        style.setColor(ImGuiCol.Separator, 255, 255, 255, 18);
    }

    /**
     * scale everything to a readable size
     *
     * @param scaleFactor 1.0 for original size
     */
    public void scaleGui(float scaleFactor) {

        ImGuiStyle style = ImGui.getStyle();
        style.scaleAllSizes(scaleFactor);  // scale buttons and other gui elements

        // scale font size
        ImFontAtlas fontAtlas = io.getFonts();
        ImFontConfig fontConfig = new ImFontConfig();
        fontConfig.setSizePixels(13 * scaleFactor); // default font size is 13px
        fontAtlas.addFontDefault(fontConfig);
        fontConfig.destroy();
    }

    public void processEvents() {
        processMouseButtonEvents();
    }

    private void processMouseButtonEvents() {

        // copy mouseDown to pmouseDown (save previous state)
        System.arraycopy(mouseDown, 0, pmouseDown, 0, mouseDown.length);

        int[] mouseButtons = new int[]{
                GLFW_MOUSE_BUTTON_1,
                GLFW_MOUSE_BUTTON_2,
                GLFW_MOUSE_BUTTON_3,
                GLFW_MOUSE_BUTTON_4,
                GLFW_MOUSE_BUTTON_5,
        };

        for (int i = 0; i < mouseButtons.length; i++) {
            mouseDown[i] = glfwGetMouseButton(glfwWindow, mouseButtons[i]) != GLFW_RELEASE;
        }

        io.setMouseDown(mouseDown);

        if (!io.getWantCaptureMouse() && mouseDown[1]) {
            ImGui.setWindowFocus(null);
        }

        // dispatch to application
        if (!io.getWantCaptureMouse()) {
            for (int i = 0; i < 5; i++) {
                if (mouseDown[i] != pmouseDown[i]) {
                    for (GLFWMouseButtonCallbackI callback : mouseButtonCallbacks) {
                        callback.invoke(glfwWindow, mouseButtons[i], mouseDown[i] ? GLFW_PRESS : GLFW_RELEASE, 0);
                    }
                }
            }
        }
    }

    public void setIO(final float dt, int width, int height) {

        float[] winWidth = new float[]{width};
        float[] winHeight = new float[]{height};
        double[] mousePosX = new double[]{0};
        double[] mousePosY = new double[]{0};
        glfwGetCursorPos(glfwWindow, mousePosX, mousePosY);

        // We SHOULD call those methods to update Dear ImGui state for the current frame
        final ImGuiIO io = ImGui.getIO();
        io.setDisplaySize(winWidth[0], winHeight[0]);
        io.setDisplayFramebufferScale(2f, 2f);
        io.setMousePos((float) mousePosX[0], (float) mousePosY[0]);
        io.setDeltaTime(dt);

        // Update the mouse cursor
        final int imguiCursor = ImGui.getMouseCursor();
        glfwSetCursor(glfwWindow, mouseCursors[imguiCursor]);
        glfwSetInputMode(glfwWindow, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
    }

    // If you want to clean a room after yourself - do it by yourself
    public void destroyImGui() {
        ImGui.destroyContext();
    }
}
