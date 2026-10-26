package com.shumtugle.freedie;

/**
 * The time of day on the street, which runs with the game: one long evening from the first
 * level to the portal. At the start the sun is already low and the shadows long; as the
 * episodes go by it sinks, the light grows warmer, the colours deeper and the contrast
 * harder, to a red sunset; then the sun is gone, an afterglow, the lamps and the windows
 * come on, the blue hour; and the last levels before the portal are played at night under
 * the moon and the stars.
 *
 * Kept as a few moments, by the level's number, and eased between them; the world asks for
 * the light of the level being played.
 */
final class Daylight {
    private Daylight() {
    }

    // the moments, by the number on the level's plate
    private static final float[] AT = {1f, 40f, 60f, 75f, 88f, 101f};
    // the sun (then the moon): height over the horizon and the turn from straight ahead, degrees; its strength
    private static final float[] ELEV = {20f, 10f, 3.5f, 1f, 28f, 38f};
    private static final float[] TURN = {-97f, -103f, -108f, -110f, 62f, 55f};
    private static final float[] LIGHT = {1f, 1f, 0.92f, 0f, 0.7f, 0.8f};
    // the light's colour
    private static final int[] SUN = {0xFFE8CE, 0xFFD39A, 0xFFA060, 0xFF8050, 0x9FB0D8, 0x9AAEE0};
    private static final float[] SUN_K = {1.6f, 1.65f, 1.7f, 1.2f, 0.55f, 0.6f};
    // the sky: overhead, at the horizon, toward the sun; the haze
    private static final int[] TOP = {0x3C73C0, 0x3568B4, 0x2E5296, 0x1E2E5C, 0x111E44, 0x070C22};
    private static final int[] HORIZON = {0xF1DCC4, 0xF4D2AE, 0xF4B488, 0xC47A78, 0x46507E, 0x1E2848};
    private static final int[] GLOW = {0xF6C08A, 0xF7A86A, 0xF5844A, 0xD86A50, 0x5E5A86, 0x2C3058};
    private static final int[] FOG = {0xE9D6BE, 0xEACBA6, 0xE4AE8C, 0x8A6E86, 0x343E62, 0x161C34};
    // the light from the sky and from the ground
    private static final int[] AMB_SKY = {0x59637D, 0x5A6078, 0x5A5470, 0x4E4C76, 0x46527E, 0x3E4C7A};
    private static final int[] AMB_GROUND = {0x423830, 0x463628, 0x4A3226, 0x30262E, 0x1E1E2C, 0x161824};
    // the grade: contrast, saturation, warmth
    private static final float[] CONTRAST = {1f, 1.07f, 1.14f, 1.06f, 1.04f, 1.08f};
    private static final float[] SATURATION = {1f, 1.1f, 1.24f, 1.08f, 0.92f, 0.82f};
    private static final float[] WARMTH = {0f, 0.35f, 0.75f, 0.35f, -0.15f, -0.3f};
    // the night: lamps and windows lit, stars out
    private static final float[] NIGHT = {0f, 0f, 0f, 0.3f, 0.85f, 1f};

    /** The light of the street for the level with this number on its plate. */
    static void light(DrawList d, float plate) {
        int i = 0;
        while (i < AT.length - 2 && plate >= AT[i + 1]) i++;
        float u = Math.max(0f, Math.min(1f, (plate - AT[i]) / (AT[i + 1] - AT[i])));
        u = u * u * (3f - 2f * u);
        // the sun goes down on the left and the moon comes up on the right: they meet at nothing,
        // so the light turns round unseen
        float elev = lerp(ELEV, i, u), turn = lerp(TURN, i, u), k = lerp(LIGHT, i, u);
        if (i == 3) {
            // the sun has set: nothing but the afterglow, and then the moon rising
            elev = u < 0.5f ? ELEV[3] : ELEV[4];
            turn = u < 0.5f ? TURN[3] : TURN[4];
            k = u < 0.5f ? 0f : LIGHT[4] * (u - 0.5f) * 2f;
        }
        double e = Math.toRadians(elev), t = Math.toRadians(turn);
        float dx = (float) (Math.cos(e) * Math.sin(t)), dy = (float) Math.sin(e), dz = (float) (Math.cos(e) * Math.cos(t));
        d.sun(dx, dy, dz, -1f, 4f, 42f, 100f, 36f, 140f);
        float[] sc = new float[3];
        DrawList.rgb(sc, mix(SUN, i, u));
        float sk = lerp(SUN_K, i, u) * k;
        d.sunColor[0] = sc[0] * sk;
        d.sunColor[1] = sc[1] * sk;
        d.sunColor[2] = sc[2] * sk;
        DrawList.rgb(d.skyTop, mix(TOP, i, u));
        DrawList.rgb(d.skyHorizon, mix(HORIZON, i, u));
        DrawList.rgb(d.skyGlow, mix(GLOW, i, u));
        DrawList.rgb(d.fog, mix(FOG, i, u));
        DrawList.rgb(d.ambientSky, mix(AMB_SKY, i, u));
        DrawList.rgb(d.ambientGround, mix(AMB_GROUND, i, u));
        d.grade[0] = lerp(CONTRAST, i, u);
        d.grade[1] = lerp(SATURATION, i, u);
        d.grade[2] = lerp(WARMTH, i, u);
        d.night = lerp(NIGHT, i, u);
    }

    private static float lerp(float[] a, int i, float u) {
        return a[i] + (a[i + 1] - a[i]) * u;
    }

    private static int mix(int[] a, int i, float u) {
        return Street.mix(a[i], a[i + 1], u);
    }
}
