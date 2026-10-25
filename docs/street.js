// The street, drawn live in the page by the game's own shaders, in the game's own passes:
// the sun's depth map, the sky and the boxes into a frame 274 pixels across, then that
// frame onto the canvas with hard pixels. The boxes are a frame the game wrote on the
// desktop; a finger dragged across turns the eye, as in the game, and it swings back.
'use strict';

(function () {
  const W = 274;
  const STRIDE = 19;
  const SHADOW_SIZE = 2048;
  const CAM = [-2.3, 1.8, 0], PLINTH_Z = 11.5;
  const CUBE = (() => {
    const f = [
      [[1, 0, 0], [[1, -1, -1], [1, 1, -1], [1, 1, 1], [1, -1, 1]]],
      [[-1, 0, 0], [[-1, -1, 1], [-1, 1, 1], [-1, 1, -1], [-1, -1, -1]]],
      [[0, 1, 0], [[-1, 1, -1], [-1, 1, 1], [1, 1, 1], [1, 1, -1]]],
      [[0, -1, 0], [[-1, -1, 1], [-1, -1, -1], [1, -1, -1], [1, -1, 1]]],
      [[0, 0, 1], [[1, -1, 1], [1, 1, 1], [-1, 1, 1], [-1, -1, 1]]],
      [[0, 0, -1], [[-1, -1, -1], [-1, 1, -1], [1, 1, -1], [1, -1, -1]]]];
    const out = [];
    for (const [n, q] of f) for (const i of [0, 1, 2, 0, 2, 3]) out.push(...q[i], ...n);
    return new Float32Array(out);
  })();

  function program(gl, vs, fs) {
    const p = gl.createProgram();
    for (const [type, src] of [[gl.VERTEX_SHADER, vs], [gl.FRAGMENT_SHADER, fs]]) {
      const s = gl.createShader(type);
      gl.shaderSource(s, src);
      gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
      gl.attachShader(p, s);
    }
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p));
    return p;
  }

  // the eye, as the game sets it: where it stands, its yaw and pitch, the focal length
  function camera(f, w, h, yaw) {
    const focal = w * 1.9, near = 0.1, far = 500, pitch = 0;
    const cp = Math.cos(pitch);
    const fx = Math.sin(yaw) * cp, fy = Math.sin(pitch), fz = Math.cos(yaw) * cp;
    const rl = Math.hypot(fz, fx);
    const rx = fz / rl, ry = 0, rz = -fx / rl;
    const ux = fy * rz - fz * ry, uy = fz * rx - fx * rz, uz = fx * ry - fy * rx;
    const [x, y, z] = CAM;
    const sx = 2 * focal / w, sy = 2 * focal / h;
    const a = (far + near) / (far - near), b = -2 * far * near / (far - near);
    const tx = -(rx * x + ry * y + rz * z), ty = -(ux * x + uy * y + uz * z), tz = -(fx * x + fy * y + fz * z);
    const m = new Float32Array(16);
    m[0] = sx * rx; m[4] = sx * ry; m[8] = sx * rz; m[12] = sx * tx;
    m[1] = sy * ux; m[5] = sy * uy; m[9] = sy * uz; m[13] = sy * ty;
    m[2] = a * fx; m[6] = a * fy; m[10] = a * fz; m[14] = a * tz + b;
    m[3] = fx; m[7] = fy; m[11] = fz; m[15] = tz;
    return { m, right: [rx, ry, rz], up: [ux, uy, uz], ahead: [fx, fy, fz], focal };
  }

  async function start(canvas, base) {
    const gl = canvas.getContext('webgl2', { antialias: false });
    if (!gl) throw new Error('no webgl2');
    const [frame, data] = await Promise.all([
      fetch(base + 'street.json').then(r => r.json()),
      fetch(base + 'street.bin').then(r => r.arrayBuffer())]);
    const boxes = new Float32Array(data);
    const src = {};
    await Promise.all(['box.vert', 'box.frag', 'shadow.vert', 'shadow.frag', 'full.vert', 'sky.frag', 'blit.frag']
      .map(n => fetch(base + 'shaders/' + n).then(r => r.text()).then(t => { src[n] = t; })));
    const boxP = program(gl, src['box.vert'], src['box.frag']);
    const shadowP = program(gl, src['shadow.vert'], src['shadow.frag']);
    const skyP = program(gl, src['full.vert'], src['sky.frag']);
    const blitP = program(gl, src['full.vert'], src['blit.frag']);
    const u = (p, n) => gl.getUniformLocation(p, n);

    const vao = gl.createVertexArray();
    gl.bindVertexArray(vao);
    gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
    gl.bufferData(gl.ARRAY_BUFFER, CUBE, gl.STATIC_DRAW);
    gl.enableVertexAttribArray(0);
    gl.vertexAttribPointer(0, 3, gl.FLOAT, false, 24, 0);
    gl.enableVertexAttribArray(1);
    gl.vertexAttribPointer(1, 3, gl.FLOAT, false, 24, 12);
    const inst = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, inst);
    gl.bufferData(gl.ARRAY_BUFFER, boxes, gl.DYNAMIC_DRAW);
    for (const [loc, size, off] of [[2, 3, 0], [3, 3, 3], [4, 3, 6], [5, 3, 9], [6, 3, 12], [7, 4, 15]]) {
      gl.enableVertexAttribArray(loc);
      gl.vertexAttribPointer(loc, size, gl.FLOAT, false, STRIDE * 4, off * 4);
      gl.vertexAttribDivisor(loc, 1);
    }
    const empty = gl.createVertexArray();

    const shadowTex = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, shadowTex);
    gl.texStorage2D(gl.TEXTURE_2D, 1, gl.DEPTH_COMPONENT24, SHADOW_SIZE, SHADOW_SIZE);
    for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, gl.LINEAR], [gl.TEXTURE_MAG_FILTER, gl.LINEAR],
      [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE],
      [gl.TEXTURE_COMPARE_MODE, gl.COMPARE_REF_TO_TEXTURE], [gl.TEXTURE_COMPARE_FUNC, gl.LEQUAL]])
      gl.texParameteri(gl.TEXTURE_2D, k, v);
    const shadowFb = gl.createFramebuffer();
    gl.bindFramebuffer(gl.FRAMEBUFFER, shadowFb);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.TEXTURE_2D, shadowTex, 0);
    gl.drawBuffers([gl.NONE]);
    gl.readBuffer(gl.NONE);

    // the small frame and an empty flat layer, sized to the canvas's shape
    let H = 0, sceneTex, sceneFb, depth, flatTex;
    function size() {
      const cw = canvas.clientWidth, ch = canvas.clientHeight;
      const h = Math.max(200, Math.round(W * ch / Math.max(1, cw)));
      const scale = Math.max(1, Math.round(cw * (window.devicePixelRatio || 1) / W));
      if (canvas.width !== W * scale || canvas.height !== h * scale) {
        canvas.width = W * scale;
        canvas.height = h * scale;
      }
      if (h === H) return;
      H = h;
      if (sceneTex) { gl.deleteTexture(sceneTex); gl.deleteTexture(flatTex); gl.deleteRenderbuffer(depth); gl.deleteFramebuffer(sceneFb); }
      sceneTex = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, sceneTex);
      gl.texStorage2D(gl.TEXTURE_2D, 1, gl.RGBA8, W, H);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
      depth = gl.createRenderbuffer();
      gl.bindRenderbuffer(gl.RENDERBUFFER, depth);
      gl.renderbufferStorage(gl.RENDERBUFFER, gl.DEPTH_COMPONENT24, W, H);
      sceneFb = gl.createFramebuffer();
      gl.bindFramebuffer(gl.FRAMEBUFFER, sceneFb);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, sceneTex, 0);
      gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.RENDERBUFFER, depth);
      flatTex = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, flatTex);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, W, H, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array(W * H * 4));
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
    }

    // the finger: dragged across it turns the eye, let go it swings back
    let look = 0, lookVel = 0, down = false, lastX = 0;
    canvas.addEventListener('pointerdown', e => { down = true; lastX = e.clientX; lookVel = 0; canvas.setPointerCapture(e.pointerId); });
    canvas.addEventListener('pointermove', e => {
      if (!down) return;
      const dx = (e.clientX - lastX) * W / canvas.clientWidth;
      look = Math.max(-0.7, Math.min(0.7, look - dx * 0.006));
      lastX = e.clientX;
    });
    const up = () => { down = false; };
    canvas.addEventListener('pointerup', up);
    canvas.addEventListener('pointercancel', up);

    const cube = frame.cube * STRIDE, button = frame.button * STRIDE;
    const two = new Float32Array(STRIDE);
    let time = frame.time, last = performance.now();
    function draw(now) {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      time += dt;
      if (!down) {
        lookVel += -look * 6 * dt;
        lookVel *= Math.exp(-4 * dt);
        look += lookVel * dt;
      }
      size();
      // the golden cube turning, the button glowing, as the game moves them
      const a = time * 0.8, c = Math.cos(a), s = Math.sin(a);
      two.set(boxes.subarray(cube, cube + STRIDE));
      two[6] = c; two[7] = 0; two[8] = -s; two[9] = 0; two[10] = 1; two[11] = 0; two[12] = s; two[13] = 0; two[14] = c;
      gl.bindBuffer(gl.ARRAY_BUFFER, inst);
      gl.bufferSubData(gl.ARRAY_BUFFER, cube * 4, two);
      const glow = 0.45 + 0.25 * Math.sin(time * 3.1);
      two.set(boxes.subarray(button, button + STRIDE));
      two[15] = (0x6A + (0xFF - 0x6A) * glow) / 255; two[16] = (0x0C + (0x2A - 0x0C) * glow) / 255; two[17] = (0x08 + (0x18 - 0x08) * glow) / 255;
      gl.bufferSubData(gl.ARRAY_BUFFER, button * 4, two);

      const cam = camera(frame, W, H, Math.atan2(-CAM[0], PLINTH_Z) + look);
      gl.enable(gl.DEPTH_TEST);
      gl.disable(gl.CULL_FACE);
      gl.bindFramebuffer(gl.FRAMEBUFFER, shadowFb);
      gl.viewport(0, 0, SHADOW_SIZE, SHADOW_SIZE);
      gl.clear(gl.DEPTH_BUFFER_BIT);
      gl.useProgram(shadowP);
      gl.uniformMatrix4fv(u(shadowP, 'uLight'), false, frame.lightProj);
      gl.bindVertexArray(vao);
      gl.drawArraysInstanced(gl.TRIANGLES, 0, 36, frame.count);

      gl.bindFramebuffer(gl.FRAMEBUFFER, sceneFb);
      gl.viewport(0, 0, W, H);
      gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
      gl.disable(gl.DEPTH_TEST);
      gl.useProgram(skyP);
      gl.uniform3fv(u(skyP, 'uRight'), cam.right);
      gl.uniform3fv(u(skyP, 'uUp'), cam.up);
      gl.uniform3fv(u(skyP, 'uAhead'), cam.ahead);
      gl.uniform2f(u(skyP, 'uFrame'), W, H);
      gl.uniform1f(u(skyP, 'uFocal'), cam.focal);
      gl.uniform3fv(u(skyP, 'uTop'), frame.skyTop);
      gl.uniform3fv(u(skyP, 'uHorizon'), frame.skyHorizon);
      gl.uniform3fv(u(skyP, 'uGlow'), frame.skyGlow);
      gl.uniform3fv(u(skyP, 'uSun'), frame.sun);
      gl.uniform1f(u(skyP, 'uTime'), time);
      gl.uniform1f(u(skyP, 'uDark'), 0);
      gl.bindVertexArray(empty);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      gl.enable(gl.DEPTH_TEST);
      gl.useProgram(boxP);
      gl.uniformMatrix4fv(u(boxP, 'uViewProj'), false, cam.m);
      gl.uniformMatrix4fv(u(boxP, 'uLight'), false, frame.lightProj);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, shadowTex);
      gl.uniform1i(u(boxP, 'uShadow'), 0);
      gl.uniform1f(u(boxP, 'uShadowTexel'), 1 / SHADOW_SIZE);
      gl.uniform3fv(u(boxP, 'uSun'), frame.sun);
      gl.uniform3fv(u(boxP, 'uSunColor'), frame.sunColor);
      gl.uniform3fv(u(boxP, 'uAmbSky'), frame.ambientSky);
      gl.uniform3fv(u(boxP, 'uAmbGround'), frame.ambientGround);
      gl.uniform3fv(u(boxP, 'uEye'), CAM);
      gl.uniform3fv(u(boxP, 'uFog'), frame.fog);
      gl.uniform1f(u(boxP, 'uFogStart'), frame.fogStart);
      gl.uniform1f(u(boxP, 'uFogScale'), frame.fogScale);
      gl.uniform1f(u(boxP, 'uDark'), 0);
      gl.uniform3fv(u(boxP, 'uSpot'), [0, 0, 0]);
      gl.uniform1f(u(boxP, 'uSpotRadius'), 4);
      gl.uniform1f(u(boxP, 'uGoldGlow'), 1);
      gl.bindVertexArray(vao);
      gl.drawArraysInstanced(gl.TRIANGLES, 0, 36, frame.count);

      gl.bindFramebuffer(gl.FRAMEBUFFER, null);
      gl.viewport(0, 0, canvas.width, canvas.height);
      gl.disable(gl.DEPTH_TEST);
      gl.useProgram(blitP);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, sceneTex);
      gl.uniform1i(u(blitP, 'uScene'), 0);
      gl.activeTexture(gl.TEXTURE1);
      gl.bindTexture(gl.TEXTURE_2D, flatTex);
      gl.uniform1i(u(blitP, 'uFlat'), 1);
      gl.uniform1f(u(blitP, 'uFlash'), 0);
      gl.uniform1f(u(blitP, 'uTube'), -1);
      gl.bindVertexArray(empty);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      gl.activeTexture(gl.TEXTURE0);
      requestAnimationFrame(draw);
    }
    requestAnimationFrame(draw);
  }

  window.freedieStreet = start;
})();
