;; ======================================================================
;; this code is meant to be stepped through interactively, not
;; executed at once.  It demonstrates how we can interact with
;; Overtone.
(ns demo2
  (:require [shadertone.tone :as t]))

(t/start "examples/disco.glsl"
         :width 800 :height 600
         :textures [:iOvertoneAudio])

;; now go find some Overtone demos like
;; examples/compositions/funk.clj

(t/stop) ; at some point...
