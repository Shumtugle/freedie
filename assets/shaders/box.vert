#version 300 es
// A box of the scene: the unit cube stretched, turned and placed by its instance data.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 iCenter;
layout(location = 3) in vec3 iHalf;
layout(location = 4) in vec3 iCol0;
layout(location = 5) in vec3 iCol1;
layout(location = 6) in vec3 iCol2;
layout(location = 7) in vec4 iColor;   // rgb and the material

uniform mat4 uViewProj;
uniform mat4 uLight;

out vec3 vWorld;
out vec3 vLocal;
out vec3 vNormal;
out vec3 vColor;
out vec4 vLightPos;
flat out vec3 vHalf;
flat out vec3 vFace;
flat out float vMaterial;

void main() {
    mat3 turn = mat3(iCol0, iCol1, iCol2);
    vec3 local = aPos * iHalf;
    vec3 world = iCenter + turn * local;
    vec3 normal = turn * aNormal;
    vWorld = world;
    vLocal = local;
    vNormal = normal;
    vColor = iColor.rgb;
    vMaterial = iColor.a;
    vHalf = iHalf;
    vFace = aNormal;
    // looked up a little off the face, so a face does not shadow itself
    vLightPos = uLight * vec4(world + normal * 0.04, 1.0);
    gl_Position = uViewProj * vec4(world, 1.0);
}
