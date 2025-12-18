package com.particle_life.app.shaders;

import org.joml.Matrix4d;

import java.io.IOException;

import static org.lwjgl.opengl.GL20C.*;

public class ParticleShader {
    public final int shaderProgram;

    private final int transformUniformLocation;
    private final int camTopLeftUniformLocation;
    private final int sizeUniformLocation;

    public final int xAttribLocation;

    private final float[] transform = new float[16];

    public ParticleShader(String vertexShaderSource,
                          String geometryShaderResource,
                          String fragmentShaderResource) throws IOException {

        shaderProgram = ShaderUtil.makeShaderProgram(vertexShaderSource, geometryShaderResource, fragmentShaderResource);

        // GET LOCATIONS
        transformUniformLocation = glGetUniformLocation(shaderProgram, "transform");
        camTopLeftUniformLocation = glGetUniformLocation(shaderProgram, "camTopLeft");
        sizeUniformLocation = glGetUniformLocation(shaderProgram, "size");

        xAttribLocation = glGetAttribLocation(shaderProgram, "x");
    }

    /**
     * Need to call this before setting uniforms.
     */
    public void use() {
        glUseProgram(shaderProgram);
    }

    public void setSize(float size) {
        glUniform1f(sizeUniformLocation, size);
    }

    public void setTransform(Matrix4d transform) {
        glUniformMatrix4fv(transformUniformLocation, false, transform.get(this.transform));
    }

    public void setCamTopLeft(float camLeft, float camTop) {
        glUniform2f(camTopLeftUniformLocation, camLeft, camTop);
    }
}
