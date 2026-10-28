package com.shumtugle.freedie;

/**
 * The game's levels gathered into episodes, by the numbers on the plate in the corner.
 * An episode opens with something new and ends where the game turns: the crossroads before
 * the 21st, the alley before the 51st and the 91st, the portal after the 101st, the stones on
 * the water after the 126th and the 151st; the others run on into the next without a seam. One table, read by the
 * game and by the bridge alike.
 */
final class Episode {
    static final int NEW = 0, ALLEY = 1, PORTAL = 2, SKIP = 3, CROSS = 4;
    static final int[] FIRST = {1, 10, 21, 40, 51, 66, 80, 91, 102, 120, 127, 140};
    static final int[] LAST = {9, 20, 39, 50, 65, 79, 90, 101, 119, 126, 139, 151};
    // how each ends: running on (a scene may come one day), or a turn the game takes there
    static final int[] ENDS = {NEW, CROSS, NEW, ALLEY, NEW, NEW, ALLEY, PORTAL, NEW, SKIP, NEW, SKIP};
    static final int COUNT = FIRST.length;
    // the first episode out at sea
    static final int SEA = 8;

    private Episode() {
    }

    /** The episode a level belongs to, by its plate number; past the last, the last. */
    static int of(int plate) {
        for (int i = 0; i < COUNT; i++) if (plate <= LAST[i]) return i;
        return COUNT - 1;
    }

    /** Whether this plate is the last of its episode. */
    static boolean last(int plate) {
        return plate == LAST[of(plate)];
    }

    static boolean first(int plate) {
        return plate == FIRST[of(plate)];
    }
}
