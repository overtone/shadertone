(ns shadertone.core
  (:use [overtone.live]
        [overtone.synth.stringed])
  (:require [shadertone.tone :as t]))

(comment

  ;; bring up a simple visualization.
  ;; - the red component is just a ramp
  ;; - the green component is based on the Overtone sound volume
  ;; - the blue component is a sinusoid based on the time.
  (t/start "shaders/simple.glsl")

  ;; now make some sounds...
  (def g (guitar))
  ;; strum away...
  (guitar-strum g :E :down 1.25)
  (guitar-strum g :A :up 0.25)
  (guitar-strum g :G :down 0.5)
  (guitar-strum g [-1 -1 -1 -1 -1 -1]) ; mute
  ;; maybe add some distortion
  (ctl g
       :pre-amp 5.0 :distort 0.76
       :lp-freq 2000 :lp-rq 0.25
       :rvb-mix 0.5 :rvb-room 0.7 :rvb-damp 0.4)

  ;; try some other visualizations...
  (t/start-fullscreen "shaders/simple.glsl")
  (t/start "shaders/sine_dance.glsl"
           :width 800 :height 800
           :title "Sine Dance")
  (t/start "shaders/quasicrystal.glsl")
  (t/start "shaders/wave.glsl")
  ;; testcase sounds for fft & wave shader (warning, a little loud)
  (demo 5 (sin-osc))
  (demo 15 (mix (sin-osc [(mouse-x 20 20000 EXP)
                          (mouse-y 20 20000 EXP)])))
  (demo 10 (mix (sin-osc [100 1000])))  ; 10000])))
  (demo 15 (saw (mouse-x 20 20000 EXP)))
  (demo 15 (square (mouse-x 20 20000 EXP)))

  ;; user-data api.  create atoms and send them to your shader
  ;; NOTE your shader needs to define:
  ;;   uniform float iRed, iGreen, iBlue;
  ;; to match the user-data map below
  (def my-red   (atom 0.3))
  (def my-green (atom 0.0))
  (def my-blue  (atom 0.0))
  (t/start "shaders/rgb.glsl"
           :user-data { "iRed"   my-red
                        "iGreen" my-green
                        "iBlue"  my-blue})
  ;; now you can adjust your data at-will and it will be sent to
  ;; the GPU at 60Hz
  (swap! my-red   (fn [x] 0.55))
  (swap! my-green (fn [x] 0.95))
  (swap! my-blue  (fn [x] 0.75))

  ;; you can use textures now, too.
  ;; grab some from:
  ;;   https://www.shadertoy.com/presets/tex02.jpg
  ;;   https://www.shadertoy.com/presets/tex07.jpg
  ;; use a keyword to tell where to place the waveform texture
  ;;   :iOvertoneAudio
  (t/start "shaders/simpletex.glsl" :textures [:iOvertoneAudio "tex02.jpg" "tex07.jpg"])

  ;; stop the shader display
  (t/stop)

  ;; some basic tests of shadertoy
  (t/start "shaders/calendar.glsl")
  (t/start "shaders/mouse.glsl")
)
