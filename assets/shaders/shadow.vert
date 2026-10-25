#version 300 es
// The boxes seen from the sun: how far they are from it, and, for leaves and railings,
// where they have holes, so the shadows come out dappled and barred.
layout(location = 0) in vec3 aPos;
layout(location = 2) in vec3 iCenter;
layout(location = 3) in vec3 iHalf;
layout(location = 4) in vec3 iCol0;
layout(location = 5) in vec3 iCol1;
layout(location = 6) in vec3 iCol2;
layout(location = 7) in vec4 iColor;   // rgb and the material

uniform mat4 uLight;

out vec3 vLocal;
flat out vec3 vHalf;
flat out float vMaterial;
#ifdef SPHERE
flat out vec3 vCenter;
flat out vec3 vTurn0, vTurn1, vTurn2;
#endif

void main() {
    mat3 turn = mat3(iCol0, iCol1, iCol2);
#ifdef SPHERE
    vCenter = iCenter;
    vTurn0 = iCol0;
    vTurn1 = iCol1;
    vTurn2 = iCol2;
#endif
    vec3 local = aPos * iHalf;
    vLocal = local;
    vHalf = iHalf;
    vMaterial = iColor.a;
    gl_Position = uLight * vec4(iCenter + turn * local, 1.0);
}
