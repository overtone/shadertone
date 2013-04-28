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
(def clap (sample (freesound-path 48310)))
(def gshake (sample (freesound-path 113625)))

(defmethod ll/play-note :default [{p :pitch}]
  (case p
      0 nil
      1 (snare)
      2 (kick)
      3 (close-hihat)
      4 (open-hihat)
      5 (clap)
      6 (gshake)))

(def beats0     ;; 1   2   3   4   5   6   7   8
  (->> (lm/phrase [1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1]
                  [2 0 1 0 2 0 1 0 2 0 1 0 2 0 1 0]) ;; <- adjust
       (lm/with (lm/phrase
                  [1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1]
                  [0 0 5 0 0 0 5 0 0 0 5 0 0 0 5 0]))
       (lm/with (lm/phrase
                  [1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1]
                  [3 0 3 0 3 0 3 0 3 0 3 0 3 0 3 0]))
       (lm/with (lm/phrase
                  [1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1]
                  [6 6 0 6 6 6 0 6 6 6 0 6 6 6 0 6]))
       (lm/where :time (lm/bpm (* 2 124)))
       (lm/where :duration (lm/bpm (* 2 124)))))

(ll/jam beats0)

(def beats0     ;; 1   2   3   4   5   6   7   8
  (->> (lm/phrase [1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1]
                  [2 0 1 1 2 0 1 0 2 0 1 1 2 0 1 0])
       (lm/with (lm/phrase
                  [1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1]
                  [0 5 5 0 0 5 5 0 0 5 5 0 0 5 5 0]))
       (lm/with (lm/phrase
                  [1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1]
                  [3 0 0 3 3 0 3 0 3 0 3 0 3 0 3 0]))
       (lm/with (lm/phrase
                  [1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1]
                  [6 6 0 6 0 6 0 6 0 6 0 6 0 6 0 6]))
       (lm/where :time (lm/bpm (* 2 124)))
       (lm/where :duration (lm/bpm (* 2 124)))))

(def beats0 nil) ;; to turn off the melody

(t/stop) ; at some point...
