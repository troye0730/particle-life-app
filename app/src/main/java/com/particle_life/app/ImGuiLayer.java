package com.particle_life.app;

import imgui.*;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiDir;

import static org.lwjgl.glfw.GLFW.*;

public class ImGuiLayer {

    private long glfwWindow;

    private ImGuiIO io;

    private final boolean[] mouseDown = new boolean[5];
    private final boolean[] pmouseDown = new boolean[5];// previous state

    public ImGuiLayer(long glfwWindow) {
        this.glfwWindow = glfwWindow;
    }

    public void initImGui() {
        // IMPORTANT!!
        // This line is critical for Dear ImGui to work.
        ImGui.createContext();

        // ------------------------------------------------------------
        // Initialize ImGuiIO config
        io = ImGui.getIO();

        io.setIniFilename(null); // We don't want to save .ini file

        glfwSetScrollCallback(glfwWindow, (w, xOffset, yOffset) -> {
            io.setMouseWheelH(io.getMouseWheelH() + (float) xOffset);
            io.setMouseWheel(io.getMouseWheel() + (float) yOffset);
        });

        final ImFontAtlas fontAtlas = io.getFonts();
        final ImFontConfig fontConfig = new ImFontConfig();
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
            mouseDown[i] = glfwGetMouseButton(glfwWindow, mouseButtons[i]) == GLFW_PRESS;
        }

        io.setMouseDown(mouseDown);
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
    }

    // If you want to clean a room after yourself - do it by yourself
    public void destroyImGui() {
        ImGui.destroyContext();
    }
}
