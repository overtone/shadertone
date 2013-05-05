                                        __              __          __
                                  _____/ /_  ____ _____/ /__  _____/ /_____  ____  ___
                                 / ___/ __ \/ __ `/ __  / _ \/ ___/ __/ __ \/ __ \/ _ \
                                (__  ) / / / /_/ / /_/ /  __/ /  / /_/ /_/ / / / /  __/
                               /____/_/ /_/\__,_/\__,_/\___/_/   \__/\____/_/ /_/\___/
                                                                                     .

A Clojure library designed to mix
Musical Synthesis via [Overtone](https://github.com/overtone/overtone) and
OpenGL shaders a la [Shadertoy](http://www.shadertoy.com).

[Overtone](https://github.com/overtone/overtone) is a toolkit for
designing sound synthesizers and coding musical performances.  Based
on [Clojure](http://clojure.org/) and
[Supercollider](http://supercollider.sourceforge.net/), it is a
powerful combination of a general-purpose, interactive, multi-threaded
language built on the JVM with a state of the art, realtime sound
synthesis server.

[Shadertoy](http://www.shadertoy.com) is a website that exhibits some
amazing dynamic imagery--all procedurally generated via WebGL.  It is
a fantastic example of how limitations and constraints allow you to
focus and create great artwork.  All the imagery is created via a
functional per-pixel program called a Fragment Shader that is provided
only a few inputs like the pixel position and current time.  The
imagery is generated inside your Graphics Processing Unit (GPU) and
offloads your CPU.  Visuals are more powerful than words, here.  Take
a look at some fun examples:
[isolines](https://www.shadertoy.com/view/MsXGz8),
[flower](https://www.shadertoy.com/view/4dX3Rn), and
[electron](https://www.shadertoy.com/view/MslGRn).

This library provides a way for you to create music and visuals that
work together.  The example inspiration for Shadertone was [this
live-coding video by h3xl3r](http://vimeo.com/51993089).  H3xl3r edits
the fragment shader visual code live while the music plays.  This
library gives you the ability to hack on *both* the visual and audio
code at the same time.

I've created a basic demo screencast that you can [watch
here](https://www.youtube.com/watch?v=_8T15N3ZvYc).  If you'd like to
see how this all started, check out [the
announcement](https://groups.google.com/forum/?fromgroups=#!topic/overtone/7bQSJUUviBw)
and [video preview](https://www.youtube.com/watch?v=UMg8Td5Gqhk).

I hope this allows you to create some happiness.  Enjoy!

## Usage

The library is just coming together, so expect change.  There are two main ways to use the code.  If you want the latest code or are just exploring what shadertone can do, you should clone the repo from github.  But, if you have a project idea and want to use shadertone as part of that project, you can specify the current version in your Leiningen project.clj to download the library from clojars.

### Option 1: clone this repository

1. See top of https://github.com/overtone/shadertone for details on how to clone the repo.
2. Make sure you have leiningen 2.1.0 or later.  This fixed an issue with loading the LWJGL libraries.
3. Bring up your favorite REPL and step through the demo files in the examples directory.  There are examples of all of the various shadertone features in the demos.
4. When you want to create your own code, make some files like examples/yourcode.clj and examples/yourshader.glsl.  See below.
5. Start with something basic in both files to be certain it works.
6. You can edit both files "live".  Use your favorite REPL environment
to adjust your Clojure code.  Shadertone will watch for edits to your
active shader and load them when you save the file.
7. Have fun!

### Option 2: download from clojars

1. In your project.clj,
    1. add `[shadertone "0.1.0"]` to your `:dependencies`.
    2. add lwjgl handling.  This is a bit involved since it requires native libs.
        1. add this to your `:dependencies`
                        
                [org.lwjgl.lwjgl/lwjgl "2.8.5"]
                [org.lwjgl.lwjgl/lwjgl_util "2.8.5"]
                [org.lwjgl.lwjgl/lwjgl-platform "2.8.5"
                 :classifier    ~(lwjgl-classifier)
                 :native-prefix ""]
        1. at the top, add code like this to compute the `:classifier`
                
                (require 'leiningen.core.eval)
                (def LWJGL-CLASSIFIER
                    "Per os native code classifier"
                    {:macosx  "natives-osx"
                     :linux   "natives-linux"
                     :windows "natives-windows"})
                (defn lwjgl-classifier
                  "Return the os-dependent lwjgl native-code classifier"
                  []
                  (let [os (leiningen.core.eval/get-os)]
                    (get LWJGL-CLASSIFIER os)))
2. In your clojure code, add something like `(:require [shadertone.tone :as t])` or `(:require [shadertone.shader :as s])` to your namespace.
3. Have fun!

Since this could be a bit confusing, take a look at
https://github.com/rogerallen/sot as a simple example of using
shadertone via Leiningen.

### yourcode.clj template

Here is a simple template for yourcode.clj

```clj
(ns yourcode
  (:use [overtone.live])
  (:require [shadertone.tone :as t]))

;; exec this to see a gray window pop up
(t/start "examples/yourshader.glsl")

;; exec this to watch the grey "throb" to red
(demo 10 (* (sin-osc 0.5) (saw 500)))
```

Here is a simple template for yourshader.glsl.

```c
uniform float iOvertoneVolume;
void main(void) {
  gl_FragColor = vec4(0.5 + 5.0*iOvertoneVolume,
                      0.5,
                      0.5,
                      1.0);
}
```

### Writing Fragment Shaders

[Fragment Shaders](http://www.opengl.org/wiki/Fragment_Shader) are
code that is executed for every pixel displayed in the window.  The
output is the color of that pixel, which you store in
`gl_FragColor`. To create different imagery, you take the current
pixel's position (from `gl_FragCoord`) and use that as input.  To have
Overtone affect the color, you'll want to use inputs like
`iOvertoneVolume`.  The syntax is C-like and given the math-processing
power of modern GPUs, it is somewhat astonishing what you can do in
1/60th of second.

One of the creators of www.shadertoy.com, Iñigo Quílez, has a website
that explains a variety of techniques to create interesting Fragment
Shaders at http://www.iquilezles.org/www/index.htm.  He also has some
interesting live coding demos here
http://www.iquilezles.org/live/index.htm.

### Shader Inputs

Here are the inputs you have available to your fragment shader.

#### From www.shadertoy.com:

There are several implicit values that Shadertone provides, just like
the website.  You will have to define other inputs at the top of your
shader.

```
uniform vec3      iResolution;     // viewport resolution (in pixels)
uniform float     iGlobalTime;     // shader playback time (in seconds)
uniform float     iChannelTime[4]; // channel playback time (in seconds)
uniform vec4      iMouse;          // mouse pixel coords. xy: current (if MLB down), zw: click
uniform samplerXX iChannel0..3;    // input channel. XX = 2D/Cube
uniform vec4      iDate;           // (year, month, day, time in seconds)
```

#### Overtone inputs:

There are three inputs from Overtone that you can use.

First is a tap on the output volume.  It is sent to the variable
`iOvertoneVolume` which you should define at the top of your shader if
you start your window with `shadertone.tone/start`.

```
uniform float iOvertoneVolume; // tap of system volume
```

Next, are two special textures.  First, if you use the keyword
:overtone-audio as a texture name, Overtone will send the output
sound's frequency spectrum (FFT) and audio waveform data to the
iChannel texture you specify.  This is
similar to www.shadertoy.com's audio textures.  The first row of the
texture contains the FFT data.  The second row of the texture contains
the current waveform data.

Second, if you use the :previous-frame keyword as a texture name,
Shadertone will capture the framebuffers you render and allow you to
use this as input for the next frame.

As an example, if you want these textures data to go to the first two
textures, add the argument
`:textures [:overtone-audio :previous-frame]`
to your `shadertone.tone/start` call.  Then, in your
glsl code use `texture2D(iChannel0,uv).r` to access the audio and
`texture2D(iChannel1,uv).rgb` to access the previous frame.

But that isn't all.  If you want to create your own inputs from
Overtone to Shadertone, you can create atoms that hold either a
floating-point number or a vector of 1-4 values.

For example, this clojure code

```clj
(def my-rgb (atom [0.3 0.1 0.5]))
(t/start "shaders/rgb.glsl" :user-data { "iRGB" my-rgb})
```

Will adjust this shader code live

```c
uniform vec3 iRGB;
void main(void)
{
    gl_FragColor = vec4(iRGB, 1.0);
}
```

Via a call like this:

```
(swap! my-rgb (fn [x] [0.55 0.95 0.75]))
```

#### Clojure API

To use Shadertone, the main routine you will use are in the
`shadertoy.tone` namespace.  Use `start` or `start-fullscreen` to bring
up a window.  Use `stop` to bring it down.  Calling `start` while a
window is active will stop it gracefully before starting up the new
window.

`start` takes a few parameters
* `:width` & `:height` are the window size in pixels
* `:title` is the window title
* `:textures` are a vector of texture filenames or the
  special texture keywords.  Up to 4 textures are allowed.
* `:user-data` is a map of strings to atoms.  The strings must match a
  uniform variable name in your shader.  The atoms are used to
  communicate values to those uniform variable names.
* `:user-fn` is for complete custom control of the application.
  Normally, you should not need to override this, but you can if you
  need to.

`start-fullscreen` takes just a subset of these, but the meanings are
the same: `:textures`, `:user-data`, `:user-fn`

There is also a similar api in `shadertone.shader` that does not
depend on Overtone.  This could be useful for other interactive
Clojure libraries.  I'd like to know if this type of use case is
desired, so please get in touch via Issue #14.

## License

Copyright © 2013 Roger Allen and [other contributors](https://github.com/overtone/shadertone/contributors).

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

For full license information, see the LICENSE file.
