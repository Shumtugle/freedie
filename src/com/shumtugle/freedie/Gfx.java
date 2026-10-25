package com.shumtugle.freedie;

import android.content.res.AssetManager;
import android.opengl.GLES30;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * The phone's renderer: draws a DrawList with the shaders in assets, in three passes. The
 * sun's depth map; the sky and the boxes into the small frame; the small frame onto the
 * screen with hard pixels and the flat layer over it. The desktop stand's page does the
 * same passes with the same shaders, so what the stand shows is what the phone shows.
 * GL thread only.
 */
final class Gfx {
    private static final int SHADOW_SIZE = 2048;
    private static final float[] CUBE = cube();

    private int boxP, shadowP, skyP, blitP;
    private int vao, empty, cubeBuf, instBuf;
    private int shadowTex, shadowFb, sceneTex, sceneFb, depthRb, flatTex;
    private int frameW, frameH;
    private FloatBuffer inst;
    private IntBuffer flat;
    private int[] swizzled;

    /** Everything that belongs to a GL context; called again whenever the context is new. */
    void create(AssetManager assets) {
        boxP = program(read(assets, "box.vert"), read(assets, "box.frag"));
        shadowP = program(read(assets, "shadow.vert"), read(assets, "shadow.frag"));
        skyP = program(read(assets, "full.vert"), read(assets, "sky.frag"));
        blitP = program(read(assets, "full.vert"), read(assets, "blit.frag"));
        int[] ids = new int[2];
        GLES30.glGenVertexArrays(2, ids, 0);
        vao = ids[0];
        empty = ids[1];
        GLES30.glGenBuffers(2, ids, 0);
        cubeBuf = ids[0];
        instBuf = ids[1];
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, cubeBuf);
        FloatBuffer cb = floats(CUBE.length);
        cb.put(CUBE).position(0);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, CUBE.length * 4, cb, GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instBuf);
        int stride = DrawList.STRIDE * 4;
        int[][] attrs = {{2, 3, 0}, {3, 3, 3}, {4, 3, 6}, {5, 3, 9}, {6, 3, 12}, {7, 4, 15}};
        for (int[] a : attrs) {
            GLES30.glEnableVertexAttribArray(a[0]);
            GLES30.glVertexAttribPointer(a[0], a[1], GLES30.GL_FLOAT, false, stride, a[2] * 4);
            GLES30.glVertexAttribDivisor(a[0], 1);
        }
        GLES30.glBindVertexArray(0);

        // the sun's depth map, compared on lookup, smoothed by the hardware
        GLES30.glGenTextures(1, ids, 0);
        shadowTex = ids[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTex);
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_DEPTH_COMPONENT24, SHADOW_SIZE, SHADOW_SIZE);
        params(GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_COMPARE_REF_TO_TEXTURE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_FUNC, GLES30.GL_LEQUAL);
        GLES30.glGenFramebuffers(1, ids, 0);
        shadowFb = ids[0];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFb);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, shadowTex, 0);
        GLES30.glDrawBuffers(1, new int[]{GLES30.GL_NONE}, 0);
        GLES30.glReadBuffer(GLES30.GL_NONE);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        frameW = frameH = 0;
    }

    /** The small frame and the flat layer, sized to the frame the world asks for. */
    private void size(int w, int h) {
        if (w == frameW && h == frameH) return;
        int[] ids = new int[1];
        if (frameW != 0) {
            GLES30.glDeleteTextures(2, new int[]{sceneTex, flatTex}, 0);
            GLES30.glDeleteRenderbuffers(1, new int[]{depthRb}, 0);
            GLES30.glDeleteFramebuffers(1, new int[]{sceneFb}, 0);
        }
        frameW = w;
        frameH = h;
        GLES30.glGenTextures(1, ids, 0);
        sceneTex = ids[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneTex);
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, w, h);
        params(GLES30.GL_NEAREST);
        GLES30.glGenRenderbuffers(1, ids, 0);
        depthRb = ids[0];
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthRb);
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, w, h);
        GLES30.glGenFramebuffers(1, ids, 0);
        sceneFb = ids[0];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFb);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, sceneTex, 0);
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, depthRb);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glGenTextures(1, ids, 0);
        flatTex = ids[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, flatTex);
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, w, h);
        params(GLES30.GL_NEAREST);
        flat = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        swizzled = new int[w * h];
    }

    void draw(DrawList d, int screenW, int screenH) {
        size(d.w, d.h);
        int n = d.count;
        if (inst == null || inst.capacity() < n * DrawList.STRIDE) inst = floats(Math.max(n, 256) * DrawList.STRIDE * 2);
        inst.clear();
        inst.put(d.boxes, 0, n * DrawList.STRIDE).position(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instBuf);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, n * DrawList.STRIDE * 4, inst, GLES30.GL_DYNAMIC_DRAW);
        // the flat layer: ARGB in, bytes in R, G, B, A order out
        int[] src = d.flat, dst = swizzled;
        for (int i = 0; i < src.length; i++) {
            int c = src[i];
            dst[i] = (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
        }
        flat.clear();
        flat.put(dst).position(0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, flatTex);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4);
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, d.w, d.h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, flat);

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glDisable(GLES30.GL_BLEND);

        // 1. the sun's depth map
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFb);
        GLES30.glViewport(0, 0, SHADOW_SIZE, SHADOW_SIZE);
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glUseProgram(shadowP);
        GLES30.glUniformMatrix4fv(u(shadowP, "uLight"), 1, false, d.lightProj, 0);
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 36, n);

        // 2. the sky, then the boxes, into the small frame
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFb);
        GLES30.glViewport(0, 0, d.w, d.h);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glUseProgram(skyP);
        GLES30.glUniform3fv(u(skyP, "uRight"), 1, d.right, 0);
        GLES30.glUniform3fv(u(skyP, "uUp"), 1, d.up, 0);
        GLES30.glUniform3fv(u(skyP, "uAhead"), 1, d.ahead, 0);
        GLES30.glUniform2f(u(skyP, "uFrame"), d.w, d.h);
        GLES30.glUniform1f(u(skyP, "uFocal"), d.focal);
        GLES30.glUniform3fv(u(skyP, "uTop"), 1, d.skyTop, 0);
        GLES30.glUniform3fv(u(skyP, "uHorizon"), 1, d.skyHorizon, 0);
        GLES30.glUniform3fv(u(skyP, "uGlow"), 1, d.skyGlow, 0);
        GLES30.glUniform3fv(u(skyP, "uSun"), 1, d.sun, 0);
        GLES30.glUniform1f(u(skyP, "uTime"), d.time);
        GLES30.glBindVertexArray(empty);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glUseProgram(boxP);
        GLES30.glUniformMatrix4fv(u(boxP, "uViewProj"), 1, false, d.viewProj, 0);
        GLES30.glUniformMatrix4fv(u(boxP, "uLight"), 1, false, d.lightProj, 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTex);
        GLES30.glUniform1i(u(boxP, "uShadow"), 0);
        GLES30.glUniform1f(u(boxP, "uShadowTexel"), 1f / SHADOW_SIZE);
        GLES30.glUniform3fv(u(boxP, "uSun"), 1, d.sun, 0);
        GLES30.glUniform3fv(u(boxP, "uSunColor"), 1, d.sunColor, 0);
        GLES30.glUniform3fv(u(boxP, "uAmbSky"), 1, d.ambientSky, 0);
        GLES30.glUniform3fv(u(boxP, "uAmbGround"), 1, d.ambientGround, 0);
        GLES30.glUniform3fv(u(boxP, "uEye"), 1, d.eye, 0);
        GLES30.glUniform3fv(u(boxP, "uFog"), 1, d.fog, 0);
        GLES30.glUniform1f(u(boxP, "uFogNear"), d.fogNear);
        GLES30.glUniform1f(u(boxP, "uFogFar"), d.fogFar);
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 36, n);

        // 3. onto the screen
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, screenW, screenH);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glUseProgram(blitP);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneTex);
        GLES30.glUniform1i(u(blitP, "uScene"), 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, flatTex);
        GLES30.glUniform1i(u(blitP, "uFlat"), 1);
        GLES30.glBindVertexArray(empty);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
    }

    // ---- helpers ----

    private static int u(int program, String name) {
        return GLES30.glGetUniformLocation(program, name);
    }

    private static void params(int filter) {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
    }

    private static FloatBuffer floats(int n) {
        return ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private static String read(AssetManager assets, String name) {
        try (InputStream in = assets.open("shaders/" + name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int k;
            while ((k = in.read(buf)) > 0) out.write(buf, 0, k);
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new RuntimeException("shader " + name + " missing", e);
        }
    }

    private static int program(String vs, String fs) {
        int p = GLES30.glCreateProgram();
        GLES30.glAttachShader(p, shader(GLES30.GL_VERTEX_SHADER, vs));
        GLES30.glAttachShader(p, shader(GLES30.GL_FRAGMENT_SHADER, fs));
        GLES30.glLinkProgram(p);
        int[] ok = new int[1];
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("link: " + GLES30.glGetProgramInfoLog(p));
        return p;
    }

    private static int shader(int type, String src) {
        int s = GLES30.glCreateShader(type);
        GLES30.glShaderSource(s, src);
        GLES30.glCompileShader(s);
        int[] ok = new int[1];
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("compile: " + GLES30.glGetShaderInfoLog(s));
        return s;
    }

    /** The unit cube, -1..1: six faces, two triangles each; position, then normal. */
    private static float[] cube() {
        float[][][] faces = {
                {{1, 0, 0}, {1, -1, -1}, {1, 1, -1}, {1, 1, 1}, {1, -1, 1}},
                {{-1, 0, 0}, {-1, -1, 1}, {-1, 1, 1}, {-1, 1, -1}, {-1, -1, -1}},
                {{0, 1, 0}, {-1, 1, -1}, {-1, 1, 1}, {1, 1, 1}, {1, 1, -1}},
                {{0, -1, 0}, {-1, -1, 1}, {-1, -1, -1}, {1, -1, -1}, {1, -1, 1}},
                {{0, 0, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}, {-1, -1, 1}},
                {{0, 0, -1}, {-1, -1, -1}, {-1, 1, -1}, {1, 1, -1}, {1, -1, -1}}};
        int[] order = {1, 2, 3, 1, 3, 4};
        float[] out = new float[6 * 6 * 6];
        int o = 0;
        for (float[][] f : faces)
            for (int i : order) {
                out[o++] = f[i][0];
                out[o++] = f[i][1];
                out[o++] = f[i][2];
                out[o++] = f[0][0];
                out[o++] = f[0][1];
                out[o++] = f[0][2];
            }
        return out;
    }
}
