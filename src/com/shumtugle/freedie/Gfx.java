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
 * screen with hard pixels and the flat layer over it. Balls go after the boxes in each pass,
 * with the same shaders built for spheres. When there is a mirror up in the sky, the sky and
 * the boxes are drawn once more before the frame, six ways round from its middle into a
 * small cube, for the mirror to show. The desktop stand's page does the
 * same passes with the same shaders, so what the stand shows is what the phone shows.
 * GL thread only.
 */
final class Gfx {
    private static final int SHADOW_SIZE = 2048;
    // what the mirror sees: six small squares, coarse as the frame itself
    private static final int CUBE_SIZE = 128;
    // the six ways round a point, as the cube's faces want them: ahead, right, up
    private static final float[][] FACES = {
            {1, 0, 0, 0, 0, -1, 0, -1, 0}, {-1, 0, 0, 0, 0, 1, 0, -1, 0},
            {0, 1, 0, 1, 0, 0, 0, 0, 1}, {0, -1, 0, 1, 0, 0, 0, 0, -1},
            {0, 0, 1, 1, 0, 0, 0, -1, 0}, {0, 0, -1, -1, 0, 0, 0, -1, 0}};
    private static final float[] CUBE = cube();

    private int boxP, shadowP, skyP, blitP, ballP, ballShadowP;
    private int vao, empty, cubeBuf, instBuf, ballVao, ballBuf;
    private int shadowTex, shadowFb, sceneTex, sceneFb, depthRb, flatTex, hudTex, hudW, hudH;
    private int cubeTex, cubeFb, cubeDepth;
    private final float[] faceProj = new float[16], faceEye = new float[3], halo = new float[4];
    private int frameW, frameH;
    private FloatBuffer inst, ballInst;
    private IntBuffer flat, hudBuf;
    private int[] hudSwizzled;
    private int[] swizzled;

    /** Everything that belongs to a GL context; called again whenever the context is new. */
    void create(AssetManager assets) {
        boxP = program(read(assets, "box.vert"), read(assets, "box.frag"));
        shadowP = program(read(assets, "shadow.vert"), read(assets, "shadow.frag"));
        skyP = program(read(assets, "full.vert"), read(assets, "sky.frag"));
        blitP = program(read(assets, "full.vert"), read(assets, "blit.frag"));
        ballP = program(sphere(read(assets, "box.vert")), sphere(read(assets, "box.frag")));
        ballShadowP = program(sphere(read(assets, "shadow.vert")), sphere(read(assets, "shadow.frag")));
        int[] ids = new int[3];
        GLES30.glGenVertexArrays(3, ids, 0);
        vao = ids[0];
        empty = ids[1];
        ballVao = ids[2];
        GLES30.glGenBuffers(3, ids, 0);
        cubeBuf = ids[0];
        instBuf = ids[1];
        ballBuf = ids[2];
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, cubeBuf);
        FloatBuffer cb = floats(CUBE.length);
        cb.put(CUBE).position(0);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, CUBE.length * 4, cb, GLES30.GL_STATIC_DRAW);
        instances(vao, instBuf);
        instances(ballVao, ballBuf);

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

        // what the mirror sees
        GLES30.glGenTextures(1, ids, 0);
        cubeTex = ids[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, cubeTex);
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_CUBE_MAP, 1, GLES30.GL_RGBA8, CUBE_SIZE, CUBE_SIZE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glGenRenderbuffers(1, ids, 0);
        cubeDepth = ids[0];
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, cubeDepth);
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, CUBE_SIZE, CUBE_SIZE);
        GLES30.glGenFramebuffers(1, ids, 0);
        cubeFb = ids[0];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, cubeFb);
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, cubeDepth);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        frameW = frameH = 0;
        hudTex = 0;
    }

    /** A vertex array: the cube's corners, and one box after another from the given buffer. */
    private void instances(int array, int buf) {
        GLES30.glBindVertexArray(array);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, cubeBuf);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buf);
        int stride = DrawList.STRIDE * 4;
        int[][] attrs = {{2, 3, 0}, {3, 3, 3}, {4, 3, 6}, {5, 3, 9}, {6, 3, 12}, {7, 4, 15}};
        for (int[] a : attrs) {
            GLES30.glEnableVertexAttribArray(a[0]);
            GLES30.glVertexAttribPointer(a[0], a[1], GLES30.GL_FLOAT, false, stride, a[2] * 4);
            GLES30.glVertexAttribDivisor(a[0], 1);
        }
        GLES30.glBindVertexArray(0);
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
        int nb = d.ballCount;
        if (nb > 0) {
            if (ballInst == null || ballInst.capacity() < nb * DrawList.STRIDE) ballInst = floats(Math.max(nb, 16) * DrawList.STRIDE * 2);
            ballInst.clear();
            ballInst.put(d.balls, 0, nb * DrawList.STRIDE).position(0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ballBuf);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, nb * DrawList.STRIDE * 4, ballInst, GLES30.GL_DYNAMIC_DRAW);
        }
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
        // the fine layer over the top, when there is one: smooth, premultiplied
        boolean newHud = hudTex == 0 || hudW != d.hudW || hudH != d.hudH;
        if (d.hudRows > 0 && d.hud != null && (d.hudFresh || newHud)) {
            if (newHud) {
                if (hudTex != 0) GLES30.glDeleteTextures(1, new int[]{hudTex}, 0);
                int[] t = new int[1];
                GLES30.glGenTextures(1, t, 0);
                hudTex = t[0];
                hudW = d.hudW;
                hudH = d.hudH;
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, hudTex);
                GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, hudW, hudH);
                params(GLES30.GL_LINEAR);
                hudBuf = ByteBuffer.allocateDirect(hudW * hudH * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
                hudSwizzled = new int[hudW * hudH];
            }
            int[] hs = d.hud, hd = hudSwizzled;
            for (int i = 0; i < hd.length; i++) {
                int c = hs[i];
                hd[i] = (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
            }
            hudBuf.clear();
            hudBuf.put(hd).position(0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, hudTex);
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4);
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, hudW, hudH, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, hudBuf);
        }

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
        if (nb > 0) {
            GLES30.glUseProgram(ballShadowP);
            GLES30.glUniformMatrix4fv(u(ballShadowP, "uLight"), 1, false, d.lightProj, 0);
            GLES30.glUniform3fv(u(ballShadowP, "uSun"), 1, d.sun, 0);
            GLES30.glBindVertexArray(ballVao);
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 36, nb);
        }

        // 2. what the mirror sees, if there is one: six ways round from its middle
        if (d.mirror[3] > 0f) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, cubeFb);
            GLES30.glViewport(0, 0, CUBE_SIZE, CUBE_SIZE);
            faceEye[0] = d.mirror[0];
            faceEye[1] = d.mirror[1];
            faceEye[2] = d.mirror[2];
            for (int f = 0; f < 6; f++) {
                GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                        GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + f, cubeTex, 0);
                float[] q = FACES[f];
                face(q, faceEye, faceProj);
                scene(d, CUBE_SIZE, CUBE_SIZE, CUBE_SIZE * 0.5f, q[3], q[4], q[5], q[6], q[7], q[8], q[0], q[1], q[2],
                        faceProj, faceEye, true, n, nb);
            }
        }

        // 3. the sky, then the boxes, into the small frame
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFb);
        GLES30.glViewport(0, 0, d.w, d.h);
        scene(d, d.w, d.h, d.focal, d.right[0], d.right[1], d.right[2], d.up[0], d.up[1], d.up[2],
                d.ahead[0], d.ahead[1], d.ahead[2], d.viewProj, d.eye, false, n, nb);

        // 4. onto the screen
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
        GLES30.glUniform1f(u(blitP, "uFlash"), d.flash);
        GLES30.glUniform1f(u(blitP, "uTube"), d.tube);
        GLES30.glUniform1f(u(blitP, "uRed"), d.red);
        GLES30.glUniform1f(u(blitP, "uGold"), d.gold);
        GLES30.glUniform1f(u(blitP, "uTime"), d.time);
        GLES30.glUniform1f(u(blitP, "uBlur"), d.blur);
        GLES30.glUniform3fv(u(blitP, "uGrade"), 1, d.grade, 0);
        GLES30.glUniform1f(u(blitP, "uBolt"), d.bolt);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, hudTex);
        GLES30.glUniform1i(u(blitP, "uHud"), 2);
        GLES30.glUniform1f(u(blitP, "uHudRows"), hudTex != 0 ? d.hudRows : 0f);
        GLES30.glBindVertexArray(empty);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
    }

    /**
     * The sky, then the boxes and the balls, into what is bound: seen from the eye given, with
     * its own three ways and focal length. Seen from the mirror's middle, the wreath round the
     * mirror and what flies are left out, and the mirror itself is not there.
     */
    private void scene(DrawList d, int w, int h, float focal, float rx, float ry, float rz, float ux, float uy, float uz,
                       float ax, float ay, float az, float[] viewProj, float[] eye, boolean inCube, int n, int nb) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glUseProgram(skyP);
        GLES30.glUniform3f(u(skyP, "uRight"), rx, ry, rz);
        GLES30.glUniform3f(u(skyP, "uUp"), ux, uy, uz);
        GLES30.glUniform3f(u(skyP, "uAhead"), ax, ay, az);
        GLES30.glUniform2f(u(skyP, "uFrame"), w, h);
        GLES30.glUniform1f(u(skyP, "uFocal"), focal);
        GLES30.glUniform3fv(u(skyP, "uTop"), 1, d.skyTop, 0);
        GLES30.glUniform3fv(u(skyP, "uHorizon"), 1, d.skyHorizon, 0);
        GLES30.glUniform3fv(u(skyP, "uGlow"), 1, d.skyGlow, 0);
        GLES30.glUniform3fv(u(skyP, "uSun"), 1, d.sun, 0);
        GLES30.glUniform1f(u(skyP, "uTime"), d.time);
        GLES30.glUniform1f(u(skyP, "uDark"), d.dark);
        GLES30.glUniform1f(u(skyP, "uOvercast"), d.overcast);
        GLES30.glUniform1f(u(skyP, "uNight"), d.night);
        GLES30.glUniform1i(u(skyP, "uFlyN"), inCube ? 0 : d.flyN);
        if (!inCube && d.flyN > 0) GLES30.glUniform4fv(u(skyP, "uFly"), d.flyN * 2, d.fly, 0);
        wreath(d, inCube);
        GLES30.glUniform4fv(u(skyP, "uHalo"), 1, halo, 0);
        GLES30.glUniform3fv(u(skyP, "uHaloK"), 1, d.halo, 0);
        GLES30.glBindVertexArray(empty);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        lit(boxP, d, viewProj, eye, inCube);
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 36, n);
        if (nb > 0) {
            lit(ballP, d, viewProj, eye, inCube);
            GLES30.glUniform1f(u(ballP, "uClear"), 0f);
            GLES30.glBindVertexArray(ballVao);
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 36, nb);
            // then clear plastic, laid over all of it: tested against the depth, not written to it
            GLES30.glUniform1f(u(ballP, "uClear"), 1f);
            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA);
            GLES30.glDepthMask(false);
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 36, nb);
            GLES30.glDepthMask(true);
            GLES30.glDisable(GLES30.GL_BLEND);
            GLES30.glUniform1f(u(ballP, "uClear"), 0f);
        }
    }

    /** Which way the mirror is from the eye and how wide it looks, for its wreath; none from inside it. */
    private void wreath(DrawList d, boolean inCube) {
        float[] m = d.mirror;
        halo[3] = 0f;
        if (inCube || m[3] <= 0f || d.halo[0] <= 0f) return;
        float x = m[0] - d.eye[0], y = m[1] - d.eye[1], z = m[2] - d.eye[2];
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        if (l <= m[3]) return;
        halo[0] = x / l;
        halo[1] = y / l;
        halo[2] = z / l;
        halo[3] = (float) Math.asin(m[3] / l);
    }

    /** One face of the cube round a point: world to clip, a quarter turn wide each way. */
    private static void face(float[] q, float[] e, float[] m) {
        float ax = q[0], ay = q[1], az = q[2], rx = q[3], ry = q[4], rz = q[5], ux = q[6], uy = q[7], uz = q[8];
        float near = 0.1f, far = 500f;
        float a = (far + near) / (far - near), b = -2f * far * near / (far - near);
        float tx = -(rx * e[0] + ry * e[1] + rz * e[2]), ty = -(ux * e[0] + uy * e[1] + uz * e[2]),
                tz = -(ax * e[0] + ay * e[1] + az * e[2]);
        m[0] = rx; m[4] = ry; m[8] = rz; m[12] = tx;
        m[1] = ux; m[5] = uy; m[9] = uz; m[13] = ty;
        m[2] = a * ax; m[6] = a * ay; m[10] = a * az; m[14] = a * tz + b;
        m[3] = ax; m[7] = ay; m[11] = az; m[15] = tz;
    }

    /** A program that draws lit boxes, made ready: the eye, the sun and its depth map, the haze, the dark. */
    private void lit(int p, DrawList d, float[] viewProj, float[] eye, boolean inCube) {
        GLES30.glUseProgram(p);
        GLES30.glUniformMatrix4fv(u(p, "uViewProj"), 1, false, viewProj, 0);
        GLES30.glUniformMatrix4fv(u(p, "uLight"), 1, false, d.lightProj, 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTex);
        GLES30.glUniform1i(u(p, "uShadow"), 0);
        GLES30.glUniform1f(u(p, "uShadowTexel"), 1f / SHADOW_SIZE);
        GLES30.glUniform3fv(u(p, "uSun"), 1, d.sun, 0);
        GLES30.glUniform3fv(u(p, "uSunColor"), 1, d.sunColor, 0);
        GLES30.glUniform3fv(u(p, "uAmbSky"), 1, d.ambientSky, 0);
        GLES30.glUniform3fv(u(p, "uAmbGround"), 1, d.ambientGround, 0);
        GLES30.glUniform3fv(u(p, "uEye"), 1, eye, 0);
        GLES30.glUniform3fv(u(p, "uFog"), 1, d.fog, 0);
        GLES30.glUniform1f(u(p, "uFogStart"), d.fogStart);
        GLES30.glUniform1f(u(p, "uFogScale"), d.fogScale);
        GLES30.glUniform1f(u(p, "uDark"), d.dark);
        GLES30.glUniform3fv(u(p, "uSpot"), 1, d.spot, 0);
        GLES30.glUniform1f(u(p, "uSpotRadius"), d.spotRadius);
        GLES30.glUniform1f(u(p, "uGoldGlow"), d.goldGlow);
        GLES30.glUniform1f(u(p, "uTime"), d.time);
        GLES30.glUniform1f(u(p, "uNight"), d.night);
        GLES30.glUniform1f(u(p, "uOvercast"), d.overcast);
        GLES30.glUniform1f(u(p, "uInCube"), inCube ? 1f : 0f);
        // the mirror reads the cube, but never while the cube is being drawn
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, inCube ? 0 : cubeTex);
        GLES30.glUniform1i(u(p, "uCube"), 1);
        GLES30.glUniform2f(u(p, "uRipple"), d.rippleAge, d.ripple);
        GLES30.glUniform1f(u(p, "uBolt"), d.bolt);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
    }

    // ---- helpers ----

    /** The same shader, built for balls. */
    private static String sphere(String src) {
        int eol = src.indexOf('\n');
        return src.substring(0, eol + 1) + "#define SPHERE\n" + src.substring(eol + 1);
    }

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
