#version 300 es
// The evening sky behind everything: deep blue overhead, warm low down toward the sun with
// a halo round it, thin streaks of pink cloud drifting along the horizon; and far off in it,
// now and then, birds or a plane's white trail, laid out by the world as strokes.
precision highp float;

in vec2 vNdc;

uniform vec3 uRight;
uniform vec3 uUp;
uniform vec3 uAhead;
uniform vec2 uFrame;      // the frame in pixels
uniform float uFocal;
uniform vec3 uTop;
uniform vec3 uHorizon;
uniform vec3 uGlow;
uniform vec3 uSun;
uniform float uTime;
uniform float uDark;     // how far the world has gone into the dark
uniform float uOvercast; // out at sea: a low heavy sky instead of the evening one
// strokes: ends in frame pixels, then widths and covers at the ends (a negative first width: dark)
uniform vec4 uFly[64];
uniform int uFlyN;

out vec4 outColor;

float h(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float n(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h(i), h(i + vec2(1.0, 0.0)), f.x), mix(h(i + vec2(0.0, 1.0)), h(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) {
    float a = 0.5, s = 0.0;
    for (int i = 0; i < 5; i++) { s += a * n(p); p *= 2.03; a *= 0.5; }
    return s;
}

void main() {
    vec3 d = normalize(uAhead * uFocal + uRight * (vNdc.x * uFrame.x * 0.5) + uUp * (vNdc.y * uFrame.y * 0.5));
    float el = max(d.y, 0.0);
    vec2 flatD = normalize(d.xz + vec2(1e-5));
    vec2 flatS = normalize(uSun.xz + vec2(1e-5));
    float toSun = max(dot(flatD, flatS), 0.0);              // 1 facing the sun
    float k = pow(min(1.0, el * 2.4), 0.5);
    vec3 low = mix(uHorizon, uGlow, toSun * 0.9);
    vec3 c = mix(low, uTop, k);
    float halo = toSun * toSun * exp(-el * 5.0);
    float glow = pow(toSun, 4.0) * exp(-el * 9.0);
    c = mix(c, vec3(1.0, 0.79, 0.49), halo * 0.55);
    c = mix(c, vec3(1.0, 0.94, 0.78), glow * 0.8);
    // clouds: thin streaks stretched along the horizon, pink, warmer toward the sun
    vec2 uv = d.xz / max(d.y, 0.05) * vec2(0.35, 1.6) + vec2(uTime * 0.004, 0.0);
    float cl = smoothstep(0.55, 0.85, fbm(uv * 1.3 + vec2(3.0, 1.0))) * smoothstep(0.03, 0.1, d.y) * (1.0 - smoothstep(0.3, 0.55, d.y));
    vec3 cloud = mix(vec3(0.94, 0.66, 0.56), vec3(1.0, 0.85, 0.72), toSun * 0.7);
    cloud = mix(cloud, vec3(0.76, 0.64, 0.71), smoothstep(0.45, 0.75, fbm(uv * 3.1 + 11.0)) * 0.5);
    c = mix(c, cloud, cl * 0.8);
    float s = max(dot(d, uSun), 0.0);
    c += vec3(1.0, 0.85, 0.6) * (pow(s, 64.0) * 0.6 + pow(s, 8.0) * 0.08);
    if (uOvercast > 0.0) {
        // a low heavy sky, neither night nor day: grey, darker overhead, banks of cloud
        // dragging across, no sun to be seen; fog low down eats the horizon
        vec2 q = d.xz / max(d.y + 0.08, 0.06);
        float bank = fbm(q * 0.6 + vec2(uTime * 0.02, 0.0));
        vec3 w = mix(uHorizon, uTop, pow(min(1.0, el * 1.6), 0.7));
        w *= 0.82 + 0.3 * bank;
        w = mix(w, uGlow, exp(-max(d.y, 0.0) * 14.0) * 0.8);
        c = mix(c, w, uOvercast);
    }
    // what flies: each stroke covers the pixels near its line, hard-edged like everything else
    vec2 p = vec2(gl_FragCoord.x, uFrame.y - gl_FragCoord.y);
    for (int i = 0; i < 32; i++) {
        if (i >= uFlyN) break;
        vec4 e = uFly[2 * i], m = uFly[2 * i + 1];
        vec2 a = e.xy, ab = e.zw - e.xy;
        float t = clamp(dot(p - a, ab) / max(dot(ab, ab), 1e-4), 0.0, 1.0);
        float dist = length(p - a - ab * t);
        float w = mix(abs(m.x), m.y, t);
        float cover = clamp((w * 0.5 + 0.35 - dist) * 2.5, 0.0, 1.0) * mix(m.z, m.w, t);
        vec3 ink = m.x < 0.0 ? vec3(0.17, 0.165, 0.19) : vec3(1.0);
        c = mix(c, ink, cover);
    }
    outColor = vec4(c * (1.0 - uDark), 1.0);
}
