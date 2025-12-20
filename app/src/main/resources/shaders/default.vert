#version 410

uniform vec4 palette[256];

in vec3 x;
in int type;

out vec4 vColor;

void main(void) {
    vColor = palette[type];
    gl_Position = vec4(x, 1.0);
}