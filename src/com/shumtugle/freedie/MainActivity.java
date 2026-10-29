package com.shumtugle.freedie;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * The window: full screen, no bars, the screen kept on; the stage fills it; the sound plays
 * while it is shown. Answers the world's requests to the phone.
 */
public final class MainActivity extends Activity implements World.Host {
    private Stage stage;
    private Vibrator vibrator;
    private final Sound sound = new Sound();

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
        stage.onResume();
        sound.start();
    }

    @Override
    protected void onPause() {
        sound.stop();
        stage.onPause();
        super.onPause();
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
        if (!stage.back()) super.onBackPressed();
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

    /**
     * A round written down: a plain text in Documents/Freedie, where any file manager finds it;
     * on old systems, in the app's own folder. Written off the game's thread.
     */
    @Override
    public void journal(final String name, final String text) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] b = text.getBytes("UTF-8");
                    if (Build.VERSION.SDK_INT >= 29) {
                        android.content.ContentValues v = new android.content.ContentValues();
                        v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                        v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                        v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                                android.os.Environment.DIRECTORY_DOCUMENTS + "/Freedie");
                        android.net.Uri u = getContentResolver().insert(android.provider.MediaStore.Files.getContentUri("external"), v);
                        if (u == null) return;
                        java.io.OutputStream o = getContentResolver().openOutputStream(u);
                        if (o == null) return;
                        o.write(b);
                        o.close();
                    } else {
                        java.io.File dir = new java.io.File(getExternalFilesDir(null), "Freedie");
                        if (!dir.isDirectory() && !dir.mkdirs()) return;
                        java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(dir, name));
                        o.write(b);
                        o.close();
                    }
                } catch (Exception e) {
                    // a round not written down is no reason to stop the game
                }
            }
        }).start();
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
