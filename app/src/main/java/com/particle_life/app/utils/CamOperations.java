package com.particle_life.app.utils;

import org.joml.Vector2d;

/**
 * A utility class for camera operations like dragging and zooming.
 * The camera position and size are defined in world coordinates.
 * The utility methods {@link #dragCam(Vector2d, Vector2d)} and {@link #zoom(double, double, double)}
 * actually modify the camera position and camera size vectors passed to the constructor of this class.
 */
public class CamOperations {

    public final Vector2d camPos;
    public double camSize;
    public double screenWidth;
    public double screenHeight;

    public static class BoundingBox {
        public double left;
        public double top;
        public double right;
        public double bottom;
    }

    public CamOperations(Vector2d camPos, double camSize, double screenWidth, double screenHeight) {
        this.camPos = camPos;
        this.camSize = camSize;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /**
     * Compute the camera dimensions matching the aspect ratio
     * dictated by this object's {@code screenWidth} and {@code screenHeight}.
     * The smaller component of the returned vector is {@link #camSize},
     * the other component is scaled up to match the aspect ratio of the window.
     */
    public Vector2d getCamDimensions() {
        return getCamDimensions(camSize);
    }

    /**
     * Compute the camera dimensions matching the aspect ratio
     * dictated by this object's {@code screenWidth} and {@code screenHeight}.
     * The smaller component of the returned vector is {@code camSize},
     * the other component is scaled up to match the aspect ratio of the window.
     * The original {@code camSize} can therefore always be restored via
     * {@code camDimensions.minComponent()}.
     */
    public Vector2d getCamDimensions(double camSize) {
        return getCamDimensions(camSize, screenWidth, screenHeight);
    }

    /**
     * Compute the camera dimensions matching the aspect ratio
     * dictated by the given {@code screenWidth} and {@code screenHeight}.
     * The smaller component of the returned vector is {@code camSize},
     * the other component is scaled up to match the aspect ratio of the window.
     * The original {@code camSize} can therefore always be restored via
     * {@code camDimensions.minComponent()}.
     */
    public static Vector2d getCamDimensions(double camSize, double screenWidth, double screenHeight) {
        Vector2d c = new Vector2d(camSize);
        if (screenWidth > screenHeight) {
            c.x *= screenWidth / screenHeight;
        } else if (screenHeight > screenWidth) {
            c.y *= screenHeight / screenWidth;
        }
        return c;
    }

    public BoundingBox getBoundingBox() {
        BoundingBox bb = new BoundingBox();
        Vector2d halfDim = getCamDimensions().div(2);
        bb.left = camPos.x - halfDim.x;
        bb.right = camPos.x + halfDim.x;
        bb.top = camPos.y - halfDim.y;
        bb.bottom = camPos.y + halfDim.y;
        return bb;
    }
}
