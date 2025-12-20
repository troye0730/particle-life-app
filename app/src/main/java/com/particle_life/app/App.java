package com.particle_life.app;

import com.particle_life.*;
import com.particle_life.app.color.Color;
import com.particle_life.app.color.Palette;
import com.particle_life.app.color.PalettesProvider;
import com.particle_life.app.shaders.*;
import com.particle_life.app.utils.*;
import org.joml.Matrix4d;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import java.io.IOException;

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
    private int particleNums = 1000;
    private float particleSize = 0.015f;
    private Particle[] particles = new Particle[particleNums];
    private double[] positions = new double[particleNums * 3];
    private int[] types = new int[particleNums];
    private double friction = 0.85;
    /**
     * Time that is assumed to have passed between each simulation step, in seconds.
     */
    private double dt = 0.02;
    private double rmax = 0.2;
    private double force = 1.0;
    private Matrix matrix;
    private PositionSetter positionSetter = new DefaultPositionSetter();
    private TypeSetter typeSetter = new DefaultTypeSetter();
    private MatrixGenerator matrixGenerator = new DefaultMatrixGenerator();
    private Accelerator accelerator;
    private PalettesProvider palettes;
    private Palette palette;
    private boolean wrap = true;

    // helper class
    private final Matrix4d transform = new Matrix4d();
    private final ParticleRenderer particleRenderer = new ParticleRenderer();
    private ParticleShader particleShader;

    // particle rendering: controls
    private final Vector2d camPos = new Vector2d(0.5, 0.5); // world center
    private double camSize = 1.0;

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

            update();
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
        glfwSetWindowSizeCallback(window, (window1, newWidth, newHeight) -> {
            width = newWidth;
            height = newHeight;
        });

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

        matrix = matrixGenerator.makeMatrix(6);

        accelerator = (a, pos) -> {
            double beta = 0.3;
            double dist = pos.length();
            double force = dist < beta ? (dist / beta - 1) : a * (1 - Math.abs(1 + beta - 2 * dist) / (1 - beta));
            return pos.mul(force / dist);
        };

        for (int i = 0; i < particleNums; i++) {
            particles[i] = new Particle();
            Particle p = particles[i];
            positionSetter.set(p.position, p.type, matrix.size());
            ensurePosition(p.position);
            p.type = typeSetter.getType(new Vector3d(p.position), new Vector3d(p.velocity), p.type, matrix.size());

            final int i3 = 3 * i;

            positions[i3] = p.position.x;
            positions[i3 + 1] = p.position.y;
            positions[i3 + 2] = p.position.z;

            types[i] = p.type;
        }

        palettes = new PalettesProvider();
        try {
            palette = palettes.create().get(0);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    private void update() {
        for (int i = 0; i < particleNums; i++) {
            updateVelocity(i);
            updatePosition(i);
        }
    }

    private void updateVelocity(int i) {
        Particle p = particles[i];

        // apply friction before adding new velocity
        double frictionFactor = Math.pow(friction, 60 * dt);
        p.velocity.mul(frictionFactor);

        for (int j = 0; j < particleNums; j++) {
            if (j == i) continue;
            Particle q = particles[j];

            Vector3d relativePosition = connection(p.position, q.position);

            double distanceSquared = relativePosition.lengthSquared();
            if (distanceSquared != 0 && distanceSquared <= rmax * rmax) {

                relativePosition.div(rmax);
                Vector3d deltaV = accelerator.accelerate(matrix.get(p.type, q.type), relativePosition);
                // apply force as acceleration
                p.velocity.add(deltaV.mul(rmax * force * dt));
            }
        }
    }

    private void updatePosition(int i) {
        Particle p = particles[i];

        // pos += vel * dt;
        p.velocity.mulAdd(dt, p.position, p.position);

        ensurePosition(p.position);

        final int i3 = 3 * i;

        positions[i3] = p.position.x;
        positions[i3 + 1] = p.position.y;
        positions[i3 + 2] = p.position.z;

    }

    private Vector3d connection(Vector3d pos1, Vector3d pos2) {
        Vector3d delta = new Vector3d(pos2).sub(pos1);
        if (wrap) {
            // wrapping the connection gives us the shortest possible distance
            Range.wrapConnection(delta);
        }
        return delta;
    }

    public void ensurePosition(Vector3d position) {
        if (wrap) {
            Range.wrap(position);
        } else {
            Range.clamp(position);
        }
    }

    private Color[] getColorsFromPalette(int n, Palette palette) {
        Color[] colors = new Color[n];
        for (int i = 0; i < n; i++) {
            colors[i] = palette.getColor(i, n);
        }
        return colors;
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
        particleRenderer.bufferParticleData(particleShader, positions, types);

        int texWidth, texHeight;

        int desiredTexSize = (int) Math.round(Math.min(width, height) / camSize);
        if (camSize > 1) {
            texWidth = desiredTexSize;
            texHeight = desiredTexSize;
            new NormalizedDeviceCoordinates(
                    new Vector2d(0.5, 0.5),  // center camera
                    new Vector2d(1, 1)  // capture whole screen
            ).getMatrix(transform);
        } else {
            texWidth = width;
            texHeight = height;
            Vector2d texCamSize = new Vector2d(camSize);
            if (width > height) texCamSize.x *= (double) texWidth / texHeight;
            else if (height > width) texCamSize.y *= (double) texHeight / texWidth;
            new NormalizedDeviceCoordinates(
                    new Vector2d(texCamSize.x / 2, texCamSize.y / 2),
                    texCamSize
            ).getMatrix(transform);
        }

        particleShader.use();

        particleShader.setPalette(getColorsFromPalette(matrix.size(), palette));
        particleShader.setTransform(transform);

        CamOperations cam = new CamOperations(camPos, camSize, width, height);
        CamOperations.BoundingBox camBox = cam.getBoundingBox();
        if (camSize > 1) {
            particleShader.setCamTopLeft(0, 0);
        } else {
            particleShader.setCamTopLeft((float) camBox.left, (float) camBox.top);
        }

        particleShader.setSize(particleSize);

        particleRenderer.drawParticles();
    }
}