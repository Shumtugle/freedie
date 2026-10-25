package com.shumtugle.freedie.hopper;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;

/**
 * The game played on one of the bridge's screens, inside this one: what was its own window
 * is now a part of the window, shown while it is played. It keeps its own things apart (its
 * levels, marks and settings), plays its own sound, and goes back to the bridge when its
 * hopscotch is left or the hammer in hand, thrown at its red button, is back. Golden cubes found in
 * it, rare as they are, go into the row by the bridge.
 */
public final class Play {
    /** What the game this one is played in is told. */
    public interface Back {
        /** Off the screen and back to the bridge. Called on the UI thread. */
        void back();
        /** A golden cube found, to be kept. */
        void cube();
    }

    final Activity activity;
    private final Back home;
    private Stage stage;
    private final Vibrator vibrator;
    private final Sound sound = new Sound();
    private final SharedPreferences prefs;
    private boolean on;

    // D minor pentatonic, the same bells as the crystal's
    private static final float[] SCALE = {293.66f, 349.23f, 392.0f, 440.0f, 523.25f, 587.33f, 698.46f, 783.99f};

    public Play(Activity activity, Back home) {
        this.activity = activity;
        this.home = home;
        vibrator = (Vibrator) activity.getSystemService(Activity.VIBRATOR_SERVICE);
        prefs = activity.getSharedPreferences("hopper", Activity.MODE_PRIVATE);
        World.kept = new World.Kept() {
            @Override
            public boolean taken(int level) {
                return goldTaken(level);
            }
        };
    }

    /** The view to show; made the first time it is asked for. */
    public View view() {
        if (stage == null) {
            int level = Math.max(0, prefs.getInt("level", 0));
            // every level is grown from its number, so the number is all that needs keeping
            int reached = Math.max(level, prefs.getInt("reached", level));
            stage = new Stage(this, level, reached, prefs.getString("marks", ""));
            // a low string under everything, the same one that hums under the crystal
            sound.setCrystal(true, 2f, 0f, 0.4f, 0f);
        }
        return stage;
    }

    /** Shown: the clock runs, the sound plays. */
    public void resume() {
        view();
        on = true;
        stage.resumeClock();
        stage.onResume();
        if (soundOn()) sound.start();
    }

    /** Hidden or the window put away: everything held, the level written down. */
    public void pause() {
        if (stage == null || !on) return;
        on = false;
        stage.onPause();
        if (!dev()) onLevel(stage.level());
        sound.stop();
    }

    /** The back key, while shown. */
    public void back() {
        if (stage != null) stage.back();
    }

    /** The developer's mode: every level open, nothing won in it written down. */
    boolean dev() {
        return prefs.getBoolean("dev", false);
    }

    /** Turns the developer's mode on or off, with a run of bells up or down to say which. */
    void setDev(boolean on) {
        prefs.edit().putBoolean("dev", on).apply();
        float[] notes = {293.66f, 369.99f, 440f, 587.33f};
        for (int i = 0; i < notes.length; i++)
            sound.strikeLater(notes[on ? i : notes.length - 1 - i], 0.3f, 1.2f, i * 0.1f);
    }

    // ---- the three settings on the hopscotch ----

    boolean soundOn() {
        return prefs.getBoolean("sound", true);
    }

    void setSoundOn(boolean on) {
        prefs.edit().putBoolean("sound", on).apply();
        if (on) {
            sound.start();
            sound.strike(587.33f, 0.3f, 1.2f);
        } else {
            sound.stop();
        }
    }

    /** The first level the watcher no longer comes to: 20, or 100 for every level up to the black drop's. */
    int watcherTo() {
        return prefs.getInt("watcherTo", 100);
    }

    void setWatcherTo(int n) {
        prefs.edit().putInt("watcherTo", n).apply();
    }

    /** Easy: the game helps the finger, and a hop ordered into harm is turned aside. */
    boolean easy() {
        return prefs.getBoolean("easy", false);
    }

    void setEasy(boolean on) {
        prefs.edit().putBoolean("easy", on).apply();
    }

    /** Whether the black drop has been beaten: the game is won, and goes on. */
    boolean crowned() {
        return prefs.getBoolean("crowned", false);
    }

    void onCrowned() {
        prefs.edit().putBoolean("crowned", true).apply();
    }

    /** The level last written down: where the game was before the developer's mode. */
    int savedLevel() {
        return Math.max(0, prefs.getInt("level", 0));
    }

    /** The level now being played; written down so the game comes back to it. */
    void onLevel(int level) {
        prefs.edit().putInt("level", level).apply();
    }

    /**
     * A level won: the next one is open from now on. Who won it is written down, one character
     * a level, '1' for a finger and '2' for the watcher; a finger's win is never overwritten.
     * Returns the marks as they now stand.
     */
    String onSolved(int level, boolean watcher) {
        StringBuilder m = new StringBuilder(prefs.getString("marks", ""));
        while (m.length() <= level) m.append('0');
        if (!watcher || m.charAt(level) != '1') m.setCharAt(level, watcher ? '2' : '1');
        int reached = Math.max(prefs.getInt("reached", 0), level + 1);
        prefs.edit().putString("marks", m.toString()).putInt("reached", reached).apply();
        return m.toString();
    }

    /**
     * A new piece of the world comes up out of the black: a low bell and a fifth over it; a
     * precious piece rings on, higher, as if something were found.
     */
    void onCityGrow(boolean precious) {
        sound.strike(146.83f, 0.45f, 2.2f);
        sound.strikeLater(220f, 0.32f, 2f, 0.35f);
        sound.strikeLater(293.66f, 0.26f, 2f, 0.8f);
        if (precious) {
            sound.strikeLater(587.33f, 0.22f, 1.8f, 1.3f);
            sound.strikeLater(739.99f, 0.18f, 1.8f, 1.45f);
            sound.strikeLater(880f, 0.16f, 2f, 1.6f);
            sound.strikeLater(1174.66f, 0.1f, 2.2f, 1.8f);
        }
    }

    /** The watcher has been called: a bell, high and soft. */
    void onSummon() {
        sound.strike(587.33f, 0.3f, 1.4f);
        sound.strikeLater(880f, 0.2f, 1.2f, 0.18f);
    }

    /** Leaves the game for the bridge; safe to call from the render thread. */
    void quit() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                home.back();
            }
        });
    }

    /**
     * A golden cube taken in a level: once a level, ever; it goes to the game this one is
     * played in, and three bells ring it home.
     */
    void onGold(final int level) {
        if (prefs.getBoolean("gold" + level, false)) return;
        prefs.edit().putBoolean("gold" + level, true).apply();
        sound.strike(587.33f, 0.5f, 1.8f);
        sound.strikeLater(739.99f, 0.4f, 1.8f, 0.14f);
        sound.strikeLater(880f, 0.35f, 2.2f, 0.3f);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                home.cube();
            }
        });
    }

    /** Whether the golden cube of this level has been taken already. */
    boolean goldTaken(int level) {
        return prefs.getBoolean("gold" + level, false);
    }

    /** Called on the render thread for every event the world raised this frame. */
    void onWorldEvent(int event, World w) {
        if (event >= World.EV_RIVAL) {
            // a rival landing near: a lighter knock than the drop's own, louder the nearer it is
            int loud = event - World.EV_RIVAL;
            sound.knock(1180f, 0.03f + 0.14f * loud / 15f, 0.022f);
            return;
        }
        if (event >= World.EV_HUM) {
            // a shard humming: its note by its place in the order, soft, louder when near
            int rank = (event - World.EV_HUM) / 16, loud = (event - World.EV_HUM) % 16;
            sound.strike(SCALE[Math.min(SCALE.length - 1, rank)], 0.03f + 0.17f * loud / 15f, 0.8f);
            return;
        }
        switch (event) {
            case World.EV_GOLD:
                onGold(w.index);
                break;
            case World.EV_THROW:
                // the hammer leaves the hand: a low swish
                sound.thud(320f, 140f, 0.22f, 0.22f);
                tick();
                break;
            case World.EV_CALL:
                // the hammer strikes the red button: a knock of wood, and the box rings, as on the street
                sound.knock(620f, 0.45f, 0.035f);
                sound.knock(240f, 0.3f, 0.04f);
                sound.strikeLater(425f, 0.4f, 0.9f, 0.05f);
                sound.strikeLater(850f, 0.18f, 0.6f, 0.09f);
                thump(false);
                break;
            case World.EV_CATCH:
                // back in the hand: a solid knock of wood on the palm
                sound.knock(300f, 0.5f, 0.04f);
                sound.thud(160f, 90f, 0.3f, 0.12f);
                thump(false);
                break;
            case World.EV_DISPATCH:
                // the dispatcher answers, and the bridge comes back
                sound.strike(293.66f, 0.4f, 1.6f);
                thump(true);
                quit();
                break;
            case World.EV_LAND:
                // a knock on bismuth, its pitch nudged a little by where it lands
                sound.knock(940f + 30f * ((w.cellX * 3 + w.cellZ * 5) % 3), 0.32f, 0.03f);
                sound.knock(310f, 0.22f, 0.025f);
                tick();
                break;
            case World.EV_LAND_GLASS:
                sound.knock(2720f, 0.22f, 0.02f);
                sound.knock(1600f, 0.12f, 0.03f);
                tick();
                break;
            case World.EV_LAND_SPRING:
                sound.thud(140f, 52f, 0.5f, 0.22f);
                sound.knock(430f, 0.25f, 0.03f);
                thump(false);
                break;
            case World.EV_SHARD: {
                // each shard one step higher up the scale
                int k = w.shardsTotal - w.shardsLeft - 1;
                sound.strike(SCALE[Math.min(SCALE.length - 1, k)], 0.6f, 1.2f);
                break;
            }
            case World.EV_BEAD:
                sound.pluck(392f, 0.45f);
                sound.pluck(587.33f, 0.3f);
                break;
            case World.EV_COOL:
                sound.strike(220f, 0.5f, 1.6f);
                thump(false);
                break;
            case World.EV_OPEN:
                sound.strikeLater(293.66f, 0.5f, 1.8f, 0.25f);
                sound.strikeLater(440f, 0.45f, 1.8f, 0.45f);
                sound.strikeLater(587.33f, 0.4f, 2f, 0.65f);
                break;
            case World.EV_WIN:
                for (int i = 0; i < 5; i++) sound.strikeLater(SCALE[i + 2], 0.45f, 1.6f, i * 0.12f);
                break;
            case World.EV_DROWN:
                sound.thud(110f, 43f, 0.8f, 0.45f);
                sound.strike(73.42f, 0.7f, 2.2f);
                thump(true);
                break;
            case World.EV_BURN:
                sound.strike(77.78f, 0.9f, 1.4f);
                sound.strike(82.41f, 0.7f, 1.4f);
                thump(true);
                break;
            case World.EV_SINK:
                sound.strikeLater(110f, 0.35f, 1.2f, 0.4f);
                break;
            case World.EV_LEAP:
                sound.knock(430f, 0.25f, 0.025f);
                sound.knock(310f, 0.2f, 0.03f);
                break;
            case World.EV_QUEUE:
                // the order is heard: a tick, higher for a leap, aside for a slanted hop
                sound.knock(w.queued > 1 ? 1600f : w.slant != 0 ? 1220f : 955f, 0.12f, 0.015f);
                break;
            case World.EV_WAKE:
                // a strike on nothing: a low thud, then the bell under the water
                sound.thud(95f, 40f, 0.8f, 0.6f);
                sound.strike(73.42f, 0.6f, 2.4f);
                sound.strikeLater(293.66f, 0.3f, 1.6f, 0.9f);
                thump(true);
                break;
            case World.EV_TREMOR:
                // the ground trembles: a low knock, a shiver in the hand
                sound.thud(70f, 48f, 0.35f, 0.3f);
                tick();
                break;
            case World.EV_SHIFT:
                // everything but this island goes under
                sound.thud(90f, 36f, 0.85f, 0.7f);
                sound.strike(73.42f, 0.55f, 2.4f);
                thump(true);
                break;
            case World.EV_RISEN:
                sound.strike(293.66f, 0.3f, 1.6f);
                sound.strikeLater(440f, 0.25f, 1.4f, 0.15f);
                break;
            case World.EV_BOSS_LAND:
                // the black drop is heavy: a low thud wherever it lands
                sound.thud(70f, 34f, 0.5f, 0.35f);
                break;
            case World.EV_STRIKE:
                // it lands on the drop
                sound.knock(150f, 0.6f, 0.06f);
                sound.thud(120f, 40f, 0.9f, 0.5f);
                thump(true);
                break;
            case World.EV_RUPTURE:
                // the world is cut: a long low grinding of stone, and the phone shudders with it
                sound.thud(55f, 28f, 0.9f, 1.6f);
                sound.strike(55f, 0.55f, 2.6f);
                sound.strikeLater(58.27f, 0.45f, 2.6f, 0.25f);
                sound.strikeLater(51.91f, 0.4f, 2.8f, 0.6f);
                grind();
                break;
            case World.EV_CRUSHED:
                sound.strike(41.2f, 0.8f, 3f);
                sound.strikeLater(43.65f, 0.6f, 3f, 0.2f);
                thump(true);
                break;
            case World.EV_STOMP:
                // down on it from above: the black drop is beaten
                sound.thud(90f, 30f, 0.9f, 0.6f);
                for (int i = 0; i < 6; i++) sound.strikeLater(SCALE[Math.min(SCALE.length - 1, i + 3)], 0.5f, 2.2f, 0.2f + i * 0.14f);
                thump(true);
                break;
            case World.EV_RIVAL_READY:
                // a rival has all its shards: two bells, a tritone apart, and it makes for the gate
                sound.strike(311.13f, 0.3f, 1.4f);
                sound.strikeLater(440f, 0.25f, 1.4f, 0.22f);
                break;
            case World.EV_BEATEN:
                // a rival is through the gate first
                sound.strike(155.56f, 0.6f, 2f);
                sound.strikeLater(146.83f, 0.5f, 2.2f, 0.3f);
                sound.strikeLater(110f, 0.45f, 2.4f, 0.7f);
                thump(true);
                break;
            case World.EV_SAVED:
                // easy: a hop ordered into harm was turned aside, with a short warning tap
                sound.knock(520f, 0.22f, 0.02f);
                sound.knock(390f, 0.16f, 0.025f);
                break;
            case World.EV_ECHO:
                // the island ahead answers, softly, a moment after the landing
                sound.strikeLater(783.99f, 0.12f, 0.4f, 0.12f);
                break;
            case World.EV_FUNNEL:
                // the wrong note: the island opens under the drop and it goes down, far down
                sound.thud(330f, 38f, 0.7f, 1.6f);
                sound.strike(77.78f, 0.5f, 2.2f);
                sound.strikeLater(69.3f, 0.4f, 2.4f, 0.6f);
                thump(true);
                break;
            case World.EV_BONK:
                // the drop meets a wall too high to climb
                sound.knock(180f, 0.4f, 0.04f);
                sound.knock(260f, 0.2f, 0.03f);
                thump(false);
                break;
            case World.EV_TURN:
                sound.knock(560f, 0.1f, 0.012f);
                break;
            default:
        }
    }

    private void tick() {
        Vibrator v = vibrator;
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
        else v.vibrate(VibrationEffect.createOneShot(6, 60));
    }

    /** A long uneven shudder, as of stone grinding on stone. */
    private void grind() {
        Vibrator v = vibrator;
        if (v == null || !v.hasVibrator()) return;
        if (v.hasAmplitudeControl()) {
            long[] t = {0, 60, 30, 80, 25, 120, 40, 90, 60, 160};
            int[] a = {0, 120, 40, 180, 60, 220, 70, 150, 40, 90};
            v.vibrate(VibrationEffect.createWaveform(t, a, -1));
        } else {
            v.vibrate(VibrationEffect.createWaveform(new long[] {0, 120, 60, 200, 80, 160}, -1));
        }
    }

    private void thump(boolean strong) {
        Vibrator v = vibrator;
        if (v == null || !v.hasVibrator()) return;
        if (v.hasAmplitudeControl()) {
            long[] t = strong ? new long[] {0, 18, 26, 60} : new long[] {0, 12, 30};
            int[] a = strong ? new int[] {0, 255, 0, 80} : new int[] {0, 160, 35};
            v.vibrate(VibrationEffect.createWaveform(t, a, -1));
        } else {
            v.vibrate(VibrationEffect.createOneShot(strong ? 30 : 14, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }
}
