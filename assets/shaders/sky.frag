#version 300 es
// The sky behind everything: deep blue overhead, pale at the horizon with a rose band low
// down, thin clouds drifting, the sun's glow.
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
    float k = clamp(d.y * 1.4, 0.0, 1.0);
    vec3 c = mix(uHorizon, uTop, pow(k, 0.55));
    c = mix(c, uGlow, 0.4 * exp(-pow((d.y - 0.05) * 12.0, 2.0)));
    vec2 uv = d.xz / max(d.y, 0.05) * vec2(0.35, 1.6) + vec2(uTime * 0.004, 0.0);
    float cl = smoothstep(0.55, 0.85, fbm(uv * 1.3 + vec2(3.0, 1.0))) * smoothstep(0.04, 0.12, d.y) * (1.0 - smoothstep(0.35, 0.6, d.y));
    c = mix(c, vec3(1.0, 0.86, 0.82), cl * 0.75);
    float s = max(dot(d, uSun), 0.0);
    c += vec3(1.0, 0.85, 0.6) * (pow(s, 64.0) * 0.6 + pow(s, 8.0) * 0.08);
    outColor = vec4(c, 1.0);
}
