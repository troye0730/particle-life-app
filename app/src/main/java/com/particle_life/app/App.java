package com.particle_life.app;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class App {
    public static void main(String[] args) {
        App app = new App();
        app.launch("Particle Life Simulator");
    }

    // The window handle
    private long window;

    private void launch(String title) {
        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Create the window
        window = glfwCreateWindow(800, 600, title, NULL, NULL);

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
}