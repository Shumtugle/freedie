// The desktop stand's renderer: draws a frame the world wrote out (its draw list as JSON and
// its flat layer as a picture) with the same shaders and in the same passes as the phone:
// the sun's depth map, the sky and the boxes into the small frame, then the small frame onto
// the screen with hard pixels and the flat layer over it. Balls go after the boxes in each
// pass, with the same shaders built for spheres. A mirror up in the sky gets what it sees
// drawn first, six ways round from its middle into a small cube.
'use strict';

const CUBE = (() => {
  // the unit cube, -1..1: six faces, two triangles each, position and normal
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

const SHADOW_SIZE = 2048;
const CUBE_SIZE = 128;
// the six ways round a point, as the cube's faces want them: ahead, right, up
const FACES = [
  [1, 0, 0, 0, 0, -1, 0, -1, 0], [-1, 0, 0, 0, 0, 1, 0, -1, 0],
  [0, 1, 0, 1, 0, 0, 0, 0, 1], [0, -1, 0, 1, 0, 0, 0, 0, -1],
  [0, 0, 1, 1, 0, 0, 0, -1, 0], [0, 0, -1, -1, 0, 0, 0, -1, 0]];

// one face of the cube round a point: world to clip, a quarter turn wide each way
function face(q, e) {
  const [ax, ay, az, rx, ry, rz, ux, uy, uz] = q;
  const near = 0.1, far = 500, a = (far + near) / (far - near), b = -2 * far * near / (far - near);
  const tx = -(rx * e[0] + ry * e[1] + rz * e[2]), ty = -(ux * e[0] + uy * e[1] + uz * e[2]), tz = -(ax * e[0] + ay * e[1] + az * e[2]);
  return new Float32Array([rx, ux, a * ax, ax, ry, uy, a * ay, ay, rz, uz, a * az, az, tx, ty, a * tz + b, tz]);
}

async function text(url) {
  const r = await fetch(url);
  if (!r.ok) throw new Error('cannot load ' + url);
  return r.text();
}

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

async function render(canvas, frame, flatImage, scale, shaderDir) {
  const W = frame.w, H = frame.h;
  canvas.width = W * scale;
  canvas.height = H * scale;
  const gl = canvas.getContext('webgl2', { antialias: false, preserveDrawingBuffer: true });
  if (!gl) throw new Error('no webgl2');
  const src = {};
  for (const n of ['box.vert', 'box.frag', 'shadow.vert', 'shadow.frag', 'full.vert', 'sky.frag', 'blit.frag'])
    src[n] = await text(shaderDir + n);
  const boxP = program(gl, src['box.vert'], src['box.frag']);
  const shadowP = program(gl, src['shadow.vert'], src['shadow.frag']);
  const skyP = program(gl, src['full.vert'], src['sky.frag']);
  const blitP = program(gl, src['full.vert'], src['blit.frag']);
  // the same shader, built for balls
  const sphere = t => t.replace('\n', '\n#define SPHERE\n');
  const ballP = program(gl, sphere(src['box.vert']), sphere(src['box.frag']));
  const ballShadowP = program(gl, sphere(src['shadow.vert']), sphere(src['shadow.frag']));

  // the cube and the instances: the boxes, and the balls apart
  const cube = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, cube);
  gl.bufferData(gl.ARRAY_BUFFER, CUBE, gl.STATIC_DRAW);
  const instances = data => {
    const a = gl.createVertexArray();
    gl.bindVertexArray(a);
    gl.bindBuffer(gl.ARRAY_BUFFER, cube);
    gl.enableVertexAttribArray(0);
    gl.vertexAttribPointer(0, 3, gl.FLOAT, false, 24, 0);
    gl.enableVertexAttribArray(1);
    gl.vertexAttribPointer(1, 3, gl.FLOAT, false, 24, 12);
    const inst = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, inst);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(data.length ? data : [0]), gl.STATIC_DRAW);
    const stride = 19 * 4;
    for (const [loc, size, off] of [[2, 3, 0], [3, 3, 3], [4, 3, 6], [5, 3, 9], [6, 3, 12], [7, 4, 15]]) {
      gl.enableVertexAttribArray(loc);
      gl.vertexAttribPointer(loc, size, gl.FLOAT, false, stride, off * 4);
      gl.vertexAttribDivisor(loc, 1);
    }
    return a;
  };
  const vao = instances(frame.boxes);
  const ballVao = instances(frame.balls || []);
  const nb = frame.ballCount || 0;
  const empty = gl.createVertexArray();

  // the sun's depth map
  const shadowTex = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, shadowTex);
  gl.texStorage2D(gl.TEXTURE_2D, 1, gl.DEPTH_COMPONENT24, SHADOW_SIZE, SHADOW_SIZE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_COMPARE_MODE, gl.COMPARE_REF_TO_TEXTURE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_COMPARE_FUNC, gl.LEQUAL);
  const shadowFb = gl.createFramebuffer();
  gl.bindFramebuffer(gl.FRAMEBUFFER, shadowFb);
  gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.TEXTURE_2D, shadowTex, 0);
  gl.drawBuffers([gl.NONE]);
  gl.readBuffer(gl.NONE);

  // the small frame
  const sceneTex = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, sceneTex);
  gl.texStorage2D(gl.TEXTURE_2D, 1, gl.RGBA8, W, H);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  const depth = gl.createRenderbuffer();
  gl.bindRenderbuffer(gl.RENDERBUFFER, depth);
  gl.renderbufferStorage(gl.RENDERBUFFER, gl.DEPTH_COMPONENT24, W, H);
  const sceneFb = gl.createFramebuffer();
  gl.bindFramebuffer(gl.FRAMEBUFFER, sceneFb);
  gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, sceneTex, 0);
  gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.RENDERBUFFER, depth);

  // the flat layer: row by row from the top, as the phone uploads it
  const flatTex = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, flatTex);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
  gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, flatImage);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);

  // what the mirror sees
  const cubeTex = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_CUBE_MAP, cubeTex);
  gl.texStorage2D(gl.TEXTURE_CUBE_MAP, 1, gl.RGBA8, CUBE_SIZE, CUBE_SIZE);
  for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, gl.LINEAR], [gl.TEXTURE_MAG_FILTER, gl.LINEAR],
    [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_R, gl.CLAMP_TO_EDGE]])
    gl.texParameteri(gl.TEXTURE_CUBE_MAP, k, v);
  const cubeDepth = gl.createRenderbuffer();
  gl.bindRenderbuffer(gl.RENDERBUFFER, cubeDepth);
  gl.renderbufferStorage(gl.RENDERBUFFER, gl.DEPTH_COMPONENT24, CUBE_SIZE, CUBE_SIZE);
  const cubeFb = gl.createFramebuffer();
  gl.bindFramebuffer(gl.FRAMEBUFFER, cubeFb);
  gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.RENDERBUFFER, cubeDepth);

  const u = (p, n) => gl.getUniformLocation(p, n);
  gl.enable(gl.DEPTH_TEST);
  gl.disable(gl.CULL_FACE);

  // 1. the sun's depth map
  gl.bindFramebuffer(gl.FRAMEBUFFER, shadowFb);
  gl.viewport(0, 0, SHADOW_SIZE, SHADOW_SIZE);
  gl.clear(gl.DEPTH_BUFFER_BIT);
  gl.useProgram(shadowP);
  gl.uniformMatrix4fv(u(shadowP, 'uLight'), false, frame.lightProj);
  gl.bindVertexArray(vao);
  gl.drawArraysInstanced(gl.TRIANGLES, 0, 36, frame.count);
  if (nb > 0) {
    gl.useProgram(ballShadowP);
    gl.uniformMatrix4fv(u(ballShadowP, 'uLight'), false, frame.lightProj);
    gl.uniform3fv(u(ballShadowP, 'uSun'), frame.sun);
    gl.bindVertexArray(ballVao);
    gl.drawArraysInstanced(gl.TRIANGLES, 0, 36, nb);
  }

  const lit = (p, viewProj, eye, inCube) => {
    gl.useProgram(p);
    gl.uniformMatrix4fv(u(p, 'uViewProj'), false, viewProj);
    gl.uniformMatrix4fv(u(p, 'uLight'), false, frame.lightProj);
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, shadowTex);
    gl.uniform1i(u(p, 'uShadow'), 0);
    gl.uniform1f(u(p, 'uShadowTexel'), 1 / SHADOW_SIZE);
    gl.uniform3fv(u(p, 'uSun'), frame.sun);
    gl.uniform3fv(u(p, 'uSunColor'), frame.sunColor);
    gl.uniform3fv(u(p, 'uAmbSky'), frame.ambientSky);
    gl.uniform3fv(u(p, 'uAmbGround'), frame.ambientGround);
    gl.uniform3fv(u(p, 'uEye'), eye);
    gl.uniform3fv(u(p, 'uFog'), frame.fog);
    gl.uniform1f(u(p, 'uFogStart'), frame.fogStart);
    gl.uniform1f(u(p, 'uFogScale'), frame.fogScale);
    gl.uniform1f(u(p, 'uDark'), frame.dark || 0);
    gl.uniform3fv(u(p, 'uSpot'), frame.spot);
    gl.uniform1f(u(p, 'uSpotRadius'), frame.spotRadius);
    gl.uniform1f(u(p, 'uGoldGlow'), frame.goldGlow);
    gl.uniform1f(u(p, 'uTime'), frame.time || 0);
    gl.uniform1f(u(p, 'uNight'), frame.night || 0);
    gl.uniform1f(u(p, 'uOvercast'), frame.overcast || 0);
    gl.uniform1f(u(p, 'uInCube'), inCube ? 1 : 0);
    // the mirror reads the cube, but never while the cube is being drawn
    gl.activeTexture(gl.TEXTURE1);
    gl.bindTexture(gl.TEXTURE_CUBE_MAP, inCube ? null : cubeTex);
    gl.uniform1i(u(p, 'uCube'), 1);
    gl.uniform2f(u(p, 'uRipple'), frame.rippleAge || 0, frame.ripple || 0);
    gl.uniform1f(u(p, 'uBolt'), frame.bolt || 0);
    gl.activeTexture(gl.TEXTURE0);
  };
  const mirror = frame.mirror || [0, 0, 0, 0];
  // which way the mirror is from the eye and how wide it looks, for its wreath; none from inside it
  const wreath = inCube => {
    const h = [0, 0, 0, 0];
    const k = frame.halo || [0, 0, 0];
    if (inCube || mirror[3] <= 0 || k[0] <= 0) return h;
    const x = mirror[0] - frame.eye[0], y = mirror[1] - frame.eye[1], z = mirror[2] - frame.eye[2];
    const l = Math.hypot(x, y, z);
    if (l <= mirror[3]) return h;
    return [x / l, y / l, z / l, Math.asin(mirror[3] / l)];
  };
  // the sky, then the boxes and the balls, into what is bound, seen from the eye given
  const scene = (w, h, focal, right, up, ahead, viewProj, eye, inCube) => {
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    gl.disable(gl.DEPTH_TEST);
    gl.useProgram(skyP);
    gl.uniform3fv(u(skyP, 'uRight'), right);
    gl.uniform3fv(u(skyP, 'uUp'), up);
    gl.uniform3fv(u(skyP, 'uAhead'), ahead);
    gl.uniform2f(u(skyP, 'uFrame'), w, h);
    gl.uniform1f(u(skyP, 'uFocal'), focal);
    gl.uniform3fv(u(skyP, 'uTop'), frame.skyTop);
    gl.uniform3fv(u(skyP, 'uHorizon'), frame.skyHorizon);
    gl.uniform3fv(u(skyP, 'uGlow'), frame.skyGlow);
    gl.uniform3fv(u(skyP, 'uSun'), frame.sun);
    gl.uniform1f(u(skyP, 'uTime'), frame.time || 0);
    gl.uniform1f(u(skyP, 'uDark'), frame.dark || 0);
    gl.uniform1f(u(skyP, 'uOvercast'), frame.overcast || 0);
    gl.uniform1f(u(skyP, 'uNight'), frame.night || 0);
    const flyN = inCube ? 0 : (frame.flyN || 0);
    gl.uniform1i(u(skyP, 'uFlyN'), flyN);
    if (flyN > 0) gl.uniform4fv(u(skyP, 'uFly'), new Float32Array(frame.fly));
    gl.uniform4fv(u(skyP, 'uHalo'), wreath(inCube));
    gl.uniform3fv(u(skyP, 'uHaloK'), frame.halo || [0, 0, 0]);
    gl.bindVertexArray(empty);
    gl.drawArrays(gl.TRIANGLES, 0, 3);
    gl.enable(gl.DEPTH_TEST);
    lit(boxP, viewProj, eye, inCube);
    gl.bindVertexArray(vao);
    gl.drawArraysInstanced(gl.TRIANGLES, 0, 36, frame.count);
    if (nb > 0) {
      lit(ballP, viewProj, eye, inCube);
      gl.bindVertexArray(ballVao);
      gl.drawArraysInstanced(gl.TRIANGLES, 0, 36, nb);
    }
  };

  // 2. what the mirror sees, if there is one: six ways round from its middle
  if (mirror[3] > 0) {
    gl.bindFramebuffer(gl.FRAMEBUFFER, cubeFb);
    gl.viewport(0, 0, CUBE_SIZE, CUBE_SIZE);
    const e = [mirror[0], mirror[1], mirror[2]];
    for (let f = 0; f < 6; f++) {
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_CUBE_MAP_POSITIVE_X + f, cubeTex, 0);
      const q = FACES[f];
      scene(CUBE_SIZE, CUBE_SIZE, CUBE_SIZE * 0.5, q.slice(3, 6), q.slice(6, 9), q.slice(0, 3), face(q, e), e, true);
    }
  }

  // 3. the sky, then the boxes, into the small frame
  gl.bindFramebuffer(gl.FRAMEBUFFER, sceneFb);
  gl.viewport(0, 0, W, H);
  scene(W, H, frame.focal, frame.right, frame.up, frame.ahead, frame.viewProj, frame.eye, false);

  // 4. onto the screen
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
  gl.uniform1f(u(blitP, 'uFlash'), frame.flash || 0);
  gl.uniform1f(u(blitP, 'uTube'), frame.tube === undefined ? -1 : frame.tube);
  gl.uniform1f(u(blitP, 'uRed'), frame.red || 0);
  gl.uniform1f(u(blitP, 'uGold'), frame.gold || 0);
  gl.uniform1f(u(blitP, 'uTime'), frame.time || 0);
  gl.uniform1f(u(blitP, 'uBlur'), frame.blur || 0);
  gl.uniform3fv(u(blitP, 'uGrade'), frame.grade || [1, 1, 0]);
  gl.uniform1f(u(blitP, 'uBolt'), frame.bolt || 0);
  gl.bindVertexArray(empty);
  gl.drawArrays(gl.TRIANGLES, 0, 3);
  gl.finish();
}
