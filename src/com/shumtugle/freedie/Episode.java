package com.shumtugle.freedie;

/**
 * The game's levels gathered into episodes, by the numbers on the plate in the corner: the
 * channels of the set on the bridge. The street runs from one into the next without a seam;
 * after the 101st the portal takes the player out onto the winter ocean.
 */
final class Episode {
    static final int[] FIRST = {1, 10, 21, 40, 51, 66, 80, 91, 102, 120, 127, 140};
    static final int[] LAST = {9, 20, 39, 50, 65, 79, 90, 101, 119, 126, 139, 151};
    static final int COUNT = FIRST.length;

    private Episode() {
    }

    /** The episode a level belongs to, by its plate number; past the last, the last. */
    static int of(int plate) {
        for (int i = 0; i < COUNT; i++) if (plate <= LAST[i]) return i;
        return COUNT - 1;
    }

    static boolean first(int plate) {
        return plate == FIRST[of(plate)];
    }
}
