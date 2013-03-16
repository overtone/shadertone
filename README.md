# shadertone

A Clojure library designed to mix
[Overtone](https://github.com/overtone/overtone) Musical Synthesis and
[Shadertoy](http://www.shadertoy.com) GLSL shaders.

## Usage

Under Construction.
See src/shadertone/core.clj
See https://www.youtube.com/watch?v=UMg8Td5Gqhk

### Building

To link in natives for LWJGL, until Leiningen gets better...do this
for your platform after running `lein deps`.

### Mac
```bash
> mkdir -p target/native/macosx/x86_64
> cd target/native/macosx/x86_64
> jar xf ~/.m2/repository/org/lwjgl/lwjgl/lwjgl-platform/2.8.5/lwjgl-platform-2.8.5-natives-osx.jar
```

### Linux
```bash
> mkdir -p target/native/linux/x86_64
> cd target/native/linux/x86_64
> jar xf ~/.m2/repository/org/lwjgl/lwjgl/lwjgl-platform/2.8.5/lwjgl-platform-2.8.5-natives-linux.jar
```

### Using

Hopefully, just copy over any www.shadertoy.com shader, load it and use
Overtone to create some audio as input.  lein swank or lein repl away...

### Shader Inputs

From www.shadertoy.com:
```
uniform vec3      iResolution;     // viewport resolution (in pixels)
uniform float     iGlobalTime;     // shader playback time (in seconds)
uniform float     iChannelTime[4]; // channel playback time (in seconds)
uniform vec4      iMouse;          // mouse pixel coords. xy: current (if MLB down), zw: click
uniform sampler2D iChannel[4];     // textures
uniform vec4      iDate;           // (year, month, day, time in seconds)
```
See code for adding your own, including:
```
uniform float iOvertoneVolume; // tap of system volume
```
and by default, Overtone sends the output sound's FFT and Wave data to a texture.

## License

Copyright © 2013 Roger Allen

Distributed under the Eclipse Public License, the same as Clojure.
