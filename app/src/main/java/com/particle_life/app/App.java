package com.particle_life.app;

import com.particle_life.app.shaders.*;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import java.io.IOException;
import java.util.Random;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.GL_SHADING_LANGUAGE_VERSION;
import static org.lwjgl.system.MemoryUtil.NULL;

public class App {

    private static String LWJGL_VERSION;
    private static String OPENGL_VENDOR;
    private static String OPENGL_RENDERER;
    private static String OPENGL_VERSION;
    private static String GLSL_VERSION;

    public static void main(String[] args) {
        App app = new App();
        app.launch("Particle Life Simulator",
            false,
            // request OpenGL version 4.1 (corresponds to "#version 410" in shaders)
            4, 1
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

    // data
    private int particleNums = 1;
    private float particleSize = 0.15f;
    private double[] positions = new double[particleNums * 3];
    private static final Random random = new Random();

    // helper class
    private final ParticleRenderer particleRenderer = new ParticleRenderer();
    private ParticleShader particleShader;

    private void launch(String title, boolean fullscreen,
                        int glContextVersionMajor, int glContextVersionMinor) {
        init(title, fullscreen, glContextVersionMajor, glContextVersionMinor);

        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities();

        setCallbacks();

        setup();

        while (!glfwWindowShouldClose(window)) {
            // use this to wait for events instead of polling, saving CPU
            glfwPollEvents();

            draw();

            glfwSwapBuffers(window); // swap the color buffers
        }

        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW when done
        glfwTerminate();
    }

    private void init(String title, boolean fullscreen,
                      int glContextVersionMajor, int glContextVersionMinor) {
        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable

        // request OpenGL version
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, glContextVersionMajor);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, glContextVersionMinor);

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
        windowPosX = (int) (f * monitorWidth / 2);
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

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);

        // Enable v-sync
        glfwSwapInterval(1);

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

        glfwSetFramebufferSizeCallback(window, (w, fbw, fbh) -> {
            width = fbw;
            height = fbh;
            glViewport(0, 0, fbw, fbh);
        });
    }

    private void setup() {
        LWJGL_VERSION = Version.getVersion();
        OPENGL_VENDOR = glGetString(GL_VENDOR);
        OPENGL_RENDERER = glGetString(GL_RENDERER);
        OPENGL_VERSION = glGetString(GL_VERSION);
        GLSL_VERSION = glGetString(GL_SHADING_LANGUAGE_VERSION);

        System.out.println("LWJGL Version: " + LWJGL_VERSION);
        System.out.println("OpenGL Vendor: " + OPENGL_VENDOR);
        System.out.println("OpenGL Renderer: " + OPENGL_RENDERER);
        System.out.println("OpenGL Version: " + OPENGL_VERSION);
        System.out.println("GLSL Version: " + GLSL_VERSION);

        particleRenderer.init();

        try {
            particleShader = new ParticleShader("src/main/resources/shaders/default.vert",
                                    "src/main/resources/shaders/default.geom",
                                    "src/main/resources/shaders/default.frag");
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        float scale = 0.3f;
        for (int i = 0; i < particleNums; i++) {
            final int i3 = i * 3;
            positions[i3] = 0.0;
            positions[i3 + 1] = 0.0;
            positions[i3 + 2] = 0.0;
        }
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

    private void draw() {
        // clear the framebuffer
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        render();
    }

    private void render() {
        particleRenderer.bufferParticleData(particleShader, positions);
        particleShader.use();
        particleShader.setSize(particleSize);
        particleShader.setAspect((float) width / (float) height);
        particleRenderer.drawParticles();
    }
}