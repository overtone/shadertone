(ns demo3
  (:require [shadertone.shader :as s]
            [shadertone.translate :as trans]))

(trans/defshader simple
  '((uniform vec3  iResolution)
    (uniform float iGlobalTime)
    (slfn void main []
          (slet [vec2  uv           (/ gl_FragCoord.xy iResolution.xy)
                 float b            (abs (sin iGlobalTime))
                 nil   gl_FragColor (vec4 uv.x uv.y b 1.0)]
                nil))))
;;(print simple)
(s/start nil :shader-str (str simple))

;; TODO
;; (s/shader-str (str simple)) ???
;; -or-
;; should the string be an atom that is watched?

(s/stop)
