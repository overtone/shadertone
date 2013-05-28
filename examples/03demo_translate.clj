(ns demo3
  (:require [shadertone.shader :as s]
            [shadertone.translate :as trans]))

;; translate this Clojure-like code to a GLSL shader
(trans/defshader simple
  '((uniform vec3  iResolution)
    (uniform float iGlobalTime)
    (slfn void main []
          (slet [vec2  uv           (/ gl_FragCoord.xy iResolution.xy)
                 float b            (abs (sin iGlobalTime))
                 nil   gl_FragColor (vec4 uv.x uv.y b 1.0)]
                nil))))
;;(print simple)

;; wrap the shader in an atom to allow it to be watched & modified
(def simple-atom (atom simple))
(s/start simple-atom)

;; swap it out for something simple
(swap! simple-atom (fn [x] "
void main(void) {
gl_FragColor = vec4(1.0,0.5,0.25,1.0);
}
"))

;; swap it back
(swap! simple-atom (fn [x] simple))

(s/stop)
