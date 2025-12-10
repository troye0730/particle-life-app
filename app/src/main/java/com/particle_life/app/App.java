package com.particle_life.app;

import org.lwjgl.glfw.GLFWVidMode;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class App {
    public static void main(String[] args) {
        App app = new App();
        app.launch("Particle Life Simulator",
            false
        );
    }

    // The window handle
    private long window;
    private int width;
    private int height;

    // remember window position and size before switching to fullscreen
    private int windowPosX;
    private int windowPosY;
    private int windowWidth = -1;
    private int windowHeight = -1;

    private void launch(String title, boolean fullscreen) {
        init(title, fullscreen);

        setCallbacks();

        while (!glfwWindowShouldClose(window)) {
            // use this to wait for events instead of polling, saving CPU
            glfwWaitEvents();
        }

        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW when done
        glfwTerminate();
    }

    private void init(String title, boolean fullscreen) {
        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable

        // Create the window
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode videoMode = glfwGetVideoMode(monitor);
        if (videoMode == null) {
            throw new RuntimeException("Unable to get video mode");
        }
        int monitorWidth = videoMode.width();
        int monitorHeight = videoMode.height();

        // Set window size and position
        double f = 0.2;
        windowPosX = (int) (f * monitorWidth / 2);;
        windowPosY = (int) (f * monitorHeight / 2);
        windowWidth = (int) ((1 - f) * monitorWidth);
        windowHeight = (int) ((1 - f) * monitorHeight);

        if (fullscreen) {
            width = monitorWidth;
            height = monitorHeight;
            window = glfwCreateWindow(width, height, title, monitor, NULL);
        } else {
            width = windowWidth;
            height = windowHeight;
            window = glfwCreateWindow(width, height, title, NULL, NULL);
        }

        if (window == NULL) {
            throw new IllegalStateException("Failed to create the GLFW window");
        }

        // Make the window visible
        glfwShowWindow(window);
    }

    private void setCallbacks() {
        // Set a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_F && action == GLFW_PRESS) {
                setFullscreen(!isFullscreen());
            }
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop
            }
        });
    }

    private boolean isFullscreen() {
        return glfwGetWindowMonitor(window) != NULL;
    }

    private void setFullscreen(boolean fullscreen) {
        if (isFullscreen() == fullscreen) {
            return;
        }

        if (fullscreen) {
            // backup window position and size
            int[] xposBuf = new int[1];
            int[] yposBuf = new int[1];
            int[] widthBuf = new int[1];
            int[] heightBuf = new int[1];
            glfwGetWindowPos(window, xposBuf, yposBuf);
            glfwGetWindowSize(window, widthBuf, heightBuf);
            windowPosX = xposBuf[0];
            windowPosY = yposBuf[0];
            windowWidth = widthBuf[0];
            windowHeight = heightBuf[0];

            // get resolution of monitor
            long monitor = glfwGetPrimaryMonitor();
            GLFWVidMode videoMode = glfwGetVideoMode(monitor);

            // switch to full screen
            width = videoMode.width();
            height = videoMode.height();
            glfwSetWindowMonitor(window, monitor, 0, 0, width, height, GLFW_DONT_CARE);
        } else {
            // restore last window size and position
            width = windowWidth;
            height = windowHeight;
            glfwSetWindowMonitor(window, NULL, windowPosX, windowPosY, width, height, GLFW_DONT_CARE);
        }
    }
}