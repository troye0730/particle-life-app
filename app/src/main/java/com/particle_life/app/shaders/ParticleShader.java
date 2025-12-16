package com.particle_life.app.shaders;

import java.io.IOException;

import static org.lwjgl.opengl.GL20C.*;

public class ParticleShader {
    public final int shaderProgram;

    private final int sizeUniformLocation;
    private final int aspectUniformLocation;

    public final int xAttribLocation;

    private final float[] transform = new float[16];

    public ParticleShader(String vertexShaderSource,
                          String geometryShaderResource,
                          String fragmentShaderResource) throws IOException {

        shaderProgram = ShaderUtil.makeShaderProgram(vertexShaderSource, geometryShaderResource, fragmentShaderResource);

        // GET LOCATIONS
        sizeUniformLocation = glGetUniformLocation(shaderProgram, "size");
        aspectUniformLocation = glGetUniformLocation(shaderProgram, "aspect");

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

    public void setAspect(float aspect) {
        glUniform1f(aspectUniformLocation, aspect);
    }
}
