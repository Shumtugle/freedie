#version 300 es
// The faces of the boxes: a coarse pattern for each material, sampled without smoothing,
// lit by a low sun with soft shadows and by the sky; far things go into the haze.
precision highp float;
precision highp sampler2DShadow;

in vec3 vWorld;
in vec3 vLocal;
in vec3 vNormal;
in vec3 vColor;
in vec4 vLightPos;
flat in vec3 vHalf;
flat in vec3 vFace;
flat in float vMaterial;

uniform sampler2DShadow uShadow;
uniform float uShadowTexel;
uniform vec3 uSun;
uniform vec3 uSunColor;
uniform vec3 uAmbSky;
uniform vec3 uAmbGround;
uniform vec3 uEye;
uniform vec3 uFog;
uniform float uFogNear;
uniform float uFogFar;

out vec4 outColor;

const float PLAIN = 0.0, GRAIN = 1.0, STONE = 2.0, ASPHALT = 3.0, WOOD = 4.0, PAVING = 5.0, PAINT = 6.0, GOLD = 7.0;

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

vec3 lin(vec3 c) { return pow(c, vec3(2.2)); }

// ACES fitted curve
vec3 tone(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

float shadow() {
    vec3 p = vLightPos.xyz * 0.5 + 0.5;
    if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
    p.z -= 0.0015;
    float s = 0.0;
    for (int i = -1; i <= 1; i++)
        for (int j = -1; j <= 1; j++)
            s += texture(uShadow, vec3(p.xy + vec2(float(i), float(j)) * uShadowTexel, p.z));
    return s / 9.0;
}

void main() {
    // the face's own two directions, in meters, and how far each runs from the middle
    vec2 uv, ext;
    if (abs(vFace.x) > 0.5) { uv = vLocal.zy; ext = vHalf.zy; }
    else if (abs(vFace.y) > 0.5) { uv = vLocal.xz; ext = vHalf.xz; }
    else { uv = vLocal.xy; ext = vHalf.xy; }
    float face = dot(vFace, vec3(1.0, 2.0, 3.0));
    vec3 base = lin(vColor);
    float k = 1.0;
    float edge = min(ext.x - abs(uv.x), ext.y - abs(uv.y));
    float gloss = 0.0;
    if (vMaterial == GRAIN) {
        k = 0.86 + 0.16 * hash(vec3(floor(uv / 0.06), face));
        if (edge < 0.03) k *= 0.8;
    } else if (vMaterial == STONE) {
        float n = hash(vec3(floor(uv / 0.05), face + 7.0));
        k = n < 0.2 ? 0.74 : n < 0.55 ? 1.0 : n < 0.85 ? 0.92 : 1.07;
        if (edge < 0.025) k *= 0.85;
    } else if (vMaterial == ASPHALT) {
        vec2 g = floor(vWorld.xz / 0.14);
        k = 0.8 + 0.32 * hash(vec3(g, 3.0));
    } else if (vMaterial == WOOD) {
        // planks run along the longer way
        float across = ext.x > ext.y ? uv.y : uv.x;
        float along = ext.x > ext.y ? uv.x : uv.y;
        k = 0.86 + 0.12 * hash(vec3(floor(across / 0.04), floor(along / 0.5), face));
        if (edge < 0.025) k *= 0.78;
    } else if (vMaterial == PAVING) {
        vec2 t = vWorld.xz / 0.5;
        vec2 f = fract(t);
        k = (f.x < 0.07 || f.y < 0.07) ? 0.78 : 0.95 + 0.08 * hash(vec3(floor(t), 5.0));
    } else if (vMaterial == PAINT) {
        k = 0.95 + 0.06 * hash(vec3(floor(uv / 0.25), face));
        if (abs(vFace.y) < 0.5) {
            // the house front: floors, windows with shutters, a ledge under each floor
            vec2 cell = (uv + ext) / vec2(2.6, 3.2);
            vec2 f = fract(cell);
            if (f.y < 0.05) { base = lin(vec3(0.94, 0.91, 0.84)); k = 1.0; }
            else if (f.x > 0.32 && f.x < 0.68 && f.y > 0.25 && f.y < 0.8) {
                base = lin(vec3(0.17, 0.2, 0.25)); k = f.x < 0.4 ? 1.6 : 1.0;
            } else if ((f.x > 0.22 && f.x < 0.3) || (f.x > 0.7 && f.x < 0.78)) {
                if (f.y > 0.25 && f.y < 0.8) { base = lin(vec3(0.31, 0.5, 0.36)); k = 1.0; }
            }
        }
    } else if (vMaterial == GOLD) {
        k = 0.92 + 0.1 * hash(vec3(floor(uv / 0.05), face));
        if (edge < 0.025) k *= 0.75;
        gloss = 1.0;
    } else {
        k = 0.97 + 0.04 * hash(vec3(floor(uv / 0.08), face));
    }
    vec3 albedo = base * k;
    vec3 n = normalize(vNormal);
    float sh = shadow();
    float lit = max(dot(n, uSun), 0.0) * sh;
    vec3 amb = mix(uAmbGround, uAmbSky, n.y * 0.5 + 0.5);
    vec3 c = albedo * (amb + uSunColor * lit);
    if (gloss > 0.0) {
        vec3 v = normalize(uEye - vWorld);
        float spec = pow(max(dot(reflect(-uSun, n), v), 0.0), 18.0) * sh;
        c += uSunColor * spec * vec3(1.0, 0.82, 0.45) * 0.8 + albedo * 0.25;
    }
    float dist = length(vWorld - uEye);
    c = mix(c, lin(uFog), smoothstep(uFogNear, uFogFar, dist));
    outColor = vec4(pow(tone(c), vec3(1.0 / 2.2)), 1.0);
}
