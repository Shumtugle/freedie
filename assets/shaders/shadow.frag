#version 300 es
// Depth only; leaves and railings keep their holes. The same holes as in box.frag.
// A ball is the sphere inside its cube, as there; the mirror is no ball here.
precision highp float;

in vec3 vLocal;
flat in vec3 vHalf;
flat in float vMaterial;

const float FROND = 9.0, RAIL = 15.0;

// a frond: leaflets slanting from a spine toward the tip, gaps between them
// (each half of a frond is a box of its own, its spine along the box's -z edge)
bool frondHole(vec3 l, vec3 hf) {
    float across = (l.z / hf.z + 1.0) * 0.5;
    if (across < 0.1) return false;
    if (across > 0.96) return true;
    return fract((l.x - (l.z + hf.z) * 0.8) / 0.12) > 0.55;
}

// a railing: a rail along the top and the bottom, bars between
bool railHole(vec3 l, vec3 hf) {
    float v = l.y + hf.y;
    if (v < 0.05 || v > 2.0 * hf.y - 0.06) return false;
    float a = hf.x > hf.z ? l.x : l.z;
    return fract(a / 0.13) > 0.3;
}

#ifdef SPHERE
flat in vec3 vCenter;
flat in vec3 vTurn0, vTurn1, vTurn2;
uniform mat4 uLight;
uniform vec3 uSun;
#endif

void main() {
#ifdef SPHERE
    // the mirror up in the sky casts nothing on the street
    if (floor(vMaterial + 0.001) == 24.0) discard;
    // nor does clear plastic: what is in it does
    if (floor(vMaterial + 0.001) == 27.0) discard;
    // a ball: the sun's ray through this point of the cube, followed to the sphere inside
    // (drawn out longer one way, as a limb: squeezed to a unit sphere first)
    mat3 turn = mat3(vTurn0, vTurn1, vTurn2);
    vec3 hs = vHalf;
    vec3 ro = vLocal / hs;
    vec3 rd = normalize(transpose(turn) * -uSun / hs);
    float b = dot(ro, rd);
    float h = b * b - dot(ro, ro) + 1.0;
    if (h < 0.0) discard;
    vec3 world = vCenter + turn * ((ro + rd * (-b - sqrt(h))) * hs);
    vec4 p = uLight * vec4(world, 1.0);
    gl_FragDepth = p.z / p.w * 0.5 + 0.5;
    return;
#endif
    float mat = floor(vMaterial + 0.001);
    if (mat == FROND && frondHole(vLocal, vHalf)) discard;
    if (mat == RAIL && railHole(vLocal, vHalf)) discard;
}
