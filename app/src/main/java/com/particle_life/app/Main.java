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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.GL_CLAMP_TO_BORDER;
import static org.lwjgl.opengl.GL13C.GL_MULTISAMPLE;
import static org.lwjgl.opengl.GL20C.GL_SHADING_LANGUAGE_VERSION;
import static org.lwjgl.opengl.GL30C.*;

public class Main extends App {

    private static final String JAVA_HOME = System.getProperty("java.home");
    private static final String JVM_VERSION = System.getProperty("java.vm.version");
    private static String LWJGL_VERSION;
    private static String OPENGL_VENDOR;
    private static String OPENGL_RENDERER;
    private static String OPENGL_VERSION;
    private static String GLSL_VERSION;

    public static void main(String[] args) {
        System.out.println("Java Home: " + JAVA_HOME);
        System.out.println("JVM Version: " + JVM_VERSION);

        Main main = new Main();
        main.launch("Particle Life Simulator",
                main.appSettings.startInFullscreen,
                // request OpenGL version 4.1 (corresponds to "#version 410" in shaders)
                4, 1);
    }

    private final AppSettings appSettings = new AppSettings();

    // data
    private final Clock renderClock = new Clock(60);
    private SelectionManager<ParticleShader> shaders;
    private SelectionManager<Palette> palettes;
    private SelectionManager<MatrixGenerator> matrixGenerators;
    private SelectionManager<PositionSetter> positionSetters;
    private SelectionManager<TypeSetter> typeSetters;

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
    private LoadDistributor physicsSnapshotLoadDistributor;  // speed up taking snapshots with parallelization
    public AtomicBoolean newSnapshotAvailable = new AtomicBoolean(false);

    // local copy of snapshot:
    private PhysicsSettings settings;
    private int particleCount;
    private int preferredNumberOfThreads;

    // particle rendering: controls
    private boolean traces = false;
    private final Vector2d camPos = new Vector2d(0.5, 0.5); // world center
    private double camSize = 1.0;
    boolean draggingShift = false;
    boolean leftDraggingParticles = false;  // dragging with left mouse button
    boolean rightDraggingParticles = false;  // dragging with right mouse button
    boolean leftPressed = false;
    boolean rightPressed = false;
    boolean upPressed = false;
    boolean downPressed = false;
    boolean wPressed = false;
    boolean aPressed = false;
    boolean sPressed = false;
    boolean dPressed = false;
    boolean leftShiftPressed = false;
    boolean rightShiftPressed = false;
    boolean leftControlPressed = false;
    boolean rightControlPressed = false;
    boolean leftAltPressed = false;
    boolean rightAltPressed = false;

    // GUI: constants that control how the GUI behaves
    private int typeCountDiagramStepSize = 100;
    private boolean typeCountDisplayPercentage = false;

    // GUI: hide / show parts
    private final ImBoolean showGui = new ImBoolean(true);
    private final ImBoolean showGraphicsWindow = new ImBoolean(false);
    private final ImBoolean showControlsWindow = new ImBoolean(false);
    private final ImBoolean showAboutWindow = new ImBoolean(false);
    private final ImBoolean showSavesPopup = new ImBoolean(false);

    // offscreen rendering buffers
    private MultisampledFramebuffer worldTexture;  // particles

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

        glEnable(GL_MULTISAMPLE);

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
            typeSetters = new SelectionManager<>(new TypeSetterProvider());

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

        // generate offscreen frame buffer to render particles to a multisampled texture
        // and also a simple texture for converting the multisampled texture to a single-sampled texture
        // (this is necessary because ImGui can't handle multisampled textures in the drawlist)
        worldTexture = new MultisampledFramebuffer();
        worldTexture.init();
        glBindTexture(GL_TEXTURE_2D, worldTexture.textureSingle);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void createPhysics() {
        Accelerator accelerator = (a, pos) -> {
            double beta = 0.3;
            double dist = pos.length();
            double force = dist < beta ? (dist / beta - 1) : a * (1 - Math.abs(1 + beta - 2 * dist) / (1 - beta));
            return pos.mul(force / dist);
        };
        physics = new ExtendedPhysics(
                accelerator,
                positionSetters.getActive(),
                matrixGenerators.getActive(),
                typeSetters.getActive());
        physicsSnapshot = new PhysicsSnapshot();
        physicsSnapshotLoadDistributor = new LoadDistributor();
        physicsSnapshot.take(physics, physicsSnapshotLoadDistributor);
        newSnapshotAvailable.set(true);
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
            physicsSnapshotLoadDistributor.kill();
        }
        imGuiGl3.shutdown();
    }

    @Override
    protected void draw() {
        renderClock.tick();
        updateCanvas();

        int texWidth, texHeight;

        // todo: make this part look less like magic
        int desiredTexSize = (int) Math.round(Math.min(width, height) / camSize);
        if (camSize > 1) {
            texWidth = desiredTexSize;
            texHeight = desiredTexSize;
            new NormalizedDeviceCoordinates(
                    new Vector2d(0.5, 0.5), // center camera
                    new Vector2d(1, 1) // capture whole screen
            ).getMatrix(transform);
        } else {
            if (settings.wrap) {
                texWidth = Math.min(desiredTexSize, width);
                texHeight = Math.min(desiredTexSize, height);
            } else {
                texWidth = width;
                texHeight = height;
            }
            Vector2d texCamSize = new Vector2d(camSize);
            if (width > height) texCamSize.x *= (double) texWidth / texHeight;
            else if (height > width) texCamSize.y *= (double) texHeight / texWidth;
            new NormalizedDeviceCoordinates(
                    new Vector2d(texCamSize.x / 2, texCamSize.y / 2),
                    texCamSize
            ).getMatrix(transform);
        }

        worldTexture.ensureSize(texWidth, texHeight, 16);

        ParticleShader particleShader = shaders.getActive();
        
        // set shader variables
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
        particleShader.setWrap(settings.wrap);
        particleShader.setSize(appSettings.particleSize * 2 * (float) settings.rmax);

        if (!traces) worldTexture.clear(0, 0, 0, 0);

        glEnable(GL_BLEND);
        particleShader.blendMode.glBlendFunc();

        glDisable(GL_SCISSOR_TEST);
        glViewport(0, 0, texWidth, texHeight);

        glBindFramebuffer(GL_FRAMEBUFFER, worldTexture.framebufferMulti);
        particleRenderer.drawParticles();
        worldTexture.toSingleSampled();

        glBindTexture(GL_TEXTURE_2D, worldTexture.textureSingle);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, settings.wrap ? GL_REPEAT : GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, settings.wrap ? GL_REPEAT : GL_CLAMP_TO_BORDER);
        glBindTexture(GL_TEXTURE_2D, 0);

        // render GUI
        // Note: Any Dear ImGui code must go between ImGui.newFrame() and ImGui.render().
        imGuiGl3.newFrame();
        ImGui.newFrame();
        if (camSize > 1) {
            ImGui.getBackgroundDrawList().addImage(worldTexture.textureSingle, 0, 0, width, height,
                    (float) camBox.left, (float) camBox.top,
                    (float) camBox.right, (float) camBox.bottom);
        } else {
            ImGui.getBackgroundDrawList().addImage(worldTexture.textureSingle, 0, 0, width, height,
                    0, 0, (float) width / texWidth, (float) height / texHeight);
        }

        buildGui();
        ImGui.render();

        glDisable(GL_SCISSOR_TEST);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    /**
     * Render particles, cursor etc., i.e. everything except the GUI elements.
     */
    private void updateCanvas() {
        if (newSnapshotAvailable.get()) {

            // get local copy of snapshot

            particleRenderer.bufferParticleData(shaders.getActive(),
                    physicsSnapshot.positions,
                    physicsSnapshot.types);
            settings = physicsSnapshot.settings.deepCopy();
            particleCount = physicsSnapshot.particleCount;
            preferredNumberOfThreads = physics.preferredNumberOfThreads;

            newSnapshotAvailable.set(false);
        }

        loop.doOnce(() -> {
            physicsSnapshot.take(physics, physicsSnapshotLoadDistributor);
            newSnapshotAvailable.set(true);
        });
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

                // POSITION SETTERS
                if (ImGuiUtils.renderCombo("##positions", positionSetters)) {
                    final PositionSetter nextPositionSetter = positionSetters.getActive();
                    loop.enqueue(() -> physics.positionSetter = nextPositionSetter);
                }
                ImGui.sameLine();
                if (ImGui.button("Positions")) {
                    loop.enqueue(physics::setPositions);
                }
                ImGuiUtils.helpMarker("[p]");

                ImGuiUtils.separator();

                // MATRIX GENERATORS
                if (ImGuiUtils.renderCombo("##matrix", matrixGenerators)) {
                    final MatrixGenerator nextMatrixGenerator = matrixGenerators.getActive();
                    loop.enqueue(() -> physics.matrixGenerator = nextMatrixGenerator);
                }
                ImGui.sameLine();
                if (ImGui.button("Matrix")) {
                    loop.enqueue(physics::generateMatrix);
                }
                ImGuiUtils.helpMarker("[m]");

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

                // TYPE SETTERS
                ImGuiUtils.renderCombo("##colors", typeSetters);
                ImGui.sameLine();
                if (ImGui.button("Colors")) {
                    loop.enqueue(() -> {
                        TypeSetter previousTypeSetter = physics.typeSetter;
                        physics.typeSetter = typeSetters.getActive();
                        physics.setTypes();
                        physics.typeSetter = previousTypeSetter;
                    });
                }
                ImGuiUtils.helpMarker("[c] Use this to set colors of particles without changing their position.");

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

        // GRAPHICS
        if (showGraphicsWindow.get()) {
            ImGui.setNextWindowSize(400, 300);
            ImGui.setNextWindowPos(width / 2f, height / 2f, ImGuiCond.FirstUseEver, 0.5f, 0.5f);
            if (ImGui.begin("Graphics", showGraphicsWindow,
                    ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoNavFocus | ImGuiWindowFlags.NoCollapse)) {
                ImGui.pushItemWidth(200);
                ImGui.text(String.format("Graphics FPS: %.0f", renderClock.getAvgFramerate()));

                // SHADERS
                ImGuiUtils.renderCombo("Shader", shaders);
                ImGuiUtils.helpMarker("Use this to set how the particles are displayed");

                // PALETTES
                ImGuiUtils.renderCombo("Palette", palettes);
                ImGuiUtils.helpMarker("Color of particles");

                ImGui.popItemWidth();
            }
            ImGui.end();
        }

        if (showControlsWindow.get()) {
            ImGui.setNextWindowPos(width / 2f, height / 2f, ImGuiCond.FirstUseEver, 0.5f, 0.5f);
            if (ImGui.begin("Controls", showControlsWindow, ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize)) {
                ImGui.text("""
                        [ESCAPE]: hide / show GUI GUI
                        [g]: show / hide graphics settings
                        [SPACE]: pause physics
                        [p]: set positions
                        [c]: set colors
                        [m]: set matrix
                        [b]: toggle boundaries (clamped / periodic)
                        [t]: toggle traces
                        [F11], [f]: toggle full screen
                        [ALT]+[F4], [q]: quit
                        """);
            }
            ImGui.end();
        }

        if (showAboutWindow.get()) {
            ImGui.setNextWindowPos(width / 2f, height / 2f, ImGuiCond.FirstUseEver, 0.5f, 0.5f);
            if (ImGui.begin("About", showAboutWindow, ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoCollapse)) {
                ImGui.text("By Tom Mohr.");
                ImGui.text("GPL-3.0 License.");
                ImGui.dummy(0, 10);
                if (ImGuiUtils.link("particle-life.com", "https://particle-life.com")) {
                    setFullscreen(false);
                }
                ImGui.dummy(0, 10);
                ImGui.text("Java Home: " + JAVA_HOME);
                ImGui.text("JVM Version: " + JVM_VERSION);
                ImGui.text("LWJGL Version: " + LWJGL_VERSION);
                ImGui.text("OpenGL Vendor: " + OPENGL_VENDOR);
                ImGui.text("OpenGL Renderer: " + OPENGL_RENDERER);
                ImGui.text("OpenGL Version: " + OPENGL_VERSION);
                ImGui.text("GLSL Version: " + GLSL_VERSION);
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

    private Color[] getColorsFromPalette(int n, Palette palette) {
        Color[] colors = new Color[n];
        for (int i = 0; i < n; i++) {
            colors[i] = palette.getColor(i, n);
        }
        return colors;
    }

    @Override
    protected void onKeyPressed(String keyName) {
        // update key states
        switch (keyName) {
            case "LEFT" -> leftPressed = true;
            case "RIGHT" -> rightPressed = true;
            case "UP" -> upPressed = true;
            case "DOWN" -> downPressed = true;
            case "w" -> wPressed = true;
            case "a" -> aPressed = true;
            case "s" -> sPressed = true;
            case "d" -> dPressed = true;
            case "LEFT_SHIFT" -> leftShiftPressed = true;
            case "RIGHT_SHIFT" -> rightShiftPressed = true;
            case "LEFT_CONTROL" -> leftControlPressed = true;
            case "RIGHT_CONTROL" -> rightControlPressed = true;
            case "LEFT_ALT" -> leftAltPressed = true;
            case "RIGHT_ALT" -> rightAltPressed = true;
        }

        // ctrl + key shortcuts
        if (leftControlPressed | rightControlPressed) {
            switch (keyName) {
                case "s" -> {
                    showSavesPopup.set(true);

                    // Clear key states manually, because releasing [ctrl]+[s]
                    // won't be captured once the popup is open.
                    leftControlPressed = false;
                    rightControlPressed = false;
                    sPressed = false;
                }
            }
            return;
        }

        // simple key shortcuts
        switch (keyName) {
            case "ESCAPE" -> showGui.set(!showGui.get());
            case "f" -> setFullscreen(!isFullscreen());
            case "t" -> traces ^= true;
            case "p" -> loop.enqueue(physics::setPositions);
            case "c" -> loop.enqueue(() -> {
                TypeSetter previousTypeSetter = physics.typeSetter;
                physics.typeSetter = typeSetters.getActive();
                physics.setTypes();
                physics.typeSetter = previousTypeSetter;
            });
            case "g" -> showGraphicsWindow.set(!showGraphicsWindow.get());
            case "m" -> loop.enqueue(physics::generateMatrix);
            case "b" -> loop.enqueue(() -> physics.settings.wrap ^= true);
            case " " -> loop.pause ^= true;
            case "q" -> close();
        }
    }

    @Override
    protected void onKeyReleased(String keyName) {
        // update key states
        switch (keyName) {
            case "LEFT" -> leftPressed = false;
            case "RIGHT" -> rightPressed = false;
            case "UP" -> upPressed = false;
            case "DOWN" -> downPressed = false;
            case "w" -> wPressed = false;
            case "a" -> aPressed = false;
            case "s" -> sPressed = false;
            case "d" -> dPressed = false;
            case "LEFT_SHIFT" -> leftShiftPressed = false;
            case "RIGHT_SHIFT" -> rightShiftPressed = false;
            case "LEFT_CONTROL" -> leftControlPressed = false;
            case "RIGHT_CONTROL" -> rightControlPressed = false;
            case "LEFT_ALT" -> leftAltPressed = false;
            case "RIGHT_ALT" -> rightAltPressed = false;
        }
    }

    @Override
    protected void onMousePressed(int button) {
        if (button == 2) {  // middle mouse button
            draggingShift = true;
        } else if (button == 0) {  // left mouse button
            leftDraggingParticles = true;
        } else if (button == 1) {  // right mouse button
            rightDraggingParticles = true;
        }
    }

    @Override
    protected void onMouseReleased(int button) {
        if (button == 2) {  // middle mouse button
            draggingShift = false;
        } else if (button == 0) {  // left mouse button
            leftDraggingParticles = false;
        } else if (button == 1) {  // right mouse button
            rightDraggingParticles = false;
        }
    }

    @Override
    protected void setFullscreen(boolean fullscreen) {
        super.setFullscreen(fullscreen);

        // remember fullscreen state for next startup
        appSettings.startInFullscreen = fullscreen;
    }
}
