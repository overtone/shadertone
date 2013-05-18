(ns shadertone.translate)

;; WORK IN PROGRESS -- NOTHING WORKS YET

;; ======================================================================
(defn- create-shader
  [& params]
  (vector params)) ;; FIXME

;; ======================================================================
;; Public API
(defmacro defshader
  "macro to define the fragment shader. returns shader"
  [name & params]
  `(def ~name (create-shader (apply hash-map (quote ~params)))))

;; (str shader) should return the shader text ready to compile

;; ======================================================================
;; For Testing...
(comment
  ;; simplest possible shader
  (defshader simplest
    :fragment (vec4 1.0 0.5 0.5 1.0))

  ;; simple test
  (defshader simple
    :varying [gl_FragCoord vec2]
    :uniform [iResolution  vec3]
    :fragment
    (let [uv (/ gl_FragCoord.xy iResolution.xy)]
      (vec4 uv.x uv.y 0.0 1.0)))
  (str simple)

  ;; preliminary translation of wave.glsl
  (defshader wave
    :varying [gl_FragCoord vec2]
    :uniform [iResolution  vec3
              iChannel0    sampler2D]
    :fragment ;; return value is gl_FragColor vec4
    ((defn smoothbump [center width x]
       (let [w2 (/ width 2.0)
             cp (+ center w2)
             cm (- center w2)]
         (* (smoothstep cm center x)
            (- 1.0 (smoothstep center cp x)))))
     (let [uv     (/ gl_FragCoord.xy iResolution.xy)
           uv.y   (- 1.0 uv.y)
           freq   (.x (texture2D iChannel0 (vec2 uv.x 0.25)))
           wave   (.x (texture2D iChannel0 (vec2 uv.x 0.75)))
           freqc  (smoothstep 0.0 (/ 1.0 iResolution.y) (+ freq uv.y -0.5))
           wavec  (smoothstep 0.0 (/ 4.0 iResolution.y) (+ wave uv.y -0.5))]
       (vec4 freqc wavec 0.25 1.0))))

  )
