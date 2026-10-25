package com.shumtugle.freedie;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The surface: collects touches on the UI thread and hands them to the world on the GL
 * thread, which advances the world, has it fill the draw list and draws that, frame after
 * frame. The world never sees a GL call.
 */
final class Stage extends GLSurfaceView implements GLSurfaceView.Renderer {
    private final World world;
    private final DrawList list = new DrawList();
    private final Gfx gfx = new Gfx();
    private int screenW = 1, screenH = 1;
    private long last;

    // touches waiting for the GL thread: kind, x, y in frame pixels
    private final float[] pending = new float[3 * 64];
    private int pendingCount;

    Stage(Context context, World.Host host) {
        super(context);
        world = new World(host);
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int kind;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: kind = World.DOWN; break;
            case MotionEvent.ACTION_MOVE: kind = World.MOVE; break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: kind = World.UP; break;
            default: return true;
        }
        float k = World.W / (float) Math.max(1, getWidth());
        synchronized (pending) {
            if (pendingCount < 64) {
                pending[pendingCount * 3] = kind;
                pending[pendingCount * 3 + 1] = e.getX() * k;
                pending[pendingCount * 3 + 2] = e.getY() * k;
                pendingCount++;
            }
        }
        return true;
    }

    /** Back from a game on one of the bridge's screens. */
    void returned() {
        synchronized (pending) {
            if (pendingCount < 64) {
                pending[pendingCount * 3] = World.RETURN;
                pending[pendingCount * 3 + 1] = 0f;
                pending[pendingCount * 3 + 2] = 0f;
                pendingCount++;
            }
        }
    }

    /** The back key: true when the world takes it (the bridge closes), false to leave the game. */
    boolean back() {
        if (!world.wantsBack()) return false;
        synchronized (pending) {
            if (pendingCount < 64) {
                pending[pendingCount * 3] = World.BACK;
                pending[pendingCount * 3 + 1] = 0f;
                pending[pendingCount * 3 + 2] = 0f;
                pendingCount++;
            }
        }
        return true;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        gfx.create(getContext().getAssets());
        last = 0;
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
        screenW = w;
        screenH = h;
        world.resize(w, h);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        long now = System.nanoTime();
        float dt = last == 0 ? 1f / 60f : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        synchronized (pending) {
            for (int i = 0; i < pendingCount; i++)
                world.touch((int) pending[i * 3], pending[i * 3 + 1], pending[i * 3 + 2]);
            pendingCount = 0;
        }
        world.update(dt);
        world.draw(list);
        gfx.draw(list, screenW, screenH);
        // how long a frame takes, frame to frame, shown in the corner while the engine is young
        world.frameMs = world.frameMs * 0.95f + dt * 1000f * 0.05f;
    }
}
