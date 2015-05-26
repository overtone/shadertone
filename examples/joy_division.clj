(ns joy-division
  (:use [overtone.live]
        [overtone.synth.stringed])
  (:require [shadertone.tone :as t]))

(def gtr (guitar))
(ctl gtr
     :pre-amp 5.0 :distort 0.76
     :lp-freq 6000 :lp-rq 0.5
     :rvb-mix 0.5 :rvb-room 0.85 :rvb-damp 0.8)

(t/start "examples/unknown_pleasures.glsl"
         :width 600 :height 600
         :textures [ :overtone-audio :previous-frame ])

(guitar-strum gtr :E :down 1.25)
(guitar-strum gtr :A :up 0.25)
(guitar-strum gtr :G :down 0.5)
(guitar-strum gtr [-1 -1 -1 -1 -1 -1]) ; mute

(t/stop)
