package com.particle_life.app;

public class AppSettings {

    public boolean startInFullscreen = false;
    public float particleSize = 0.045f;   // particle size (relative to rmax)
    public boolean showCursor = true;
    public double cursorSize = 0.1;
    public String cursorActionLeft = "Move";
    public String cursorActionRight = "Delete";
    public int brushPower = 100;
    public double matrixGuiStepSize = 0.2;
    public String palette = "Natural Rainbow.map";
    public String shader = "default";
    public double dt = 0.02;
    public boolean autoDt = false;
    public String positionSetter = "centered";
}
