package com.shumtugle.freedie;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.shumtugle.freedie.hopper.Play;

/**
 * The window: full screen, no bars, the screen kept on; the stage fills it; the sound plays
 * while it is shown. Answers the world's requests to the phone.
 */
public final class MainActivity extends Activity implements World.Host {
    private Stage stage;
    private Vibrator vibrator;
    private final Sound sound = new Sound();
    // the game on the bridge's first screen, made when it is first played; shown instead of the stage
    private Play game;
    private boolean inGame;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        stage = new Stage(this, this);
        setContentView(stage);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
        if (inGame) {
            game.resume();
            return;
        }
        stage.onResume();
        sound.start();
    }

    @Override
    protected void onPause() {
        if (inGame) game.pause();
        else {
            sound.stop();
            stage.onPause();
        }
        super.onPause();
    }

    @Override
    public void play(int which) {
        if (which != 0) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                enterGame();
            }
        });
    }

    private void enterGame() {
        if (inGame) return;
        if (game == null) {
            game = new Play(this, new Play.Back() {
                @Override
                public void back() {
                    leaveGame();
                }

                @Override
                public void cube() {
                    saveInt("cubes", loadInt("cubes") + 1);
                }
            });
        }
        inGame = true;
        sound.stop();
        stage.onPause();
        setContentView(game.view());
        game.resume();
        hideBars();
    }

    private void leaveGame() {
        if (!inGame) return;
        inGame = false;
        game.pause();
        setContentView(stage);
        stage.onResume();
        sound.start();
        stage.returned();
        hideBars();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideBars();
    }

    @SuppressWarnings("deprecation")
    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (inGame) game.back();
        else if (!stage.back()) super.onBackPressed();
    }

    @Override
    public void sound(int id, float gain, float rate) {
        sound.synth.play(id, gain, rate);
    }

    @Override
    public void buzz(int ms) {
        if (vibrator != null && vibrator.hasVibrator() && ms > 0)
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    @Override
    public void saveInt(String key, int value) {
        getSharedPreferences("game", MODE_PRIVATE).edit().putInt(key, value).apply();
    }

    @Override
    public int loadInt(String key) {
        return getSharedPreferences("game", MODE_PRIVATE).getInt(key, 0);
    }

    @Override
    public byte[] asset(String name) {
        try (java.io.InputStream in = getAssets().open(name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int k;
            while ((k = in.read(buf)) > 0) out.write(buf, 0, k);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            return null;
        }
    }
}
