(ns shadertone.core
  (:use [overtone.live]
        [overtone.synth.stringed])
  (:require [shadertone.shader :as s]))

(comment
  ;; bring up the visualizations
  (s/start-run-thread 800 800 "shaders/simple.glsl")

  ;; now make some sounds...
  (def g (guitar))
  ;; Note: when you modify the synths, you need to re-tap the system
  ;; volume

  ;; strum away...
  (guitar-strum g :E :down 1.25)
  (guitar-strum g :A :up 0.25)
  (guitar-strum g :G :down 0.5)
  (guitar-strum g [-1 -1 -1 -1 -1 -1]) ; mute
  ;; maybe add some distortion
  (ctl g :pre-amp 5.0 :distort 0.76
     :lp-freq 2000 :lp-rq 0.25
     :rvb-mix 0.5 :rvb-room 0.7 :rvb-damp 0.4)
  ;; or not...
  (ctl g :pre-amp 6.0 :distort 0.0 :lp-freq 5000)

  ;; try some other visualizations
  (s/start-run-thread 800 800 "shaders/sine_dance.glsl")
  (s/start-run-thread 800 800 "shaders/quasicrystal.glsl")
  )
