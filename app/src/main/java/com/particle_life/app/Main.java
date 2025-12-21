package com.particle_life.app;

import com.particle_life.app.color.Color;
import com.particle_life.app.color.Palette;
import com.particle_life.app.color.PalettesProvider;
import com.particle_life.Accelerator;
import com.particle_life.Physics;
import com.particle_life.app.shaders.ParticleShader;
import com.particle_life.app.utils.CamOperations;
import com.particle_life.app.utils.NormalizedDeviceCoordinates;
import org.joml.Matrix4d;
import org.joml.Vector2d;
import org.lwjgl.Version;

import java.io.IOException;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.GL_SHADING_LANGUAGE_VERSION;

public class Main extends App {

    private static String LWJGL_VERSION;
    private static String OPENGL_VENDOR;
    private static String OPENGL_RENDERER;
    private static String OPENGL_VERSION;
    private static String GLSL_VERSION;

    public static void main(String[] args) {
        Main main = new Main();
        main.launch("Particle Life Simulator",
            false,
            // request OpenGL version 4.1 (corresponds to "#version 410" in shaders)
            4, 1
        );
    }

    private final AppSettings appSettings = new AppSettings();

    // helper class
    private final Matrix4d transform = new Matrix4d();
    private final ParticleRenderer particleRenderer = new ParticleRenderer();
    private ParticleShader particleShader;

    private Physics physics;
    /**
     * The snapshot is used to store a deep copy of the physics state
     * (particles, physics settings, ...) just for this thread,
     * so that the physics simulation can continue modifying the data
     * in different threads in the meantime.
     * Otherwise, the renderer could get in trouble if it tries to
     * access the data while it is being modified by the physics simulation.
     */
    private PhysicsSnapshot physicsSnapshot;

    // particle rendering: controls
    private final Vector2d camPos = new Vector2d(0.5, 0.5); // world center
    private double camSize = 1.0;

    @Override
    protected void setup() {
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

        createPhysics();

        PalettesProvider palettes = new PalettesProvider();
        try {
            appSettings.palette = palettes.create().get(0);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    private void createPhysics() {
        Accelerator accelerator = (a, pos) -> {
            double beta = 0.3;
            double dist = pos.length();
            double force = dist < beta ? (dist / beta - 1) : a * (1 - Math.abs(1 + beta - 2 * dist) / (1 - beta));
            return pos.mul(force / dist);
        };
        physics = new Physics(accelerator);
        physicsSnapshot = new PhysicsSnapshot();
        physicsSnapshot.take(physics);
    }

    private Color[] getColorsFromPalette(int n, Palette palette) {
        Color[] colors = new Color[n];
        for (int i = 0; i < n; i++) {
            colors[i] = palette.getColor(i, n);
        }
        return colors;
    }

    @Override
    protected void draw() {
        // update particles
        physicsSnapshot.take(physics);
        physics.update();

        // clear the framebuffer
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        render();
    }

    private void render() {
        particleRenderer.bufferParticleData(particleShader, physicsSnapshot.positions, physicsSnapshot.types);

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

        particleShader.setPalette(getColorsFromPalette(physicsSnapshot.settings.matrix.size(), appSettings.palette));
        particleShader.setTransform(transform);

        CamOperations cam = new CamOperations(camPos, camSize, width, height);
        CamOperations.BoundingBox camBox = cam.getBoundingBox();
        if (camSize > 1) {
            particleShader.setCamTopLeft(0, 0);
        } else {
            particleShader.setCamTopLeft((float) camBox.left, (float) camBox.top);
        }

        particleShader.setSize(appSettings.particleSize);

        particleRenderer.drawParticles();
    }
}
