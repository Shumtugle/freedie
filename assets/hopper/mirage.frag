#version 300 es
precision highp float;
#include common
// A mirage: an upright sheet of quicksilver on an island, always turned to the player. It shows
// the diamond hammer and next to nothing else, through a shimmer, the way hot air shows what is
// not there.
in vec3 vN;
in vec3 vW;
in vec2 vTag;   // x: across the sheet, -1 to 1; y: up it, 0 to 1
out vec4 o;
uniform vec3 uEye;
uniform vec4 uHammer; // where the hammer stands (its foot), and the way it faces as an angle

// a box in the hammer's own frame, met by a ray: the distance, or -1; and the face's normal
float box(vec3 ro, vec3 rd, vec3 c, vec3 h, out vec3 nrm) {
    vec3 inv = 1.0 / rd;
    vec3 t0 = (c - h - ro) * inv, t1 = (c + h - ro) * inv;
    vec3 tn = min(t0, t1), tf = max(t0, t1);
    float a = max(max(tn.x, tn.y), tn.z), b = min(min(tf.x, tf.y), tf.z);
    if (a > b || b < 0.0) return -1.0;
    nrm = tn.x >= tn.y && tn.x >= tn.z ? vec3(-sign(rd.x), 0.0, 0.0)
        : tn.y >= tn.z ? vec3(0.0, -sign(rd.y), 0.0) : vec3(0.0, 0.0, -sign(rd.z));
    return a;
}
uniform float uTime;

void main() {
    vec3 n = normalize(vN);
    vec3 side = vec3(-n.z, 0.0, n.x);
    // the face wavers in slow crossing ripples, and what it shows wavers with it
    // thin bands sliding up, as over hot ground, on a slower sway
    float w1 = sin(vTag.y * 34.0 - uTime * 4.0 + sin(vTag.x * 2.0 + uTime * 0.7) * 1.2);
    float w2 = sin(vTag.y * 7.0 + vTag.x * 3.0 - uTime * 1.3);
    vec3 m = normalize(n + side * (0.009 * w1 + 0.012 * w2) + vec3(0.0, 0.008, 0.0) * w2);
    vec3 d = normalize(vW - uEye);
    vec3 r = reflect(d, m);
    // the hammer, looked for along the reflected ray, in its own frame
    vec3 right = vec3(-cos(uHammer.w), 0.0, -sin(uHammer.w)), ahead = vec3(sin(uHammer.w), 0.0, -cos(uHammer.w));
    vec3 q = vW - uHammer.xyz;
    vec3 ro = vec3(dot(q, right), q.y, dot(q, ahead)), rd = vec3(dot(r, right), r.y, dot(r, ahead));
    const float H = 0.3;
    vec3 n1, n2;
    float t1 = box(ro, rd, vec3(0.0, H * 0.5, 0.0), vec3(0.018, H * 0.5, 0.018), n1);
    float t2 = box(ro, rd, vec3(0.0, H, 0.0), vec3(0.057, 0.047, 0.09), n2);
    float t = t1 > 0.0 && (t2 < 0.0 || t1 < t2) ? t1 : t2;
    vec3 nl = t == t1 ? n1 : n2;
    vec3 col;
    float a;
    if (t > 0.0) {
        // diamond seen in quicksilver: cold, clear, a spark on a facet
        vec3 nb = nl.x * right + vec3(0.0, nl.y, 0.0) + nl.z * ahead;
        vec3 rr = reflect(r, nb);
        float c = clamp(dot(nb, -r), 0.0, 1.0);
        vec3 room = vec3(0.15, 0.16, 0.20) * smoothstep(-0.3, 1.0, rr.y);
        vec3 ice = vec3(0.78, 0.92, 1.05);
        float spark = pow(max(dot(rr, normalize(SUN)), 0.0), 60.0);
        col = (0.06 + (env(rr) + haze(rr) + room) * 1.5) * (0.55 + 0.45 * pow(1.0 - c, 2.0)) * ice + ice * 0.12 + vec3(spark * 2.5);
        a = 0.82;
    } else {
        // elsewhere the sheet is dark and thin: the far band of light and the stars, faintly
        col = vec3(0.01, 0.012, 0.016) + haze(r) * 3.0 + stars(r) * 1.5 + env(r) * 0.2;
        a = 0.4;
    }
    // a faint sheen across the face, stronger at a glance
    col += vec3(0.6, 0.65, 0.8) * 0.06 * pow(1.0 - abs(dot(n, d)), 3.0);
    // no frame: the edges fray and waver with the rest
    float edge = min(1.0 - abs(vTag.x), min(vTag.y, 1.0 - vTag.y) * 2.0) + 0.05 * w1;
    a *= smoothstep(0.0, 0.3, edge);
    o = vec4(tonemap(col * fog(length(uEye - vW))), a);
}
