#version 300 es
// The small frame onto the screen, every frame pixel a hard square; the flat layer on top,
// then the fine layer at the top of the frame (the crest). A flash draws the whole frame in
// toward its middle and turns it white; a picture coming on like an old set opens from a
// bright line across the middle. Last the aim, drawn smooth at the screen's own size from what
// the world says of it: the ring on the building and its wind-up, the dotted flight the throw
// would take, or the trail of the one in the air.
precision highp float;
precision highp int;

in vec2 vNdc;

uniform sampler2D uScene;
uniform sampler2D uFlat;
uniform float uFlash;
uniform float uTube;      // the set coming on, 0 to 1; below 0 it is on
uniform float uTime;
uniform vec3 uGrade;      // the hour's grade: contrast, saturation, warmth
uniform float uBolt;      // lightning just now: the whole frame lit cold for a moment
uniform sampler2D uHud;   // the fine layer over the top of the frame (the crest), premultiplied
uniform float uHudRows;   // how many frame rows it covers from the top; none, 0
uniform vec2 uScreen;     // the screen, in its own pixels
uniform float uScale;     // screen pixels to a frame pixel
// the aim: the ring's middle (frame pixels), the wind-up 0 to 1, shown (1) or not; the dots of
// the flight, so many, each x, y and kind (0 the aim's, 1 the throw's trail), and the box they
// all lie in (frame pixels), so the rest of the screen skips them
uniform vec4 uRing;
uniform vec3 uTrail[32];
uniform int uTrailN;
uniform vec4 uTrailBox;

out vec4 outColor;

const vec3 CREAM = vec3(1.0, 0.965, 0.847);
const vec3 SHADE = vec3(0.10, 0.07, 0.04);

// a soft disc or ring, in screen pixels: 1 inside, 0 outside, a pixel of smoothing
float disc(vec2 p, vec2 c, float r) {
    return clamp(r - length(p - c) + 0.5, 0.0, 1.0);
}

float ring(vec2 p, vec2 c, float r, float w) {
    float d = abs(length(p - c) - r);
    return clamp(w * 0.5 - d + 0.5, 0.0, 1.0);
}

void main() {
    vec2 uv = vNdc * 0.5 + 0.5;
    float glowTube = 0.0;
    if (uTube >= 0.0) {
        float k = max(0.012, uTube * uTube * (3.0 - 2.0 * uTube));
        uv.y = 0.5 + (uv.y - 0.5) / k;
        glowTube = (1.0 - k) * 0.7;
        if (uv.y < 0.0 || uv.y > 1.0) { outColor = vec4(0.0, 0.0, 0.0, 1.0); return; }
    }
    vec4 sc = texture(uScene, uv);
    vec3 scene = sc.rgb;
    // the hour of the day: colours deeper and contrast harder toward sunset, warm or cool
    if (uGrade != vec3(1.0, 1.0, 0.0)) {
        float l0 = dot(scene, vec3(0.299, 0.587, 0.114));
        scene = mix(vec3(l0), scene, uGrade.y);
        scene = clamp((scene - 0.45) * uGrade.x + 0.45, 0.0, 1.0);
        scene *= vec3(1.0 + 0.07 * uGrade.z, 1.0 + 0.01 * uGrade.z, 1.0 - 0.09 * uGrade.z);
    }
    vec4 flat2 = texture(uFlat, vec2(uv.x, 1.0 - uv.y));
    vec3 c = mix(scene, flat2.rgb, flat2.a);
    if (uHudRows > 0.0) {
        float gy = (1.0 - uv.y) * float(textureSize(uFlat, 0).y);
        if (gy < uHudRows) {
            vec4 hd = texture(uHud, vec2(uv.x, gy / uHudRows));
            c = c * (1.0 - hd.a) + hd.rgb;
        }
    }
    if (uFlash > 0.0) {
        // streaks drawn in toward the middle, longer as the flash grows
        vec3 sum = c;
        for (int i = 1; i < 8; i++) {
            vec2 q = 0.5 + (uv - 0.5) * (1.0 - uFlash * 0.07 * float(i));
            sum += texture(uScene, q).rgb;
        }
        c = mix(c, sum / 8.0, min(1.0, uFlash * 2.0));
        c = mix(c, vec3(1.0, 0.98, 0.93), smoothstep(0.25, 1.0, uFlash));
    }
    if (uBolt > 0.0) c = mix(c, vec3(0.86, 0.9, 1.0), uBolt * 0.3) + uBolt * 0.06;
    c = mix(c, vec3(0.957, 0.973, 1.0), glowTube);

    // the aim, in screen pixels from the top left
    vec2 p = vec2(gl_FragCoord.x, uScreen.y - gl_FragCoord.y);
    float s = uScale;
    vec2 pf = p / s;
    bool nearTrail = uTrailN > 0 && pf.x > uTrailBox.x - 4.0 && pf.y > uTrailBox.y - 4.0
            && pf.x < uTrailBox.z + 4.0 && pf.y < uTrailBox.w + 4.0;
    for (int i = 0; i < 32; i++) {
        if (!nearTrail) break;
        if (i >= uTrailN) break;
        vec3 t = uTrail[i];
        vec2 q = t.xy * s;
        if (t.z > 0.5) {
            // the throw's trail: amber dots, a dark rim round each
            c = mix(c, SHADE, disc(p, q, 3.0 * s) * 0.7);
            c = mix(c, vec3(1.0, 0.72, 0.2), disc(p, q, 2.3 * s));
        } else {
            c = mix(c, SHADE, disc(p, q, 1.7 * s) * 0.35);
            c = mix(c, CREAM, disc(p, q, 1.25 * s) * 0.95);
        }
    }
    if (uRing.w > 0.5 && length(pf - uRing.xy) < 26.0) {
        vec2 r0 = uRing.xy * s;
        float power = uRing.z;
        c = mix(c, CREAM, disc(p, r0, 21.0 * s) * 0.16);
        // the wind-up: an amber band from the top, clockwise; full, it flickers
        vec2 d = p - r0;
        float a = atan(d.x, -d.y);
        if (a < 0.0) a += 6.2831853;
        float fill = min(power, 1.0);
        float band = ring(p, r0, 22.0 * s, 4.6 * s) * step(a, fill * 6.2831853);
        vec3 amber = power >= 1.0 && fract(uTime * 4.0) < 0.5 ? vec3(1.0, 0.89, 0.48) : vec3(1.0, 0.79, 0.23);
        c = mix(c, SHADE, ring(p, r0, 22.0 * s, 6.0 * s) * step(a, fill * 6.2831853) * 0.35);
        c = mix(c, amber, band);
        // the ring itself: cream, a thin shade under it
        c = mix(c, SHADE, ring(p, r0 + vec2(0.0, 0.6 * s), 14.0 * s, 3.4 * s) * 0.35);
        c = mix(c, CREAM, ring(p, r0, 14.0 * s, 2.6 * s));
        c = mix(c, CREAM, disc(p, r0, 1.3 * s));
    }
    outColor = vec4(c, 1.0);
}
