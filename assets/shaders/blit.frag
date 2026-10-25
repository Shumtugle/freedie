#version 300 es
// The small frame onto the screen, every frame pixel a hard square; the flat layer on top.
precision mediump float;

in vec2 vNdc;

uniform sampler2D uScene;
uniform sampler2D uFlat;

out vec4 outColor;

void main() {
    vec2 uv = vNdc * 0.5 + 0.5;
    vec3 scene = texture(uScene, uv).rgb;
    vec4 flat2 = texture(uFlat, vec2(uv.x, 1.0 - uv.y));
    outColor = vec4(mix(scene, flat2.rgb, flat2.a), 1.0);
}
