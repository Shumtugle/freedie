# Freedie

A game for Android about knocking down buildings with a hammer, on a summer street of a
seaside town. Freedie, a giant heaped together from stone, brick and planks and dressed
as a maid, is its mascot.

Early days: the engine stands, the game is still to come.

## How it is made

- Java 8 and the platform alone; no build system, no libraries.
- The game lives in plain Java: rules, physics, what stands where. Each frame it fills a
  draw list: the eye, the sun, the sky and every box in the scene.
- The phone draws that list with OpenGL ES 3: the sun's depth map, then the sky and the
  boxes into a small frame 274 pixels across, then the small frame onto the screen with
  hard square pixels and a flat layer of figures over it. The shaders are in
  `assets/shaders`.
- The desktop stand (`stand/`) plays scenes against the same Java and writes each frame's
  draw list; `stand/web/index.html` draws it with the same shaders in a browser.

## Build

```sh
ANDROID_JAR=... KEYSTORE=... KEY_ALIAS=... KS_PASS=... ./build.sh
```

## License

Zero-Clause BSD, see `LICENSE`.
