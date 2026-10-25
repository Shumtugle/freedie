#version 300 es
// The faces of the boxes: a coarse pattern for each material, sampled without smoothing,
// lit by a low sun with soft shadows and by the sky; far things go into the haze.
// A material may carry a number of its own in its fraction (a house's seed, a flag).
precision highp float;
precision highp int;
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
uniform float uFogStart;
uniform float uFogScale;
uniform float uDark;          // how far the world has gone into the dark
uniform vec3 uSpot;           // the one pool of light left in it
uniform float uSpotRadius;
uniform float uGoldGlow;      // how much gold keeps its own light in the dark

out vec4 outColor;

const float PLAIN = 0.0, GRAIN = 1.0, STONE = 2.0, ASPHALT = 3.0, WOOD = 4.0, PAVING = 5.0, PAINT = 6.0, GOLD = 7.0;
const float FACADE = 8.0, FROND = 9.0, TRUNK = 10.0, ROAD = 11.0, GLASS = 12.0, GLOW = 13.0, SEA = 14.0, RAIL = 15.0;
const float PROMENADE = 16.0, AWNING = 17.0, SQUARE = 18.0, LANE = 19.0;
const float POLISHED = 20.0, RUBBER = 21.0;

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

// the integer hash the world uses too, so both agree on which window is shut
int nz(int a, int b, int seed) {
    uint h = uint(a) * 374761393u + uint(b) * 668265263u + uint(seed) * 1442695041u;
    h = (h ^ (h >> 13u)) * 1274126177u;
    return int((h ^ (h >> 16u)) & 255u);
}

vec3 rgb(int c) {
    return vec3(float((c >> 16) & 255), float((c >> 8) & 255), float(c & 255)) / 255.0;
}

vec3 lin(vec3 c) { return pow(c, vec3(2.2)); }

// ACES fitted curve
vec3 tone(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

// how much of the sun reaches a point, seen from the sun as lp
float shadowAt(vec4 lp) {
    vec3 p = lp.xyz * 0.5 + 0.5;
    if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
    p.z -= 0.0012;
    float s = 0.0;
    for (int i = -1; i <= 1; i++)
        for (int j = -1; j <= 1; j++)
            s += texture(uShadow, vec3(p.xy + vec2(float(i), float(j)) * uShadowTexel, p.z));
    return s / 9.0;
}

// a frond: leaflets slanting from a spine toward the tip, gaps between them (as in shadow.frag)
// (each half of a frond is a box of its own, its spine along the box's -z edge)
bool frondHole(vec3 l, vec3 hf) {
    float across = (l.z / hf.z + 1.0) * 0.5;
    if (across < 0.1) return false;
    if (across > 0.96) return true;
    return fract((l.x - (l.z + hf.z) * 0.8) / 0.12) > 0.55;
}

// a railing: a rail along the top and the bottom, bars between (as in shadow.frag)
bool railHole(vec3 l, vec3 hf) {
    float v = l.y + hf.y;
    if (v < 0.05 || v > 2.0 * hf.y - 0.06) return false;
    float a = hf.x > hf.z ? l.x : l.z;
    return fract(a / 0.13) > 0.3;
}

/**
 * A street front in a seaside quarter at (u along the wall, v up), meters, unlit: shops at
 * street level, above them tall windows with shutters, floor after floor, a cornice line
 * at every floor. Balconies and awnings are boxes of their own. Glass comes back glossy.
 */
vec3 facade(float u, float v, vec3 base, int seed, out float glass) {
    glass = 0.0;
    const float fh = 3.3, bw = 2.9;
    int storey = int(floor(v / fh));
    float fv = v - float(storey) * fh;
    int bay = int(floor(u / bw));
    float fu = u - float(bay) * bw;
    int h = nz(bay, storey, seed);
    // stucco: patches of the wall a little lighter or darker
    float st = float(nz(int(u * 3.0), int(v * 3.0), seed + 7));
    vec3 wall = base * (0.93 + st / 2550.0 + (float(nz(bay, storey / 2, seed + 3)) - 128.0) / 2200.0);
    if (storey > 0 && fv < 0.14) return base * 0.78;                 // cornice line
    if (storey > 0 && fv < 0.22) return base * 1.06;
    if (storey == 0) {
        if (fu > 0.3 && fu < 2.6 && fv < 2.75) {
            if (fv < 0.2) return rgb(0x4A4440);
            glass = 1.0;
            return nz(bay, 99, seed) > 150 ? rgb(0x6E4A2E) : rgb(0x35302D);
        }
        return wall * 0.92;
    }
    bool closed = (h & 7) == 0;
    vec3 shutter = (h & 16) != 0 ? rgb(0x3F6A50) : rgb(0x557587);
    float slat = mod(floor(fv * 9.0), 2.0) == 0.0 ? 1.0 : 0.8;
    if (fv > 0.36 && fv < 2.7) {
        if (fu > 0.95 && fu < 1.95) {
            if (closed) return shutter * slat;
            if (fu < 1.01 || fu > 1.89 || fv > 2.62) return rgb(0xE9E3D6);       // frame
            glass = 1.0;
            return fv > 1.6 ? rgb(0x3A4048) : rgb(0x2C2E33);
        }
        if ((fu > 0.5 && fu < 0.95) || (fu > 1.95 && fu < 2.4)) {
            if (closed) return wall;
            return shutter * slat;                                              // open shutters
        }
        if (fu > 0.42 && fu < 2.48 && fv > 2.62) return wall * 1.08;
    }
    if (fv > 2.7 && fv < 2.95 && fu > 0.85 && fu < 2.05) return wall * 1.1; // lintel
    return wall;
}

/** The street's asphalt with its paint: the zebra and its yellow bars, the middle line, the edges. */
vec3 road(vec2 p, vec3 base) {
    float x = p.x, z = p.y, ax = abs(x);
    float patch2 = hash(vec3(floor(x * 0.7 + 31.0), floor(z * 0.5), 2.0));
    vec3 c = patch2 < 0.16 ? base * 0.86 : base;
    c *= 0.9 + 0.16 * hash(vec3(floor(p / 0.14), 3.0));
    float m = mod(x + 20.0, 0.9);
    if (ax < 3.5 && z > 3.4 && z < 5.0 && m < 0.5) c = rgb(0xE4DFD3);
    if (ax < 3.5 && ((z > 3.15 && z < 3.36) || (z > 5.04 && z < 5.25)) && m < 0.5) c = rgb(0xE0A43A);
    if (ax < 0.07 && z > 5.4 && mod(z, 6.0) < 2.6) c = rgb(0xE2DED4);
    if (z > 2.55 && z < 2.75 && x < 0.0 && x > -3.5) c = rgb(0xE2DED4);
    if (z > 1.5 && z < 9.0 && ((ax > 0.55 && ax < 0.75) || (ax > 1.85 && ax < 2.05))
            && hash(vec3(floor(z * 3.0), floor(x * 20.0), 4.0)) < 0.47) c = mix(c, rgb(0x3A3734), 0.45);
    if (ax > 3.75 && ax < 3.88) c = rgb(0xDDA638);
    else if (ax >= 3.88) c = rgb(0x5E5A55);
    return c;
}

#ifdef SPHERE
flat in vec3 vCenter;
flat in vec3 vTurn0, vTurn1, vTurn2;          // the ball's turn, column after column
uniform mat4 uViewProj;
uniform mat4 uLight;

// the three finger holes, as directions in the ball's own frame
const vec3 HOLE0 = vec3(0.0, 0.96, 0.28), HOLE1 = vec3(-0.24, 0.95, -0.2), HOLE2 = vec3(0.24, 0.95, -0.2);

/**
 * A ball, drawn on the faces of the cube round it: the eye's ray through this pixel is
 * followed in the ball's own frame to where it meets the sphere; outside it there is
 * nothing. The depth is the sphere's, so balls touch and hide each other as round things do.
 * A polished ball has a marbled swirl and three finger holes that turn with it and a small
 * hard shine; a rubber one is plain with a broader, softer shine.
 */
void sphere(float mat) {
    float r = vHalf.x;
    mat3 vTurn = mat3(vTurn0, vTurn1, vTurn2);
    mat3 back = transpose(vTurn);
    vec3 ro = back * (uEye - vCenter);
    vec3 rd = normalize(back * (vWorld - uEye));
    float b = dot(ro, rd);
    float h = b * b - dot(ro, ro) + r * r;
    if (h < 0.0) discard;
    vec3 l = (ro + rd * (-b - sqrt(h))) / r;              // on the unit sphere, in the ball's frame
    vec3 world = vCenter + vTurn * (l * r);
    vec4 clip = uViewProj * vec4(world, 1.0);
    gl_FragDepth = clip.z / clip.w * 0.5 + 0.5;
    vec3 n = vTurn * l;
    vec3 v = normalize(uEye - world);
    bool rubber = mat == RUBBER;
    vec3 base = lin(vColor);
    bool hole = false;
    if (!rubber) {
        hole = max(max(dot(l, HOLE0), dot(l, HOLE1)), dot(l, HOLE2)) > 0.988;
        float swirl = sin(l.x * 7.0 + sin(l.y * 5.0 + l.z * 3.0) * 2.2);
        if (hole) base = lin(rgb(0x14121A));
        else if (swirl > 0.25) base = lin(mix(vColor, vec3(1.0), 0.28));
    }
    float sh = shadowAt(uLight * vec4(world + n * 0.04, 1.0));
    float ndl = dot(n, uSun);
    vec3 amb = mix(uAmbGround, uAmbSky, n.y * 0.5 + 0.5);
    vec3 c = base * (amb + uSunColor * max(ndl, 0.0) * sh);
    if (!hole) {
        // the shine sits half way between the sun and the eye
        float sp = max(dot(n, normalize(uSun + v)), 0.0);
        sp = rubber ? pow(sp, 10.0) * 0.6 : pow(sp, 48.0) * 1.5;
        c += uSunColor * sp * vec3(1.0, 0.92, 0.8) * sh;
        if (!rubber) {
            // polish: the sky in it, more at a slant
            vec3 rf = reflect(-v, n);
            float fres = 0.05 + 0.35 * pow(1.0 - max(dot(n, v), 0.0), 3.0);
            c = mix(c, rf.y > 0.0 ? lin(vec3(0.62, 0.72, 0.86)) : uAmbGround * 0.6, fres);
        }
    }
    float dist = length(world - uEye);
    float haze = 1.0 - exp(-max(dist - uFogStart, 0.0) / uFogScale);
    c = mix(c, lin(uFog), haze);
    float pool = 1.0 - smoothstep(uSpotRadius * 0.55, uSpotRadius, length((world - uSpot) * vec3(0.55, 1.0, 1.0)));
    c *= mix(1.0, pool, uDark);
    outColor = vec4(pow(tone(c), vec3(1.0 / 2.2)), 1.0);
}
#endif

void main() {
    float mat = floor(vMaterial + 0.001);
#ifdef SPHERE
    sphere(mat);
    return;
#endif
    int seed = int(floor(fract(vMaterial + 0.001) * 256.0));
    if (mat == FROND && frondHole(vLocal, vHalf)) discard;
    if (mat == RAIL && railHole(vLocal, vHalf)) discard;

    // the face's own two directions, in meters, and how far each runs from the middle
    vec2 uv, ext;
    if (abs(vFace.x) > 0.5) { uv = vLocal.zy; ext = vHalf.zy; }
    else if (abs(vFace.y) > 0.5) { uv = vLocal.xz; ext = vHalf.xz; }
    else { uv = vLocal.xy; ext = vHalf.xy; }
    float face = dot(vFace, vec3(1.0, 2.0, 3.0));
    vec3 base = lin(vColor);
    float k = 1.0;
    float edge = min(ext.x - abs(uv.x), ext.y - abs(uv.y));
    float gloss = 0.0, glass = 0.0, leaf = 0.0;
    vec3 v = normalize(uEye - vWorld);
    float dist = length(vWorld - uEye);
    float haze = 1.0 - exp(-max(dist - uFogStart, 0.0) / uFogScale);

    if (mat == GLOW) {
        // lit from inside: no sun, no shade, only the haze
        vec3 c = mix(base * 1.7, lin(uFog) * (1.0 - uDark), haze);
        outColor = vec4(pow(tone(c), vec3(1.0 / 2.2)), 1.0);
        return;
    }
    if (mat == GRAIN) {
        k = 0.86 + 0.16 * hash(vec3(floor(uv / 0.06), face));
        if (edge < 0.03) k *= 0.8;
    } else if (mat == STONE) {
        float n = hash(vec3(floor(uv / 0.05), face + 7.0));
        k = n < 0.2 ? 0.74 : n < 0.55 ? 1.0 : n < 0.85 ? 0.92 : 1.07;
        if (edge < 0.025) k *= 0.85;
    } else if (mat == ASPHALT) {
        vec2 g = floor(vWorld.xz / 0.14);
        k = 0.8 + 0.32 * hash(vec3(g, 3.0));
    } else if (mat == ROAD) {
        base = lin(road(vWorld.xz, vColor));
    } else if (mat == WOOD) {
        // planks run along the longer way
        float across = ext.x > ext.y ? uv.y : uv.x;
        float along = ext.x > ext.y ? uv.x : uv.y;
        k = 0.86 + 0.12 * hash(vec3(floor(across / 0.04), floor(along / 0.5), face));
        if (edge < 0.025) k *= 0.78;
    } else if (mat == PAVING) {
        // square slabs in front of the houses
        vec2 t = vWorld.xz / 0.7;
        vec2 f = fract(t);
        k = (f.x < 0.05 || f.y < 0.05) ? 0.82 : 0.95 + 0.07 * hash(vec3(floor(t), 5.0));
    } else if (mat == PROMENADE) {
        // big pale stone in a herringbone of joints
        vec2 t = vec2(vWorld.x + vWorld.z, vWorld.z - vWorld.x) * 0.7;
        vec2 f = fract(t);
        k = (f.x < 0.04 || f.y < 0.04) ? 0.82 : 0.96 + 0.06 * hash(vec3(floor(t), 6.0));
    } else if (mat == SQUARE) {
        // warm stone laid in rings round the middle, a darker band toward the rim
        float rr = length(vec2(vLocal.x, vLocal.z * 1.18));
        k = mod(rr, 1.2) < 0.06 ? 0.83 : 0.96 + 0.06 * hash(vec3(floor(vWorld.xz * 1.3), 7.0));
        if (rr > 9.5 && rr < 10.3) base = lin(vec3(0.72, 0.56, 0.42));
    } else if (mat == LANE) {
        // asphalt in patches, a dashed line down the middle of the longer way
        bool alongX = vHalf.x > vHalf.z;
        float across = alongX ? vLocal.z : vLocal.x, along = alongX ? vWorld.x : vWorld.z;
        float p = hash(vec3(floor(vWorld.x * 0.6 + 31.0), floor(vWorld.z * 0.5), 8.0));
        k = (p < 0.16 ? 0.86 : 1.0) * (0.9 + 0.16 * hash(vec3(floor(vWorld.xz / 0.14), 3.0)));
        if (abs(across) < 0.08 && mod(along, 6.0) < 2.6) { base = lin(vec3(0.886, 0.871, 0.831)); k = 1.0; }
    } else if (mat == PAINT) {
        k = 0.95 + 0.06 * hash(vec3(floor(uv / 0.25), face));
    } else if (mat == FACADE) {
        if (abs(vFace.y) < 0.5) {
            base = lin(facade(uv.x + ext.x, uv.y + ext.y, vColor, seed, glass));
        } else {
            base *= 0.7;                                  // the roof
        }
    } else if (mat == AWNING) {
        // cloth, plain or striped across, the stripes running out from the wall
        if (seed == 1 && mod(floor(vLocal.x * 4.0), 2.0) == 0.0) base = lin(vec3(0.957, 0.933, 0.886));
        k = 0.95 + 0.06 * hash(vec3(floor(uv / 0.1), face));
    } else if (mat == TRUNK) {
        // rings of old leaf bases up the trunk
        float r = fract((vLocal.y + float(seed) * 0.07) / 0.2);
        k = r < 0.25 ? 0.62 : 0.95 + 0.1 * hash(vec3(floor(uv / 0.06), face));
    } else if (mat == FROND) {
        float across = (vLocal.z / vHalf.z + 1.0) * 0.5;
        k = across < 0.1 ? 0.8 : 0.9 + 0.2 * hash(vec3(floor(vLocal.xz / 0.06), face));
        leaf = 1.0;
    } else if (mat == RAIL) {
        k = 1.0;
    } else if (mat == GLASS) {
        glass = 1.0;
    } else if (mat == SEA) {
        // the sea: darker near, paler far, glittering toward the sun
        float far = clamp(1.0 - v.y * 9.0, 0.0, 1.0);
        base = lin(mix(rgb(0x2D6990), rgb(0xB6CBD0), pow(far, 3.0)));
        float n = hash(vec3(floor(vWorld.x * 1.5), floor(vWorld.z * 0.6), 9.0));
        vec3 r = reflect(-uSun, vec3(0.0, 1.0, 0.0));
        float toward = max(dot(r, v), 0.0);
        if (n > 0.93 - 0.2 * toward) base = mix(base, lin(rgb(0xFFE3B0)), 0.75 * toward);
        else if (n < 0.16) base *= 0.85;
    } else if (mat == GOLD) {
        k = 0.92 + 0.1 * hash(vec3(floor(uv / 0.05), face));
        if (edge < 0.025) k *= 0.75;
        gloss = 1.0;
    } else {
        k = 0.97 + 0.04 * hash(vec3(floor(uv / 0.08), face));
    }
    vec3 albedo = base * k;
    vec3 n = normalize(vNormal);
    float sh = shadowAt(vLightPos);
    float ndl = dot(n, uSun);
    // leaves let the light through: lit from either side, never quite dark
    float lit = leaf > 0.0 ? (0.35 + 0.65 * abs(ndl)) * sh : max(ndl, 0.0) * sh;
    vec3 amb = mix(uAmbGround, uAmbSky, n.y * 0.5 + 0.5);
    vec3 c = albedo * (amb + uSunColor * lit);
    if (gloss > 0.0) {
        float spec = pow(max(dot(reflect(-uSun, n), v), 0.0), 18.0) * sh;
        c += uSunColor * spec * vec3(1.0, 0.82, 0.45) * 0.8 + albedo * 0.25;
    }
    if (glass > 0.0) {
        // glass shows the sky, more of it at a slant, and a glint of the sun
        vec3 r = reflect(-v, n);
        float fres = 0.08 + 0.5 * pow(1.0 - max(dot(n, v), 0.0), 3.0);
        vec3 sky = r.y > 0.0 ? lin(vec3(0.62, 0.72, 0.86)) : uAmbGround;
        c = mix(c, sky, fres);
        c += uSunColor * pow(max(dot(r, uSun), 0.0), 40.0) * sh * 0.6;
    }
    c = mix(c, lin(uFog), haze);
    // in the dark only the pool of light is left
    // (a wide pool: what stands to either side of it is lit further out than what is below)
    float pool = 1.0 - smoothstep(uSpotRadius * 0.55, uSpotRadius, length((vWorld - uSpot) * vec3(0.55, 1.0, 1.0)));
    // gold keeps its own light in the dark
    if (mat == GOLD) pool = max(pool, 0.9 * uGoldGlow);
    c *= mix(1.0, pool, uDark);
    outColor = vec4(pow(tone(c), vec3(1.0 / 2.2)), 1.0);
}
