#version 300 es
// The boxes seen from the sun: only how far they are from it.
layout(location = 0) in vec3 aPos;
layout(location = 2) in vec3 iCenter;
layout(location = 3) in vec3 iHalf;
layout(location = 4) in vec3 iCol0;
layout(location = 5) in vec3 iCol1;
layout(location = 6) in vec3 iCol2;

uniform mat4 uLight;

void main() {
    mat3 turn = mat3(iCol0, iCol1, iCol2);
    gl_Position = uLight * vec4(iCenter + turn * (aPos * iHalf), 1.0);
}
