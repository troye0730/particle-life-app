package com.particle_life.app;

import com.particle_life.*;
import com.particle_life.app.color.*;
import com.particle_life.app.selection.SelectionManager;
import com.particle_life.app.shaders.ParticleShader;
import com.particle_life.app.shaders.ShaderProvider;
import com.particle_life.app.utils.*;
import imgui.ImGui;
import imgui.flag.*;
import imgui.gl3.ImGuiImplGl3;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;

import org.joml.Matrix4d;
import org.joml.Vector2d;
import org.lwjgl.Version;

import java.util.Arrays;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.GL_SHADING_LANGUAGE_VERSION;
import static org.lwjgl.opengl.GL30C.*;

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
                4, 1);
    }

    private final AppSettings appSettings = new AppSettings();

    // data
    private SelectionManager<ParticleShader> shaders;
    private SelectionManager<Palette> palettes;
    private SelectionManager<MatrixGenerator> matrixGenerators;
    private SelectionManager<PositionSetter> positionSetters;

    // helper class
    private final Matrix4d transform = new Matrix4d();
    private final ParticleRenderer particleRenderer = new ParticleRenderer();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

    private ExtendedPhysics physics;
    private Loop loop;
    /**
     * The snapshot is used to store a deep copy of the physics state
     * (particles, physics settings, ...) just for this thread,
     * so that the physics simulation can continue modifying the data
     * in different threads in the meantime.
     * Otherwise, the renderer could get in trouble if it tries to
     * access the data while it is being modified by the physics simulation.
     */
    private PhysicsSnapshot physicsSnapshot;

    // local copy of snapshot:
    private PhysicsSettings settings;
    private int particleCount;
    private int preferredNumberOfThreads;

    // particle rendering: controls
    private final Vector2d camPos = new Vector2d(0.5, 0.5); // world center
    private double camSize = 1.0;

    // GUI: constants that control how the GUI behaves
    private int typeCountDiagramStepSize = 100;
    private boolean typeCountDisplayPercentage = false;

    // GUI: hide / show parts
    private final ImBoolean showGui = new ImBoolean(true);
    private final ImBoolean showGraphicsWindow = new ImBoolean(false);
    private final ImBoolean showControlsWindow = new ImBoolean(false);
    private final ImBoolean showAboutWindow = new ImBoolean(false);
    private final ImBoolean showSavesPopup = new ImBoolean(false);

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

        // Method initializes LWJGL3 renderer.
        // This method SHOULD be called after you've initialized your ImGui
        // configuration (fonts and so on).
        // ImGui context should be created as well.
        imGuiGl3.init("#version 410 core");

        particleRenderer.init();

        try {
            shaders = new SelectionManager<>(new ShaderProvider());
            palettes = new SelectionManager<>(new PalettesProvider());
            matrixGenerators = new SelectionManager<>(new MatrixGeneratorProvider());
            positionSetters = new SelectionManager<>(new PositionSetterProvider());

            positionSetters.setActivesByName(appSettings.positionSetter);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        try {
            shaders.setActivesByName(appSettings.shader);
        } catch (IllegalArgumentException e) {
            // todo: emit warning
            shaders.setActive(0);
        }

        createPhysics();
        loop = new Loop();
        loop.start(this::updatePhysics);

        // set default selection for palette
        if (palettes.hasName(appSettings.palette)) {
            palettes.setActive(palettes.getIndexByName(appSettings.palette));
        }
    }

    private void createPhysics() {
        Accelerator accelerator = (a, pos) -> {
            double beta = 0.3;
            double dist = pos.length();
            double force = dist < beta ? (dist / beta - 1) : a * (1 - Math.abs(1 + beta - 2 * dist) / (1 - beta));
            return pos.mul(force / dist);
        };
        physics = new ExtendedPhysics(accelerator);
        physicsSnapshot = new PhysicsSnapshot();
        physicsSnapshot.take(physics);
    }

    private void updatePhysics(double realDt) {
        physics.settings.dt = appSettings.autoDt ? realDt : appSettings.dt;
        physics.update();
    }

    @Override
    protected void beforeClose() {
        if (!loop.stop(1000)) {
            loop.kill();
            physics.kill();
        }
        imGuiGl3.shutdown();
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
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);

        // update particles
        loop.doOnce(() -> {
            physicsSnapshot.take(physics);
        });

        render();

        imGuiGl3.newFrame();
        // render GUI
        // Note: Any Dear ImGui code must go between ImGui.newFrame() and
        // ImGui.render().
        ImGui.newFrame();
        buildGui();
        ImGui.render();

        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    private void buildGui() {
        if (showGui.get()) {
            // MAIN MENU
            ImGui.setNextWindowSize(-1, -1, ImGuiCond.FirstUseEver);
            ImGui.setNextWindowPos(0, 0, ImGuiCond.Always);
            ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0);
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 4f, 0f);
            ImGui.pushStyleVar(ImGuiStyleVar.WindowMinSize, 0f, 0f);
            if (ImGui.begin("Particle Life Simulator",
                    ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoNavFocus | ImGuiWindowFlags.NoMove
                            | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.MenuBar)) {
                ImGui.popStyleVar(3);
                if (ImGui.beginMenuBar()) {
                    buildMainMenu();
                    ImGui.endMenuBar();
                }
            }
            ImGui.end();

            // PARTICLES
            ImGui.setNextWindowSize(-1, -1, ImGuiCond.FirstUseEver);
            ImGui.setNextWindowPos(width, 0, ImGuiCond.Always, 1.0f, 0.0f);
            ImGui.getStyle().setWindowMenuButtonPosition(ImGuiDir.Right);
            if (ImGui.begin("Particles",
                    ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoNavFocus | ImGuiWindowFlags.NoMove)) {
                ImGui.pushItemWidth(200);

                // N
                ImInt particleCountInput = new ImInt(particleCount);
                if (ImGui.inputInt("Particle count", particleCountInput, 1000, 1000, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    final int newCount = Math.max(0, particleCountInput.get());
                    loop.enqueue(() -> physics.setParticleCount(newCount));
                }

                // MATRIX
                ImGuiMatrix.draw(200 * scale, 200 * scale,
                        palettes.getActive(),
                        appSettings.matrixGuiStepSize,
                        settings.matrix,
                        (i, j, newValue) -> loop.enqueue(() -> physics.settings.matrix.set(i, j, newValue))
                );
                if (ImGui.button("Copy")) {
                    ImGui.setClipboardText(MatrixParser.matrixToString(settings.matrix));
                }
                ImGui.sameLine();
                if (ImGui.button("Paste")) {
                    Matrix parsedMatrix = MatrixParser.parseMatrix(ImGui.getClipboardText());
                    if (parsedMatrix != null) {
                        loop.enqueue(() -> {
                            physics.setMatrixSize(parsedMatrix.size());
                            physics.settings.matrix = parsedMatrix;
                        });
                    }
                }
                ImGuiUtils.helpMarker("Save / load matrix via the clipboard.");
                if (ImGui.treeNode("Settings##matrix")) {
                    ImFloat inputValue = new ImFloat((float) appSettings.matrixGuiStepSize);
                    if (ImGui.inputFloat("Step Size##Matrix", inputValue, 0.05f, 0.05f, "%.2f")) {
                        appSettings.matrixGuiStepSize = MathUtils.clamp(inputValue.get(), 0.05f, 1.0f);
                    }
                    ImGui.treePop();
                }

                ImGuiUtils.separator();

                // NTYPES
                ImInt matrixSizeInput = new ImInt(settings.matrix.size());
                if (ImGui.inputInt("Colors##input", matrixSizeInput, 1, 1, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    final int newSize = Math.max(1, Math.min(matrixSizeInput.get(), 256));
                    loop.enqueue(() -> physics.setMatrixSize(newSize));
                }

                ImGuiBarGraph.draw(200, 100,
                        palettes.getActive(),
                        typeCountDiagramStepSize,
                        physicsSnapshot.typeCount,
                        (type, newValue) -> {
                            final int[] newTypeCount = Arrays.copyOf(physicsSnapshot.typeCount, physicsSnapshot.typeCount.length);
                            newTypeCount[type] = newValue;
                            loop.enqueue(() -> physics.setTypeCount(newTypeCount));
                        },
                        typeCountDisplayPercentage
                );
                if (ImGui.button("Equalize")) {
                    loop.enqueue(() -> physics.setTypeCountEqual());
                }
                if (ImGui.treeNode("Settings##colorbars")) {
                    {
                        ImInt inputValue = new ImInt(typeCountDiagramStepSize);
                        if (ImGui.inputInt("Step Size##ColorCount", inputValue, 10)) {
                            typeCountDiagramStepSize = Math.max(0, inputValue.get());
                        }
                    }

                    {
                        ImInt selected = new ImInt(typeCountDisplayPercentage ? 1 : 0);
                        ImGui.radioButton("Absolute", selected, 0);
                        ImGui.sameLine();
                        ImGui.radioButton("Percentage", selected, 1);
                        typeCountDisplayPercentage = selected.get() == 1;
                    }
                    ImGui.treePop();
                }

                ImGui.popItemWidth();
            }
            ImGui.end();

            // PHYSICS
            ImGui.setNextWindowSize(-1, -1, ImGuiCond.FirstUseEver);
            ImGui.setNextWindowPos(width, height, ImGuiCond.Always, 1.0f, 1.0f);
            ImGui.getStyle().setWindowMenuButtonPosition(ImGuiDir.Right);
            if (ImGui.begin("Physics",
                    ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoNavFocus | ImGuiWindowFlags.NoMove)) {
                ImGui.pushItemWidth(200);

                if (ImGui.button(loop.pause ? "Play" : "Pause")) {
                    loop.pause ^= true;
                }
                ImGuiUtils.helpMarker("[SPACE] " +
                        "The physics simulation runs independently from the graphics in the background.");
                
                ImGui.sameLine();
                if (loop.getAvgFramerate() < 100000) {
                    ImGui.text(String.format("FPS: %5.0f", loop.getAvgFramerate()));
                } else {
                    ImGui.text("");
                }

                // SliderFloat Block
                ImGuiUtils.numberInput("rmax",
                        0.005f, 1f,
                        (float) settings.rmax,
                        "%.3f",
                        value -> loop.enqueue(() -> physics.settings.rmax = value));
                ImGuiUtils.helpMarker("The distance at which particles interact.");

                ImGuiUtils.numberInput("Friction Coefficient",
                        0f, 1f,
                        (float) settings.friction,
                        "%.3f",
                        value -> loop.enqueue(() -> physics.settings.friction = value),
                        false);
                ImGuiUtils.helpMarker("The velocity of all particles is multiplied with this value" +
                        " in each update step to simulate friction (assuming 60 fps).");

                ImGuiUtils.numberInput("Force Scaling",
                        0f, 100f,
                        (float) settings.force,
                        "%.1f",
                        value -> loop.enqueue(() -> physics.settings.force = value));
                ImGuiUtils.helpMarker("Scales the forces between all particles with a constant factor.");

                ImGuiUtils.separator();

                if (ImGui.checkbox("Periodic Boundaries", settings.wrap)) {
                    final boolean newWrap = !settings.wrap;
                    loop.enqueue(() -> physics.settings.wrap = newWrap);
                }
                ImGuiUtils.helpMarker("[b] Determines if the space wraps around at the borders or not.");

                if (appSettings.autoDt) ImGui.beginDisabled();
                ImGuiUtils.numberInput(
                        "Time Step",
                        0, 100,
                        (float) appSettings.dt * 1000f,
                        "%.2f ms",
                        value -> appSettings.dt = Math.max(0, value / 1000));
                if (appSettings.autoDt) ImGui.endDisabled();
                ImGui.sameLine();
                if (ImGui.checkbox("Auto", appSettings.autoDt)) appSettings.autoDt ^= true;
                ImGuiUtils.helpMarker("[ctrl+shift+scroll] The time step of the physics computation." +
                        "\nIf 'Auto' is ticked, the time step will be chosen automatically based on the real passed time.");

                ImInt threadNumberInput = new ImInt(preferredNumberOfThreads);
                if (ImGui.inputInt("Threads", threadNumberInput, 1, 1, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    final int newThreadNumber = Math.max(1, threadNumberInput.get());
                    loop.enqueue(() -> physics.preferredNumberOfThreads = newThreadNumber);
                }
                ImGuiUtils.helpMarker("The number of threads used by your processor for the physics computation." +
                        "\n(If you don't know what this means, just ignore it.)");

                ImGui.popItemWidth();
            }
            ImGui.end();
        }
    }

    private void buildMainMenu() {
        if (ImGui.beginMenu("Menu")) {

            if (ImGui.menuItem("Saves##menu", "Ctrl+s")) {
                showSavesPopup.set(true);
            }

            if (ImGui.menuItem("Controls..")) {
                showControlsWindow.set(true);
            }

            if (ImGui.menuItem("About..")) {
                showAboutWindow.set(true);
            }

            if (ImGui.menuItem("Quit", "Alt+F4, q")) {
                close();
            }

            ImGui.endMenu();
        }

        if (ImGui.beginMenu("View")) {

            if (isFullscreen()) {
                if (ImGui.menuItem("Exit Fullscreen", "F11, f")) {
                    setFullscreen(false);
                }
            } else {
                if (ImGui.menuItem("Fullscreen", "F11, f")) {
                    setFullscreen(true);
                }
            }

            if (ImGui.menuItem("Hide GUI", "Esc")) {
                showGui.set(false);
            }

            if (ImGui.beginMenu("Zoom")) {
                if (ImGui.menuItem("100%", "z")) {
                }
                if (ImGui.menuItem("Fit", "Z")) {
                }
                ImGui.endMenu();
            }

            if (ImGui.menuItem("Graphics..", "g")) {
                showGraphicsWindow.set(true);
            }

            ImGui.endMenu();
        }
    }

    private void render() {
        ParticleShader particleShader = shaders.getActive();
        particleRenderer.bufferParticleData(particleShader,
            physicsSnapshot.positions,
            physicsSnapshot.types);
        settings = physicsSnapshot.settings.deepCopy();
        particleCount = physicsSnapshot.particleCount;
        preferredNumberOfThreads = physics.preferredNumberOfThreads;

        int texWidth, texHeight;

        int desiredTexSize = (int) Math.round(Math.min(width, height) / camSize);
        if (camSize > 1) {
            texWidth = desiredTexSize;
            texHeight = desiredTexSize;
            new NormalizedDeviceCoordinates(
                    new Vector2d(0.5, 0.5), // center camera
                    new Vector2d(1, 1) // capture whole screen
            ).getMatrix(transform);
        } else {
            texWidth = width;
            texHeight = height;
            Vector2d texCamSize = new Vector2d(camSize);
            if (width > height)
                texCamSize.x *= (double) texWidth / texHeight;
            else if (height > width)
                texCamSize.y *= (double) texHeight / texWidth;
            new NormalizedDeviceCoordinates(
                    new Vector2d(texCamSize.x / 2, texCamSize.y / 2),
                    texCamSize).getMatrix(transform);
        }

        particleShader.use();

        particleShader.setPalette(getColorsFromPalette(settings.matrix.size(), palettes.getActive()));
        particleShader.setTransform(transform);

        CamOperations cam = new CamOperations(camPos, camSize, width, height);
        CamOperations.BoundingBox camBox = cam.getBoundingBox();
        if (camSize > 1) {
            particleShader.setCamTopLeft(0, 0);
        } else {
            particleShader.setCamTopLeft((float) camBox.left, (float) camBox.top);
        }

        particleShader.setSize(appSettings.particleSize * 2 * (float) settings.rmax);

        particleRenderer.drawParticles();
    }
}
