#version 300 es
// Depth only; leaves and railings keep their holes. The same holes as in box.frag.
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

void main() {
    float mat = floor(vMaterial + 0.001);
    if (mat == FROND && frondHole(vLocal, vHalf)) discard;
    if (mat == RAIL && railHole(vLocal, vHalf)) discard;
}
