(ns #^{:author "Roger Allen"
       :doc "Shadertoy meets Overtone.  A demonstration for 'lein run'"}
  shadertone.core
  (:use [overtone.live])
  (:require [shadertone.tone :as t])
  (:gen-class))

;; See examples/00demo_intro_tour.clj for the example code that was
;; once here.

;; inspiration https://twitter.com/redFrik/status/329311535723839489
;; more translated tweets in
;; https://github.com/rogerallen/explore_overtone/blob/master/src/explore_overtone/redFrik.clj
(defsynth red-frik-329311535723839489
  [gate 1]
  (out
   0
   (* (env-gen (adsr 3.0 0.1 0.8 3.0) :gate gate :action FREE)
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
  (let [rf (red-frik-329311535723839489)
        ss (t/start "examples/redFrik.glsl"
                    :width 1280 :height 720
                    :user-data {"t0" (atom {:synth rf :tap "t0"})})]
    (println "Playing a 60 second demo inspired by")
    (println "https://twitter.com/redFrik/status/329311535723839489\nEnjoy...")
    (Thread/sleep (* 60 1000))
    (println "Done.")
    (ctl rf :gate 0) ;; fade out
    (Thread/sleep (* 3 1000))
    (t/stop ss)
    (stop)
    (System/exit 0)))
