package com.particle_life.app;

import com.particle_life.app.shaders.ParticleShader;

import static org.lwjgl.opengl.GL11.GL_DOUBLE;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

class ParticleRenderer {
    private int vao;
    private int vboX;

    /**
     * Remember the last buffered size in order to use subBufferData instead of bufferData whenever possible.
     */
    private int lastBufferedSize = -1;
    private int lastShaderProgram = -1;

    void init() {
        vao = glGenVertexArrays();
        vboX = glGenBuffers();
    }

    void bufferParticleData(ParticleShader particleShader, double[] x) {
        glBindVertexArray(vao);

        // detect change
        boolean shaderChanged = particleShader.shaderProgram != lastShaderProgram;
        boolean bufferSizeChanged = x.length != lastBufferedSize * 3;
        lastBufferedSize = x.length / 3;
        lastShaderProgram = particleShader.shaderProgram;

        if (shaderChanged) {
            // if the shader changed, we have to rebind the attribute locations

            if (particleShader.xAttribLocation != -1) {
                glBindBuffer(GL_ARRAY_BUFFER, vboX);
                glVertexAttribPointer(particleShader.xAttribLocation, 3, GL_DOUBLE, false, 0, 0);
                glEnableVertexAttribArray(particleShader.xAttribLocation);
            }
        }

        final int usage = GL_DYNAMIC_DRAW;

        if (particleShader.xAttribLocation != -1) {
            glBindBuffer(GL_ARRAY_BUFFER, vboX);

            if (bufferSizeChanged) {
                glBufferData(GL_ARRAY_BUFFER, x, usage);
            } else {
                glBufferSubData(GL_ARRAY_BUFFER, 0, x);
            }
        }
    }

    void drawParticles() {
        if (lastBufferedSize <= 0) return;
        glBindVertexArray(vao);
        glDrawArrays(GL_POINTS, 0, lastBufferedSize);
    }
}
