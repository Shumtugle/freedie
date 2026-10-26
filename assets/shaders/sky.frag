#version 300 es
// The evening sky behind everything: deep blue overhead, warm low down toward the sun with
// a halo round it, thin streaks of pink cloud drifting along the horizon; and far off in it,
// now and then, birds or a plane's white trail, laid out by the world as strokes. Round the
// mirror up in the sky, when the eye looks up at it, a wreath of dark storm cloud.
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
uniform float uNight;    // how far into the night: the stars out, the moon in place of the sun
// strokes: ends in frame pixels, then widths and covers at the ends (a negative first width: dark)
uniform vec4 uFly[64];
uniform int uFlyN;
// the mirror's wreath: which way the mirror is and how wide it looks (radians, 0: none);
// how far out the cloud goes (in mirror widths), how black it is, lightning in it just now
uniform vec4 uHalo;
uniform vec3 uHaloK;

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
    c = mix(c, vec3(1.0, 0.79, 0.49), halo * 0.55 * (1.0 - uNight));
    c = mix(c, vec3(1.0, 0.94, 0.78), glow * 0.8 * (1.0 - uNight));
    // clouds: thin streaks stretched along the horizon, pink, warmer toward the sun
    vec2 uv = d.xz / max(d.y, 0.05) * vec2(0.35, 1.6) + vec2(uTime * 0.004, 0.0);
    float cl = smoothstep(0.55, 0.85, fbm(uv * 1.3 + vec2(3.0, 1.0))) * smoothstep(0.03, 0.1, d.y) * (1.0 - smoothstep(0.3, 0.55, d.y));
    vec3 cloud = mix(vec3(0.94, 0.66, 0.56), vec3(1.0, 0.85, 0.72), toSun * 0.7);
    cloud = mix(cloud, vec3(0.76, 0.64, 0.71), smoothstep(0.45, 0.75, fbm(uv * 3.1 + 11.0)) * 0.5);
    // overhead, where the eye looks up: heaped clouds, lit as the hour lights them
    if (d.y > 0.3 && uOvercast < 0.5) {
        vec2 q = d.xz / d.y * 1.3 + vec2(uTime * 0.012, 0.0);
        float heap = smoothstep(0.5, 0.78, fbm(q + 7.0)) * smoothstep(0.3, 0.55, d.y);
        float dim = clamp(length(uHorizon) / 1.49, 0.1, 1.0);
        vec3 lit = mix(vec3(1.0, 0.99, 0.97), uGlow, 0.35) * dim;
        vec3 under = mix(uTop, lit, 0.5);
        c = mix(c, mix(under, lit, smoothstep(0.35, 0.75, fbm(q * 2.3 + 3.0))), heap * 0.9);
    }
    // the clouds go dark with the sky
    cloud *= clamp(length(uHorizon) / 1.49, 0.15, 1.0);
    c = mix(c, cloud, cl * 0.8);
    float s = max(dot(d, uSun), 0.0);
    c += mix(vec3(1.0, 0.85, 0.6), vec3(0.75, 0.8, 0.95), uNight) * (pow(s, 64.0) * 0.6 + pow(s, 8.0) * 0.08 * (1.0 - uNight));
    if (uNight > 0.0) {
        // the moon, a hard pale disc; the stars, fixed in the sky, out once it is dark enough
        float dark = smoothstep(0.3, 0.75, uNight);
        if (s > 0.99985) c = mix(c, vec3(0.92, 0.93, 0.98), dark);
        // cells of a grid in space, not of latitude and longitude, so they keep their shape
        // overhead, where the view goes when it looks up
        vec3 g3 = floor(d * 160.0);
        float st = h(g3.xy + g3.z * vec2(0.37, 0.71));
        if (st > 0.993 && d.y > 0.06) c = mix(c, vec3(0.9, 0.92, 1.0), dark * smoothstep(0.06, 0.25, d.y) * (0.4 + 0.6 * (st - 0.993) / 0.007));
    }
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
    if (uHalo.w > 0.0) {
        // the wreath: torn, churning slowly, black at heart, the hour's light on its outer edge
        float a = acos(clamp(dot(d, uHalo.xyz), -1.0, 1.0)) / uHalo.w;
        vec3 ax = normalize(cross(uHalo.xyz, abs(uHalo.y) > 0.9 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0)));
        vec3 ay = cross(uHalo.xyz, ax);
        vec2 round2 = vec2(dot(d, ax), dot(d, ay));
        round2 /= max(length(round2), 1e-4);
        float churn = fbm(round2 * 2.2 + vec2(uTime * 0.03, -uTime * 0.02) + a * 0.9);
        float fine = fbm(round2 * 7.0 + vec2(a * 3.0 - uTime * 0.08, uTime * 0.05));
        float outer = uHaloK.x * (0.82 + 0.42 * churn) + 0.12 * (fine - 0.5);
        float cover = 1.0 - smoothstep(outer - 0.16, outer + 0.02, a);
        float edge = smoothstep(outer - 0.45, outer - 0.02, a);
        vec3 heart = mix(vec3(0.03, 0.03, 0.045), vec3(0.09, 0.1, 0.12), uOvercast);
        heart = mix(heart, heart * 0.4, uHaloK.y);
        // the outer edge takes the sky's own light, a lit fringe even at night
        vec3 rim = mix(uTop, uHorizon, 0.5) * 0.55 + vec3(0.06, 0.06, 0.08) * uNight;
        vec3 w = mix(heart, rim, edge * (0.45 + 0.55 * fine) * (1.0 - 0.6 * uHaloK.y));
        // lightning inside it lights it from within
        w += uHaloK.z * vec3(0.5, 0.5, 0.7) * smoothstep(0.5, 0.75, fine) * (1.0 - edge * 0.5);
        c = mix(c, w, cover);
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
