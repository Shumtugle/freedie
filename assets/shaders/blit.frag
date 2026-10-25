#version 300 es
// The small frame onto the screen, every frame pixel a hard square; the flat layer on top.
// A flash draws the whole frame in toward its middle and turns it white.
precision mediump float;

in vec2 vNdc;

uniform sampler2D uScene;
uniform sampler2D uFlat;
uniform float uFlash;

out vec4 outColor;

void main() {
    vec2 uv = vNdc * 0.5 + 0.5;
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
    outColor = vec4(c, 1.0);
}
