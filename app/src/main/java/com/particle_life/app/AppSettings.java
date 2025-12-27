package com.particle_life.app;

import com.particle_life.app.color.Palette;

public class AppSettings {

    public float particleSize = 0.045f;   // particle size (relative to rmax)
    public double matrixGuiStepSize = 0.2;
    public Palette palette;
    public String shader = "default";
    public double dt = 0.02;
    public boolean autoDt = false;
}
