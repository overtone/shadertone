;; ======================================================================
;; this code is meant to be stepped through interactively, not
;; executed at once.  It demonstrates some basic features of
;; shadertone.  Kind of an "introductory tour"...
(ns demo0
  (:use [overtone.live]
        [overtone.synth.stringed])
  (:require [shadertone.tone :as t]))

;; ======================================================================
;; start an example shader showing movement and reaction to sounds
(t/start "examples/sine_dance.glsl")

;; ======================================================================
;; now define some sounds...
(def gtr (guitar))
(def snare (sample (freesound-path 26903)))
(def kick (sample (freesound-path 2086)))
(def close-hihat (sample (freesound-path 802)))
(def open-hihat (sample (freesound-path 26657)))
;; play the sounds. see how it affects the shader
;; some drums...
(snare)
(kick)
(close-hihat)
(open-hihat)
;; some guitar strums...
(guitar-strum gtr :E :down 1.25)
(guitar-strum gtr :A :up 0.25)
(guitar-strum gtr :G :down 0.5)
(guitar-strum gtr [-1 -1 -1 -1 -1 -1]) ; mute
;; maybe add some distortion
(ctl gtr
     :pre-amp 5.0 :distort 0.76
     :lp-freq 2000 :lp-rq 0.25
     :rvb-mix 0.5 :rvb-room 0.7 :rvb-damp 0.4)

;; ======================================================================
;; try some other visualizations...make sure you play with the sounds,
;; too.
;; see how can control width & height
(t/start "examples/quasicrystal.glsl"
         :width 800 :height 800
         :title "Quasicrystal")
;; Here is a feature that you cannot get from the shadertoy.com
;; website.  Use the previously rendered frame as input to this frame.
(t/start "examples/zoomwave.glsl" :textures [ :overtone-audio :previous-frame ])
;; see the sound waveform and FFT
(t/start "examples/wave.glsl" :textures [ :overtone-audio ])

;; these testcase sounds are mainly for the wave shader
;; (warning, a little loud)
(demo 5 (* 1.25 (sin-osc))) ;; looks like a max to me
(demo 15 (mix (sin-osc [(mouse-x 20 20000 EXP)
                        (mouse-y 20 20000 EXP)])))
(demo 10 (mix (sin-osc [100 1000])))  ; 10000])))
(demo 15 (saw (mouse-x 20 20000 EXP)))
(demo 15 (square (mouse-x 20 20000 EXP)))

;; ======================================================================
;; bring up a simple visualization to consider learning with
;; - the red component is just a ramp
;; - the green component is based on the Overtone sound volume
;; - the blue component is a sinusoid based on the time.
(t/start "examples/simple.glsl")
;; see that you can easily go fullscreen
(t/start-fullscreen "examples/simple.glsl")

;; ======================================================================
;; you can use textures, too.
;; use a keyword to tell where to place the waveform texture
;;   :overtone-audio
(t/start "examples/simpletex.glsl"
         :textures [:overtone-audio "textures/granite.png" "textures/towel.png"])
;; (sound doesn't affect the next two)
(t/start "examples/simpletexa.glsl"
         :title "Simple Tex w/Alpha"
         :textures ["textures/granite_alpha.png" "textures/towel.png"])
;; (use your mouse to look around)
(t/start "examples/simplecube.glsl" :textures ["textures/buddha_*.jpg"])

;; ======================================================================
;; the user-data api.  create atoms and send them to your shader
;; NOTE your shader needs to define:
;;   uniform float iRGB;
;; to match the user-data map below
(def my-rgb (atom [0.3 0.1 0.5]))
(t/start "examples/rgb.glsl" :user-data { "iRGB" my-rgb})
;; now you can adjust your data at-will and it will be sent to
;; the GPU at 60Hz
(swap! my-rgb (fn [x] [0.55 0.95 0.75]))

;; NEW in 0.2.0, communicate internal taps from a synth to the shader
(defsynth vvv
  []
  (let [a (+ 300 (* 50 (sin-osc:kr (/ 1 3))))
        b (+ 300 (* 100 (sin-osc:kr (/ 1 5))))
        _ (tap "a" 60 (a2k a))
        _ (tap "b" 60 (a2k b))]
    (out 0 (pan2 (+ (sin-osc a)
                    (sin-osc b))))))
(def v (vvv))
(t/start "examples/vvv.glsl"
         :user-data { "iA" (atom {:synth v :tap "a"})
                      "iB" (atom {:synth v :tap "b"}) })
(stop)

;; stop the shader display
(t/stop)

;; ======================================================================
;; some less interesting basic tests of shadertoy...
(t/start "examples/calendar.glsl") ;; very slowly changes
(t/start "examples/mouse.glsl") ;; click-n-drag mouse
