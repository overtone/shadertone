(ns demo1
  (:use [overtone.live]
        [overtone.inst.sampled-piano])
  (:require [shadertone.tone :as t]))

(comment
  ;; follow along, executing lines in this file for a demo
  ;; Note: 720p is 160x42 chars for my emacs

  (t/start "examples/demo1a.glsl" :width 1280 :height 720)

  (sampled-piano)

  ;; to be continued...

 )
