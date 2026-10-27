package com.shumtugle.freedie;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * The desktop stand: plays a scene file against the world on an ordinary JVM, and for each
 * shot writes the frame's draw list (name.json) and its flat layer (name.flat.png). The
 * page in stand/web draws them with the game's own shaders.
 *
 * Scene commands, one a line: screen W H · wait SECONDS · down X Y · move X Y · up X Y ·
 * drag X0 Y0 X1 Y1 SECONDS · shot NAME · skip (a touch through the opening, if it plays) ·
 * sounds (every sound of the synthesizer in turn, each after the last has died away) ·
 * keep KEY VALUE (as kept by an earlier game) · back (the phone's back key) · restart (the
 * game started again, with what it kept) · fight (the fight at once, as from the console) ·
 * bot SECONDS [SKILL] (a player in the fight for so long) · fightstate (how it stands).
 * Coordinates are frame pixels. # starts a comment. Every sound the world asks for is
 * mixed, as the phone would mix it, into sound.wav.
 */
public final class Stand implements World.Host {
    private static final float DT = 1f / 60f;
    private final java.util.ArrayList<float[]> sounds = new java.util.ArrayList<float[]>();
    private final java.util.HashMap<String, Integer> kept = new java.util.HashMap<String, Integer>();
    private World world = new World(this);
    private final DrawList list = new DrawList();
    private final File out;
    private float time;
    private String shotDown;

    private Stand(File out) {
        this.out = out;
        world.resize(1096, 2560);
    }

    public static void main(String[] args) throws IOException {
        File scene = new File(args.length > 0 ? args[0] : "stand/scene.txt");
        File out = new File(args.length > 1 ? args[1] : "out/stand");
        out.mkdirs();
        Stand s = new Stand(out);
        List<String> lines = Files.readAllLines(scene.toPath(), StandardCharsets.UTF_8);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            s.exec(line.split("\\s+"));
        }
        s.writeSound();
        System.out.println("done: " + String.format("%.1f", s.time) + "s of game time, " + s.sounds.size() + " sounds");
    }

    @Override
    public void sound(int id, float gain, float rate) {
        sounds.add(new float[]{time, id, gain, rate});
    }


    @Override
    public void buzz(int ms) {
    }

    /** The games on the screens cannot run here: they are written down, and come straight back. */
    int played = -1;

    @Override
    public void play(int game) {
        played = game;
        System.out.println(String.format("%7.2fs  plays game %d", time, game));
        world.touch(World.RETURN, 0f, 0f);
    }

    @Override
    public void saveInt(String key, int value) {
        kept.put(key, value);
    }

    @Override
    public int loadInt(String key) {
        Integer v = kept.get(key);
        return v == null ? 0 : v;
    }

    @Override
    public void journal(String name, String text) {
        try {
            Files.write(new File(out, name).toPath(), text.getBytes(StandardCharsets.UTF_8));
            System.out.println(String.format("%7.2fs  journal %s", time, name));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] asset(String name) {
        try {
            return Files.readAllBytes(new File("assets", name).toPath());
        } catch (IOException e) {
            return null;
        }
    }

    /** All the sounds of the scene, mixed as the phone would, into sound.wav. */
    private void writeSound() throws IOException {
        Synth syn = new Synth();
        int total = (int) ((time + 2.5f) * Synth.RATE);
        short[] pcm = new short[total];
        short[] chunk = new short[256];
        int si = 0;
        for (int pos = 0; pos < total; pos += chunk.length) {
            float t = pos / (float) Synth.RATE;
            while (si < sounds.size() && sounds.get(si)[0] <= t) {
                float[] e = sounds.get(si++);
                syn.play((int) e[1], e[2], e[3]);
            }
            int n = Math.min(chunk.length, total - pos);
            syn.mix(chunk, n);
            System.arraycopy(chunk, 0, pcm, pos, n);
        }
        java.io.DataOutputStream o = new java.io.DataOutputStream(new java.io.FileOutputStream(new File(out, "sound.wav")));
        int bytes = total * 2;
        o.writeBytes("RIFF");
        o.writeInt(Integer.reverseBytes(36 + bytes));
        o.writeBytes("WAVEfmt ");
        o.writeInt(Integer.reverseBytes(16));
        o.writeShort(Short.reverseBytes((short) 1));
        o.writeShort(Short.reverseBytes((short) 1));
        o.writeInt(Integer.reverseBytes(Synth.RATE));
        o.writeInt(Integer.reverseBytes(Synth.RATE * 2));
        o.writeShort(Short.reverseBytes((short) 2));
        o.writeShort(Short.reverseBytes((short) 16));
        o.writeBytes("data");
        o.writeInt(Integer.reverseBytes(bytes));
        for (short v : pcm) o.writeShort(Short.reverseBytes(v));
        o.close();
    }

    private void step() {
        world.update(DT);
        // drawn every step, as the phone does: the world learns from it where things are on screen
        world.draw(list);
        time += DT;
    }

    private void run(float seconds) {
        int n = Math.round(seconds / DT);
        for (int i = 0; i < n; i++) step();
    }

    private static float f(String s) {
        return Float.parseFloat(s);
    }

    private void exec(String[] p) throws IOException {
        String c = p[0];
        if (c.equals("screen")) {
            world.resize(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } else if (c.equals("wait")) {
            run(f(p[1]));
        } else if (c.equals("down")) {
            world.touch(World.DOWN, f(p[1]), f(p[2]));
            step();
        } else if (c.equals("move")) {
            world.touch(World.MOVE, f(p[1]), f(p[2]));
            step();
        } else if (c.equals("up")) {
            world.touch(World.UP, f(p[1]), f(p[2]));
            step();
        } else if (c.equals("drag")) {
            float x0 = f(p[1]), y0 = f(p[2]), x1 = f(p[3]), y1 = f(p[4]);
            int n = Math.max(2, Math.round(f(p[5]) / DT));
            world.touch(World.DOWN, x0, y0);
            step();
            for (int i = 1; i <= n; i++) {
                world.touch(World.MOVE, x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n);
                step();
            }
            world.touch(World.UP, x1, y1);
            step();
        } else if (c.equals("sounds")) {
            Synth syn = new Synth();
            for (int id = 0; id < syn.bank.length; id++) {
                if (syn.bank[id] == null) continue;
                sound(id, 0.9f, 1f);
                System.out.println(String.format("%7.2fs  sound %d", time, id));
                run(syn.bank[id].length / (float) Synth.RATE + 0.45f);
            }
        } else if (c.equals("keep")) {
            // keep KEY VALUE: as if a game before had kept it
            saveInt(p[1], Integer.parseInt(p[2]));
            if (p[1].equals("cubes")) world.cubes = Integer.parseInt(p[2]);
        } else if (c.equals("restart")) {
            // the game started again, with what it kept
            world = new World(this);
            world.resize(1096, 2560);
        } else if (c.equals("back")) {
            world.touch(World.BACK, 0f, 0f);
            step();
        } else if (c.equals("skip")) {
            if (world.opening()) {
                world.touch(World.DOWN, World.W * 0.5f, 300f);
                step();
                world.touch(World.UP, World.W * 0.5f, 300f);
                step();
            }
        } else if (c.equals("tear")) {
            // every mattress torn, every suitcase burst open
            world.standTear();
        } else if (c.equals("birds")) {
            // a flock across the far end of the street now
            world.standFliers(1, 0f);
            step();
        } else if (c.equals("plane")) {
            // plane SECONDS: a plane high up that has flown so long, its trail behind it
            world.standFliers(2, f(p[1]));
            step();
        } else if (c.equals("deck")) {
            // deck VERDICT: up among the watchers at once (0 laughter, 1 nods, 2 unimpressed, 3 the stars ran out)
            world.standDeck(Integer.parseInt(p[1]));
            step();
        } else if (c.equals("truck")) {
            // the truck's film between the first episode and the second, at once
            world.standTruck();
        } else if (c.equals("crane")) {
            // the crane's film between the third episode and the fourth, at once
            world.standCrane();
            step();
        } else if (c.equals("fight")) {
            // the fight at once, as from the bridge's console
            world.standFight();
            step();
        } else if (c.equals("bot")) {
            // bot SECONDS [SKILL]: a player in the fight, throwing at what matters most whenever
            // the hammer is in hand, until the fight is over; skill is the share of throws aimed
            // true, the rest go wide
            int n = Math.round(f(p[1]) / DT);
            float skill = p.length > 2 ? f(p[2]) : 1f;
            java.util.Random r = new java.util.Random(5);
            float wait = 0.6f;
            for (int i = 0; i < n && !world.standFightOver(); i++) {
                step();
                wait -= DT;
                if (wait > 0f) continue;
                float[] at = world.standFightPick();
                if (at == null || !world.standHandFree()) continue;
                float off = r.nextFloat() < skill ? 0f : 18f;
                // a finger as a player's: put down under the ring's spot, held a moment, lifted
                float fx = at[0] + (r.nextFloat() - 0.5f) * 2f * off, fy = at[1] + (r.nextFloat() - 0.5f) * 2f * off + Fight.FINGER;
                world.touch(World.DOWN, fx, fy);
                for (int k = 0; k < 6; k++) {
                    step();
                    i++;
                }
                if (shotDown != null) {
                    shot(shotDown);
                    shotDown = null;
                }
                world.touch(World.UP, fx, fy);
                wait = 0.5f + r.nextFloat() * 0.4f;
            }
            System.out.println(String.format("%7.2fs  %s", time, world.standFightState()));
        } else if (c.equals("botshot")) {
            // botshot NAME: the next throw of the bot is shot with the finger down
            shotDown = p[1];
        } else if (c.equals("fightstate")) {
            System.out.println(String.format("%7.2fs  %s", time, world.standFightState()));
        } else if (c.equals("shot")) {
            shot(p[1]);
        } else {
            throw new IllegalArgumentException("unknown command: " + c);
        }
    }

    private void shot(String name) throws IOException {
        world.draw(list);
        Files.write(new File(out, name + ".json").toPath(), list.json().getBytes(StandardCharsets.UTF_8));
        BufferedImage img = new BufferedImage(list.w, list.h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, list.w, list.h, list.flat, 0, list.w);
        ImageIO.write(img, "png", new File(out, name + ".flat.png"));
        System.out.println(String.format("%7.2fs  %s (%d boxes)", time, name, list.count));
    }
}
