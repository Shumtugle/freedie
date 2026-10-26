#version 300 es
// The small frame onto the screen, every frame pixel a hard square; the flat layer on top.
// A flash draws the whole frame in toward its middle and turns it white; a picture coming
// on like an old set opens from a bright line across the middle. In the fight the world is
// seen red and black, every colour still alive under it, and what is marked to burn burns in
// a golden fire; as it ends, the world goes gold, then back to day.
precision mediump float;

in vec2 vNdc;

uniform sampler2D uScene;
uniform sampler2D uFlat;
uniform float uFlash;
uniform float uTube;      // the set coming on, 0 to 1; below 0 it is on
uniform float uRed;       // how far the world is seen red, 0 to 1
uniform float uGold;      // how far it is seen gold
uniform float uTime;
uniform float uBlur;      // out of focus by so many frame pixels, waking from the dark
uniform vec3 uGrade;      // the hour's grade: contrast, saturation, warmth

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
    vec4 sc = texture(uScene, uv);
    if (uBlur > 0.05) {
        // a soft disc of the frame round the pixel, as eyes not yet in focus see it
        vec2 px = uBlur / vec2(textureSize(uScene, 0));
        vec3 sum = vec3(0.0);
        float n = 0.0;
        for (int j = -3; j <= 3; j++)
            for (int i = -3; i <= 3; i++) {
                vec2 o = vec2(float(i), float(j)) / 3.0;
                if (dot(o, o) > 1.0) continue;
                sum += texture(uScene, uv + o * px).rgb;
                n += 1.0;
            }
        sc.rgb = sum / n;
    }
    vec3 scene = sc.rgb;
    // the hour of the day: colours deeper and contrast harder toward sunset, warm or cool
    if (uGrade != vec3(1.0, 1.0, 0.0)) {
        float l0 = dot(scene, vec3(0.299, 0.587, 0.114));
        scene = mix(vec3(l0), scene, uGrade.y);
        scene = clamp((scene - 0.45) * uGrade.x + 0.45, 0.0, 1.0);
        scene *= vec3(1.0 + 0.07 * uGrade.z, 1.0 + 0.01 * uGrade.z, 1.0 - 0.09 * uGrade.z);
    }
    // what glows of her own (her eyes, her knees), and the diamond hammer, keep their colours through it all
    if ((uRed > 0.0 || uGold > 0.0) && sc.a > 0.375) {
        float l = dot(scene, vec3(0.3, 0.55, 0.15));
        vec3 o = scene;
        if (sc.a < 0.75 && uRed > 0.0) {
            // golden fire: dark embers in the folds, gold and white heat on the edges
            float y = uv.y * 640.0, fl = 0.88 + 0.12 * sin(y * 0.31 + uTime * 11.0) * sin(y * 0.07 - uTime * 6.6);
            float t = min(1.0, l * fl * 1.25);
            vec3 f = t < 0.4 ? vec3(60.0 + t / 0.4 * 170.0, 10.0 + t / 0.4 * 70.0, 8.0 + t * 20.0)
                             : vec3(230.0 + (t - 0.4) / 0.6 * 25.0, 80.0 + (t - 0.4) / 0.6 * 160.0,
                                    t < 0.7 ? 8.0 + t * 20.0 : 22.0 + (t - 0.7) / 0.3 * 150.0);
            o = mix(o, f / 255.0, uRed);
        } else if (uRed > 0.0) {
            // red and black: the reds kept, the rest sunk, the shadows deeper
            float d = l * l * 1.4;
            vec3 r = vec3(min(1.0, scene.r * 0.55 + d * 0.75), scene.g * 0.22 + d * 0.157, scene.b * 0.2 + d * 0.118);
            o = mix(o, r, uRed * 0.85);
        }
        if (uGold > 0.0) {
            vec3 g = vec3(min(1.0, l * 1.137 + 0.078), min(1.0, l * 0.9 + 0.031), l * 0.43);
            o = mix(o, g, uGold * 0.75);
        }
        scene = o;
    }
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
