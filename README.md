# shadertone

A Clojure library designed to mix Overtone and www.shadertoy.com GLSL shaders.

## Usage

### Building

To link in natives, until Leiningen gets better...do this for your platform.

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
Overtone to create audio as input.  lein swank or lein repl away...

### Shader Inputs

uniform vec3      iResolution;     // viewport resolution (in pixels)
uniform float     iGlobalTime;     // shader playback time (in seconds)
uniform float     iChannelTime[4]; // channel playback time (in seconds)
uniform vec4      iMouse;          // mouse pixel coords. xy: current 
                                   // (if MLB down), zw: click
uniform sampler2D iChannel[4];
uniform vec4      iDate;           // (year, month, day, time in seconds)

## License

Copyright © 2013 Roger Allen

Distributed under the Eclipse Public License, the same as Clojure.
