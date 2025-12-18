#version 410
#pragma optimize(on)

uniform mat4 transform;
uniform vec2 camTopLeft;
uniform float size;

layout (points) in;

/* Why are we setting max_vertices to that number?
 * Because we are drawing at most 4 particles for each input particle (the original one and its 3 periodic copies).
 * And each particle is drawn as a quad with 4 corners,
 * so we are never outputting more than 4 * 4 = 16 vertices.
 */
layout (triangle_strip, max_vertices = 16) out;

in vec4 vColor[];
out vec4 fColor;
out vec2 texCoord;

#define Pi 3.141592654

void quad(vec4 center) {
    float r = 0.5 * size;

    gl_Position = transform * (center + vec4(-r, -r, 0.0, 0.0));
    texCoord = vec2(-1.0, -1.0);
    EmitVertex();

    gl_Position = transform * (center + vec4(r, -r, 0.0, 0.0));
    texCoord = vec2(1.0, -1.0);
    EmitVertex();

    gl_Position = transform * (center + vec4(-r, r, 0.0, 0.0));
    texCoord = vec2(-1.0, 1.0);
    EmitVertex();

    gl_Position = transform * (center + vec4(r, r, 0.0, 0.0));
    texCoord = vec2(1.0, 1.0);
    EmitVertex();

    EndPrimitive();
}

void main() {
    fColor = vColor[0];
    vec4 center = gl_in[0].gl_Position;

    center -= vec4(camTopLeft, 0.0, 0.0);

    quad(center);
}