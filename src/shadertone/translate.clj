(ns #^{:author "Roger Allen"
       :doc "Overtone library code."}
  shadertone.translate
  (:require [clojure.walk :as walk]))

;; WORK IN PROGRESS -- NOTHING WORKS YET

;; ======================================================================
;; translation functions for a dialect of clojure-like s-expressions
(defn- shader-assign-str [z]
  (let [[x y] z]
    (println "shader-assign-str" x y)
    (format "%s = %s;\n" x (shader-walk (list y)))))

(defn- shader-walk-let [x]
  (println "shader-walk-let:" x)
  (let [var-str  (apply str (map shader-assign-str (partition 2 (nth x 1))))
        stmt-str (apply str (map #(shader-walk (list %)) (butlast (drop 2 x))))
        ret-str  (let [v (first (shader-walk (list (last (drop 2 x)))))]
                   (if (nil? v)
                     ";\n"
                     (format "return %s;\n" v)))]
    (str var-str stmt-str ret-str)))
(defn- shader-walk-defn-args [x]
  (if (empty? x)
    "void"
    (identity x))) ;; FIXME
(defn- shader-walk-slfn [x]
 (let [fn-str (format "%s %s(%s) {\n%s}\n"
                      (nth x 1)
                      (nth x 2)
                      (shader-walk-defn-args (nth x 3))
                      (shader-walk (list (nth x 4))))]
   fn-str))
(defn- shader-walk-fn [x]
  (let [fn-str (format "%s(%s);\n"
                       (str (first x))
                       (apply str (interpose \, (map shader-walk (rest x)))))]
    ;;(println "shader-walk-fn" fn-str)
    fn-str))
(defn- inner-walk
  [x]
  (do
    (println "in:  " x);; "list?" (list? x) "vector?" (vector? x) "symbol?" (symbol? x))
    (cond
     (list? x)     (cond
                    (= "slfn" (str (first x))) (shader-walk-slfn x)
                    (= "let" (str (first x))) (shader-walk-let x)
                    :else (shader-walk-fn x))
     (symbol? x)   (identity x)
     (float? x)    (identity x)
     (integer? x)  (identity x)
     :else         (shader-walk x))))
(defn- outer-walk [x]
  (do
    (println "out: " x)
    (cond
     (list? x)     (first x)
     :else         (identity x))))
(defn- shader-walk [form]
  (walk/walk inner-walk outer-walk form))
(defn- create-shader
  [& params]
  (list
   (va
  (shader-walk (:fragment (first params)))) ;; FIXME

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
    :fragment
    ((slfn void main []
           (let [gl_FragCoord (vec4 1.0 0.5 0.5 1.0)]
             nil))))
  (str simplest)

  ;; simple test
  (defshader simple
    :varying [gl_FragCoord vec2]
    :uniform [iResolution  vec3]
    :fragment
    ((slfn void main []
           (let [uv (/ gl_FragCoord.xy iResolution.xy)
                 gl_FragColor (vec4 uv.x uv.y 0.0 1.0)]
             nil))))

  (str simple)

  ;; preliminary translation of wave.glsl
  (defshader wave
    :varying [gl_FragCoord vec2]
    :uniform [iResolution  vec3
              iChannel0    sampler2D]
    :fragment ;; return value is gl_FragColor vec4
    ((slfn float smoothbump [center width x]
           (let [w2 (/ width 2.0)
                 cp (+ center w2)
                 cm (- center w2)]
             (* (smoothstep cm center x)
                (- 1.0 (smoothstep center cp x)))))
     (slfn void main []
           (let [uv     (/ gl_FragCoord.xy iResolution.xy)
                 uv.y   (- 1.0 uv.y)
                 freq   (.x (texture2D iChannel0 (vec2 uv.x 0.25)))
                 wave   (.x (texture2D iChannel0 (vec2 uv.x 0.75)))
                 freqc  (smoothstep 0.0 (/ 1.0 iResolution.y) (+ freq uv.y -0.5))
                 wavec  (smoothstep 0.0 (/ 4.0 iResolution.y) (+ wave uv.y -0.5))
                 gl_FragColor (vec4 freqc wavec 0.25 1.0)]
             nil))))
  (str wave)

  )
