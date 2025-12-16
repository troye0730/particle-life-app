#version 410

in vec3 x;

out vec4 vColor;

void main(void) {
    vColor = vec4(0.0, 0.65, 1.0, 1.0);
    gl_Position = vec4(x, 1.0);
}