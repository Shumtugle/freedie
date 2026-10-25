#version 300 es
// The small frame onto the screen, every frame pixel a hard square; the flat layer on top.
// A flash draws the whole frame in toward its middle and turns it white; a picture coming
// on like an old set opens from a bright line across the middle.
precision mediump float;

in vec2 vNdc;

uniform sampler2D uScene;
uniform sampler2D uFlat;
uniform float uFlash;
uniform float uTube;      // the set coming on, 0 to 1; below 0 it is on

out vec4 outColor;

void main() {
    vec2 uv = vNdc * 0.5 + 0.5;
    float glowTube = 0.0;
    if (uTube >= 0.0) {
        float k = max(0.012, uTube * uTube * (3.0 - 2.0 * uTube));
        uv.y = 0.5 + (uv.y - 0.5) / k;
        glowTube = (1.0 - k) * 0.7;
        if (uv.y < 0.0 || uv.y > 1.0) { outColor = vec4(0.0, 0.0, 0.0, 1.0); return; }
    }
    vec3 scene = texture(uScene, uv).rgb;
    vec4 flat2 = texture(uFlat, vec2(uv.x, 1.0 - uv.y));
    vec3 c = mix(scene, flat2.rgb, flat2.a);
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
    c = mix(c, vec3(0.957, 0.973, 1.0), glowTube);
    outColor = vec4(c, 1.0);
}
