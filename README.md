                         __              __          __
                   _____/ /_  ____ _____/ /__  _____/ /_____  ____  ___
                  / ___/ __ \/ __ `/ __  / _ \/ ___/ __/ __ \/ __ \/ _ \
                 (__  ) / / / /_/ / /_/ /  __/ /  / /_/ /_/ / / / /  __/
                /____/_/ /_/\__,_/\__,_/\___/_/   \__/\____/_/ /_/\___/
                                                                                     .

![HeaderImage](https://github.com/overtone/shadertone/raw/master/readme_header.jpg)

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

I've created a few screencast demos that you can watch:
* [Basic features overview](https://www.youtube.com/watch?v=_8T15N3ZvYc) from the [intro tour](https://github.com/overtone/shadertone/blob/master/examples/00demo_intro_tour.clj)
* [Livecoding demo](https://www.youtube.com/watch?v=X7EMPYlGgFc) which you can try via `lein run`
* [A quick teaser](https://www.youtube.com/watch?v=kyL3xc7MzR0) from the [disco example](https://github.com/overtone/shadertone/blob/master/examples/02demo_disco.clj)
* [Video preview](https://www.youtube.com/watch?v=UMg8Td5Gqhk) showing the very early stages of shadertone.

[Meta-eX](http://meta-ex.com/) has used Shadertone in live
performance.  Here are a few videos from their May, 2013 Kiev #hotcode
conference performance that show how Shadertone can be used: [after
party 1](http://vimeo.com/67487486), [main
conference](http://vimeo.com/67487485), [after party
1](http://www.youtube.com/watch?v=CjFCSTQbJx0), [after party
2](http://www.youtube.com/watch?v=CjFCSTQbJx0).  Latest news & videos
can always be found at [their website](http://meta-ex.com)

[Repl Electric](http://www.repl-electric.com/) has also used
Shadertone in performance.  Here is a wonderful recreation of ["The
Stars" livecoded on Vimeo](http://vimeo.com/95988263).

If you're interested, here's [the original
announcement](https://groups.google.com/forum/?fromgroups=#!topic/overtone/7bQSJUUviBw)
on the Overtone Google Group.

I hope this library allows you to create some happiness.  Enjoy!

## Usage

The library is just coming together, so expect change.  There are two
main ways to use the code.  If you want the latest code or are just
exploring what shadertone can do, you should clone the repo from
github.  But, if you have a project idea and want to use shadertone as
part of that project, you can specify the current version in your
Leiningen project.clj to download the library from clojars.

### Option 1: clone this repository

1. See top of https://github.com/overtone/shadertone for details on how to clone the repo.
2. Bring up your favorite REPL and step through the demo files in the examples directory.  There are examples of all of the various shadertone features in the demos.
3. When you want to create your own code, make some files like examples/yourcode.clj and examples/yourshader.glsl.  See below.
4. Start with something basic in both files to be certain it works.
5. You can edit both files "live".  Shadertone will watch for edits to your active shader and load them when you save the file.

### Option 2: download from clojars

In your project.clj, add ![Clojars Project](http://clojars.org/shadertone/latest-version.svg) to your `:dependencies`.

See https://github.com/rogerallen/sot for a simple example of using
shadertone via Leiningen.  Use the example directory in this repository for ideas.

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

The code needs to be in a GLSL format with a `main()` routine that
returns the `gl_FragColor`.  Shadertoy.com has changed their
syntax to make translation just a bit more work.  You will need to
translate Image shaders formatted like this:

```c
void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
  ...
  fragColor = vec4(...);
}
```

To GLSL fragment shaders formatted like this:

```c
void main(void) {
  ...
  gl_FragColor = vec4(...);
}
```

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
uniform float     iTime;     // shader playback time (in seconds)
uniform float     iChannelTime[4]; // channel playback time (in seconds)
uniform vec3      iChannelResolution[4]; // channel width, height, 1
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

##### Synth Inputs

You can `tap` a synth value and easily communicate that
value to your GLSL fragment shader.  See the "vvv" synth example in
the 00_demo_intro_tour.clj demo and also the core.clj usage.

In a nutshell, you define your synth and add a call to `(tap "a" 60
a)` where `"a"` is the name of your tap, it is updated 60 times per
second and the `a` synth variable is some interesting value you want
to communicate.

Then in your `start` call, the `:user-data` map should include a
reference to that `tap`.  Like `:user-data { "iA" (atom {:synth v :tap
"a"}) }` to call the value `iA` as input to your GLSL shader, if your
synth was instantiated as `v`.

Finally, in your GLSL fragment shader, define your new input value as
`uniform float iA;` and use `iA` to control your shader in interesting
ways.

### Clojure API

To use Shadertone, the main routines you will use are in the
`shadertoy.tone` namespace.  Use `start` or `start-fullscreen` to bring
up a window.  Use `stop` to bring it down.  Calling `start` while a
window is active will stop it gracefully before starting up the new
window.

`start` takes a few parameters
* the first parameter is either the filename of a glsl shader or an atom containing a string with the glsl code.
* `:width` & `:height` are the window size in pixels
* `:title` is the window title
* `:display-sync-hz` is for setting the refresh rate of the window.  By default it is 60 Hz.
* `:textures` are a vector of texture filenames or the
  special texture keywords.  Up to 4 textures are allowed.
* `:user-data` is a map of strings to atoms.  The strings must match a
  uniform variable name in your shader.  The atoms are used to
  communicate values to those uniform variable names.
* `:user-fn` is for complete custom control of the application.
  Normally, you should not need to override this, but you can if you
  need to.

`start-fullscreen` takes just a subset of these, but the meanings are
the same: `:display-sync-hz`, `:textures`, `:user-data`, and `:user-fn`

The base api is in `shadertone.shader` and does not
depend on Overtone.  This is useful when you don't need sound accompaniment and
could be useful for other interactive ideas.

### Lisp-like GLSL

You can program your GLSL in a lisp-like language.  It is a simple, straight translation
layer from lisp to a GLSL string.  For those hoping for Clojure instead of lisp,
I welcome your suggestions and help, but a Clojure-to-C compiler was way too much work for me at this time.

See https://github.com/overtone/shadertone/blob/master/examples/03demo_translate.clj for an example of how you can use this.
In addition, there are tests in https://github.com/overtone/shadertone/blob/master/test/shadertone/translate_test.clj that could be informative.

To access this functionality, import the `shadertone.translate` namespace and use `defshader` to compile your
lisp into a string for passing to the `start` function.

#### Special forms and how they relate to GLSL code
* define functions
    `(defn <return-type> <function-name> <function-args-vector> <body-stmt1> ... )`
* function calls
    `(<name> <arg1> <arg2> ... )`
* variable creation/assignment
    `(uniform <type> <name>)`
    `(setq <type> <name> <statement>)`
    `(setq <name> <statement>)`
* for(;;) {}
    `(forloop [ <init-stmt> <test-stmt> <step-stmt> ] <body-stmt1> ... )`
* while() {}
    `(while <test-stmt> <body-stmt1> ... )`
* if() {}
    `(if <test> <stmt>)`
    `(if <test> (do <body-stmt1> ...))`
* if() {} else {}
    `(if <test> <stmt> <else-stmt>)`
    `(if <test> (do <body-stmt1> ...) (do <else-stmt1> ...))`
* switch () { case integer: ... break; ... default: ... }
    `(switch <test> <case-int-1> <case-stmt-1> ...)`
    cases can only be integer or :default keyword
* break;
    `(break)`
* continue;
    `(continue)`
* return value;
    `(return <statement>)`

### Reading Pixels from the Framebuffer

You can read back a pixel value from your frame either via
`(shadertone.shader/pixel)` function or by accessing the
`@shadertone.shader/pixel-value` atom directly.  With this feature,
your fragment-shader could control your synths!  Thanks to Circu Virtu
on the Google Group for this idea.

Enable this feature via `(pixel-read-enable! x y)` where x and y are
valid locations within your window.  Be careful as there is no error
checking here.

Disable this access via `(pixel-read-disable!)`

## Changes

* __0.2.6 - Released Sept ?, 2015__

  * Bugfix: [Issue #31](https://github.com/overtone/shadertone/issues/31) - Fix unary math ops and note the shadertoy.com syntax change.  Thanks to [hlolli](https://github.com/hlolli)

* __0.2.5 - Released May 26, 2015__

  * Bugfix: [Issue #28](https://github.com/overtone/shadertone/issues/28) - Fix crash on reloading shaders.  Thanks to [hlolli](https://github.com/hlolli)
  * Enhancement: [Pull Request #26](https://github.com/overtone/shadertone/issues/26), [Issue #29](https://github.com/overtone/shadertone/issues/29) - Get rid of reflection issues.  Thanks to [josephwilk](https://github.com/josephwilk)
  * Typo: [Pull Request #27](https://github.com/overtone/shadertone/issues/27) - Thanks to [dvberkel](https://github.com/dvberkel)

* __0.2.4 - Released Dec 11, 2014__

  * Enhancement: [Issue #16](https://github.com/overtone/shadertone/issues/16) - Add iChannelResolution.
  * Enhancement: [Issue #18](https://github.com/overtone/shadertone/issues/18) - Add pixel read feature.  Thanks to Circu Virtu.
  * Enhancement: [Issue #14](https://github.com/overtone/shadertone/issues/14) - No external change since LWJGL is limited to one window, but code has been improved by reducing the use of global state a bit.
  * Enhancement: [Issue #23](https://github.com/overtone/shadertone/issues/23) - Generally improve error handling & robustness.  Thanks to [josephwilk](https://github.com/josephwilk)

* __0.2.3 - Released Mar 8, 2014__

 * Enhancement: Update to LWJGL 2.9.1
 * Bugfix: [Issue #22](https://github.com/overtone/shadertone/issues/22) - Fix support for Intel Graphics chips.  Thanks to [josephwilk](https://github.com/josephwilk)
 * Bugfix: [Issue #20](https://github.com/overtone/shadertone/issues/20) - Fix fullscreen support on Retina Macs.  Thanks to [johnjelinek](https://github.com/johnjelinek)

* __[0.2.2](https://github.com/overtone/shadertone/issues?milestone=4) - Released Nov 25, 2013__

 * Update: [Issue #19](https://github.com/overtone/shadertone/issues/19) - Overtone 0.9.1
 * Bugfix: uninitialized `rotx` variable in simplecube.glsl example.

* __0.2.1 - Released Sep 1, 2013__

 * Bugfix: [Issue #15](https://github.com/overtone/shadertone/issues/15) - Automatic file reloading fail on cygwin.

* __[0.2.0](https://github.com/overtone/shadertone/issues?milestone=2&state=closed) - Released Aug 1, 2013__

 * Enhancement: Add ability to 'tap' a synth and communicate that to a shader via :user-data
 * Enhancement: create shaders in a lisp-like language (Issue #1)
 * Enhancement: "lein run" does something now! Added redFrik tweet-inspired demo.
 * Enhancement: Increased resolution of waveform and FFT texture to 4096 from 512.
 * Enhancement: Add Spectrogram example using FFT texture and :previous-frame texture.
 * Enhancement: Update to LWJGL 2.9.0
 * Bugfix: fixup native library handling (Issues [#8](https://github.com/overtone/shadertone/issues/8), [#13](https://github.com/overtone/shadertone/issues/13))
 * Bugfix: [Issue #12](https://github.com/overtone/shadertone/issues/12) - Mac window positioning broken on LWJGL 2.9.0

* __[0.1.0](https://github.com/overtone/shadertone/issues?milestone=1&state=closed) - Released May 5, 2013__

 * Initial Release


## Acknowledgements

Many thanks to those who have helped directly and indirectly to make
Shadertone.  Specifically, I'd like to thank:

* Sam Aaron for helping organize the code and developing the public interface.
* Iñigo Quílez for creating Shadertoy.
* Fredrik Olofsson (@redFrik) for his fantastic sctweets.

## License

Copyright © 2013-2014 Roger Allen and [other
contributors](https://github.com/overtone/shadertone/contributors).

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
