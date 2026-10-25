#version 300 es
// The evening sky behind everything: deep blue overhead, warm low down toward the sun with
// a halo round it, thin streaks of pink cloud drifting along the horizon.
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
    outColor = vec4(c, 1.0);
}
