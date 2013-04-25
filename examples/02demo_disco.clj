;; ======================================================================
;; this code is meant to be stepped through interactively, not
;; executed at once.  It demonstrates how we can interact with
;; Overtone.
(ns demo2
  (:use [overtone.live])
  (:require [shadertone.tone :as t]
            [leipzig.live    :as ll]
            [leipzig.melody  :as lm]))

(t/start "examples/disco.glsl"
         :width 800 :height 600
         :textures [:overtone-audio])

;; now go find some Overtone demos like examples/compositions/funk.clj

;; or try out your own drumkit via leipzig
(def snare (sample (freesound-path 26903)))
(def kick (sample (freesound-path 2086)))
(def close-hihat (sample (freesound-path 802)))
(def open-hihat (sample (freesound-path 26657)))
(defmethod ll/play-note :default [{p :pitch}]
  (case p
      0 nil
      1 (snare)
      2 (kick)
      3 (close-hihat)
      4 (open-hihat)))
(def melody (->> (lm/phrase [1 1 1 1 1 1 1 1]
                            [2 0 2 0 2 0 2 0]) ;; <- play!
                 (lm/with (lm/phrase
                           [1 1 1 1 1 1 1 1]
                           [0 0 1 0 3 1 4 0])) ;; <- play!
                 (lm/where :time (lm/bpm (* 4 60)))))
(ll/jam melody)
(def melody nil) ;; to turn it off

(t/stop) ; at some point...
