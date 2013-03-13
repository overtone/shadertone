(ns shadertone.core
  (:use [overtone.live]
        [overtone.synth.stringed])
  (:require [shadertone.shader :as s]
            [shadertone.tone :as t]))

(comment

  ;; bring up a simple visualization.
  ;; - the red component is just a ramp
  ;; - the green component is based on the Overtone sound volume
  ;; - the blue component is a sinusoid based on the time.
  (s/start 800 800 "shaders/simple.glsl" "Hello World!" t/overtone-volume)

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
  ;; or not...
  (ctl g :pre-amp 6.0 :distort 0.0 :lp-freq 5000)

  ;; try some other visualizations...
  (s/start-fullscreen "shaders/simple.glsl" t/overtone-volume)
  (s/start 800 800
           "shaders/sine_dance.glsl"
           "Sine Dance"
           t/overtone-volume)
  (s/start 800 800
           "shaders/quasicrystal.glsl"
           "Quasicrystal"
           t/overtone-volume)
  (s/start 800 800 "shaders/wave.glsl"
           "Hello Wave!"
           t/overtone-waveform)
  ;; testcase sounds
  (demo 5 (sin-osc 800))
  (demo 5 (saw 400))
  (demo 5 (square 200))

  ;; stop the shader display
  (s/stop)
  )

;; some basic tests of shadertoy
(comment
  (s/start 800 800 "shaders/calendar.glsl")
  (s/start 800 800 "shaders/mouse.glsl")
)
