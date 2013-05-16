(ns shadertone.core
  (:use [overtone.live])
  (:require [shadertone.tone :as t])
  (:gen-class))

;; See examples/00demo_intro_tour.clj for the example code that was
;; once here.

;; inspiration https://twitter.com/redFrik/status/329311535723839489
;; more translated tweets in
;; https://github.com/rogerallen/explore_overtone/blob/master/src/explore_overtone/redFrik.clj
(defsynth red-frik-329311535723839489
  []
  (out 0
       (* 2.0
          (rlpf:ar
           (distort
            (leak-dc:ar
             (lf-tri:ar
              (let [t (leak-dc:ar
                       (sum
                        (for [x (range 1 9)]
                          (pan2:ar
                           (> (lf-tri:ar
                               (/ 1 x) (/ x 3)) 0.3333)
                           (lf-tri:ar (/ 666 x))))))
                    ;; add a filtered tap to use in the shader
                    _ (tap "t0" 60 (a2k (lag t  0.5)))]
                (* t 999))))) 3e3))))

(defn -main [& args]
  (def rf (red-frik-329311535723839489))
  (t/start "examples/redFrik.glsl"
           :width 1280 :height 720
           :user-data {"t0" (atom {:synth rf :tap "t0"})})
  (println "Playing a demo for 100 seconds...")
  (println "  Inspired by from https://twitter.com/redFrik/status/329311535723839489")
  (Thread/sleep (* 100 1000))
  (println "Done.")
  (t/stop)
  (stop)
  (System/exit 0))
