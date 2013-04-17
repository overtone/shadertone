;; ======================================================================
;; this code is meant to be stepped through interactively, not
;; executed at once.  It demonstrates how an implicit function can be
;; used to create interesting graphics in shadertone.
;; Code was inspired by this article:
;;   http://www.iquilezles.org/www/articles/distance/distance.htm
(ns demo1
  ;; NOTE -- not using the Overtone bits...
  (:require [shadertone.shader :as s]))

;; Go over to the implicit_fn.glsl code and see how changes to the
;; code are reflected on the screen.  Note that on the Mac, you can
;; use emacs from a terminal, make it transparent and see the shader
;; window through the terminal for live-coding awesomeness.  Linux?
;; Windows?  Not sure...
(s/start "examples/implicit_fn.glsl"
         :width 800 :height 600 ;; fit to your own screen
         :textures [ "textures/granite.jpg" ])
;; FIXME? move :user-data to into shader.clj from tone.clj?
;; this would allow control of some implicit fn params from here.

(s/stop) ; at some point...
