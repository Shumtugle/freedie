package com.shumtugle.freedie;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A round written down as it went, for the author to read afterwards and to point at: when
 * the finger went down and where, where the ring was, how far the view had closed in, what
 * the throw was and what the hammer met, how much came off, what the gods said. One plain
 * text a round; the phone keeps it in a folder of its own.
 *
 * Places are given in frame pixels (the game's own coarse pixels, the frame being so many
 * across) and as shares of the screen, 0 at the left or top to 1 at the right or bottom, so
 * a place can be pointed at on any screen.
 */
final class Journal {
    private final StringBuilder s = new StringBuilder(4096);
    private float t0;
    private boolean open;
    private String name;
    int fw = World.W, fh = 640;

    /** A round begins: its name and what it is. */
    void begin(float clock, int plate, String what) {
        s.setLength(0);
        t0 = clock;
        open = true;
        Date now = new Date();
        name = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.ROOT).format(now) + "-L" + plate;
        s.append("Freedie ").append(World.VERSION).append(", round at level ").append(plate).append('\n');
        s.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(now)).append('\n');
        s.append("frame ").append(fw).append(" x ").append(fh).append(" px; places as frame px (x, y) and screen share [x, y]\n");
        s.append(what).append('\n').append('\n');
    }

    boolean open() {
        return open;
    }

    /** Something that happened, at this time into the round. */
    void line(float clock, String what) {
        if (!open) return;
        s.append(String.format(Locale.ROOT, "%7.2f  ", clock - t0)).append(what).append('\n');
    }

    /** A place on the frame, both ways. */
    String at(float x, float y) {
        return String.format(Locale.ROOT, "(%.0f, %.0f) [%.2f, %.2f]", x, y, x / fw, y / fh);
    }

    /** The round is over: its name and its text, and the journal is closed until the next. */
    String[] end(float clock, String what) {
        if (!open) return null;
        line(clock, what);
        open = false;
        return new String[]{"freedie-" + name + ".txt", s.toString()};
    }
}
