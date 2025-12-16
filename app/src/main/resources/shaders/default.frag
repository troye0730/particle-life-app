#version 410

in vec4 fColor;
in vec2 texCoord; // normalized [-1, 1]
out vec4 FragColor;

void main()
{
    if (length(texCoord) > 1.0) discard; // discard fragments outside the circle radius
    FragColor = fColor;
}