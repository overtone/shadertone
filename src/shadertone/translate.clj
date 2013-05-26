(ns #^{:author "Roger Allen"
       :doc "Overtone library code."}
  shadertone.translate
  (:require [clojure.walk :as walk]))

;; WORK IN PROGRESS -- Barely working...
;; TODO:
;;   x fn calls
;;   o for(;;) {}
;;     o dotimes
;;   o while() {}
;;   o if() {}
;;   o if() {} else {}
;;   o switch () { case integer: ... break; ... default: ... }
;;   x break;
;;   x continue;
;;   x void main() {}

;; ======================================================================
;; translation functions for a dialect of clojure-like s-expressions
(declare shader-walk)

(defn- shader-assign-str [z]
  (let [[type name value] z
        ;;_ (println "shader-assign-str-0:" type name value)
        type-str (if (nil? type) "" (format "%s " (str type)))
        asn-str (format "%s%s = %s;\n"
                        type-str name
                        (shader-walk (list value)))]
    ;;(println "shader-assign-str-1:" asn-str)
    asn-str))

(defn- shader-walk-let [x]
  ;;(println "shader-walk-let-0:" x)
  (let [var-str  (apply str (map shader-assign-str (partition 3 (nth x 1))))
        stmt-str (apply str (map #(shader-walk (list %)) (butlast (drop 2 x))))
        ret-str  (let [v (shader-walk (list (last (drop 2 x))))]
                   (if (nil? (first v))
                     ""
                     (format "return(%s);\n" v)))]
    ;;(println "shader-walk-let-1:" var-str stmt-str ret-str)
    (str var-str stmt-str ret-str)))

(defn- shader-walk-defn-args [x]
  ;;(println "shader-walk-defn-args-0" x (empty? x))
  (if (empty? x)
    "void"
    (apply str (interpose \, (map #(apply (partial format "%s %s") %)
                                  (partition 2 x))))))
(defn- shader-walk-slfn [x]
  ;;(println "shader-walk-slfn-0:" x)
  (let [fn-str (format "%s %s(%s) {\n%s}\n"
                       (nth x 1)
                       (nth x 2)
                       (shader-walk-defn-args (nth x 3))
                       (shader-walk (list (nth x 4))))]
   ;;(print "shader-walk-slfn-1:" fn-str)
   fn-str))

(defn- shader-walk-fn [x]
  ;;(println "shader-walk-fn-0:" x)
  (let [pre-fn (if (= (first (str (first x))) \.) "" (str (first x)))
        post-fn (if (= (first (str (first x))) \.) (str (first x)) "")
        fn-str (format "%s(%s)%s"
                       pre-fn
                       (apply str (interpose \, (map #(shader-walk (list %))
                                                     (rest x))))
                       post-fn)]
    ;;(println "shader-walk-fn-1:" fn-str)
    fn-str))

(defn- shader-walk-infix [x]
  ;;(println "shader-walk-infix-0:" x)
  (let [fn-str (format "(%s)"
                       (apply str (interpose (format " %s " (str (first x)))
                                             (map #(shader-walk (list %))
                                                  (rest x)))))]
    ;;(println "shader-walk-infix-1:" fn-str)
    fn-str))

(defn- infix-operator? [x]
  (not (nil? (get #{ "+" "-" "*" "/"} x))))

(defn- shader-statement [x]
  (format "%s;\n" (apply str (interpose \  x))))

(defn- shader-walk-times [x]
  nil) ;; FIXME

(defn- shader-walk-while [x]
  nil) ;; FIXME

(defn- shader-walk-if [x]
  nil) ;; FIXME

(defn- shader-walk-switch [x]
  nil) ;; FIXME

(defn- inner-walk
  [x]
  ;;(println "in:  " x)
  (cond
   (list? x)    (let [sfx (str (first x))]
                  (cond
                   (= "slfn" sfx)        (shader-walk-slfn x)
                   (= "slet" sfx)        (shader-walk-let x)
                   (= "sltimes" sfx)     (shader-walk-times x)
                   (= "while" sfx)       (shader-walk-while x)
                   (= "if" sfx)          (shader-walk-if x)
                   (= "switch" sfx)      (shader-walk-switch x)
                   (= "break" sfx)       (shader-statement x)
                   (= "continue" sfx)    (shader-statement x)
                   (= "uniform" sfx)     (shader-statement x)
                   (infix-operator? sfx) (shader-walk-infix x)
                   :else                 (shader-walk-fn x)))
   (symbol? x)  (identity x)
   (float? x)   (identity x)
   (integer? x) (identity x)
   :else        (shader-walk x)))

(defn- outer-walk [x]
  (do
    ;;(println "out: " x)
    (cond
     (list? x)     (apply str x)
     :else         (identity x))))

(defn- shader-walk [form]
  (walk/walk inner-walk outer-walk form))

;; ======================================================================
;; Public API
(defn create-shader
  [& params]
  (shader-walk (first params)))

(defmacro defshader
  "macro to define the fragment shader. returns shader as a string."
  [name body]
  `(def ~name (create-shader ~body)))

;; (str shader) should return the shader text ready to compile

;; ======================================================================
;; For Testing...
(comment
  ;; simplest possible shader
  (defshader simplest
    '((slfn void main []
            (slet [nil gl_FragCoord (vec4 1.0 0.5 0.5 1.0)]
                  nil))))
  (print simplest)

  ;; simple test
  (defshader simple
    '((uniform vec3 iResolution)
      (slfn void main []
            (slet [vec2 uv (/ gl_FragCoord.xy iResolution.xy)
                   nil gl_FragColor (vec4 uv.x uv.y 0.0 1.0)]
             nil))))
  (print simple)

  ;; preliminary translation of wave.glsl
  (defshader wave
    '((uniform vec3 iResolution)
      (uniform sampler2D iChannel0)
      (slfn float smoothbump
            [float center
             float width
             float x]
            (slet [float w2 (/ width 2.0)
                   float cp (+ center w2)
                   float cm (- center w2)]
                  (* (smoothstep cm center x)
                     (- 1.0 (smoothstep center cp x)))))
      (slfn void main
            []
            (slet [float uv     (/ gl_FragCoord.xy iResolution.xy)
                   nil   uv.y   (- 1.0 uv.y)
                   float freq   (.x (texture2D iChannel0 (vec2 uv.x 0.25)))
                   float wave   (.x (texture2D iChannel0 (vec2 uv.x 0.75)))
                   float freqc  (smoothstep 0.0 (/ 1.0 iResolution.y) (+ freq uv.y -0.5))
                   float wavec  (smoothstep 0.0 (/ 4.0 iResolution.y) (+ wave uv.y -0.5))
                   nil   gl_FragColor (vec4 freqc wavec 0.25 1.0)]
                  nil))))
  (print wave)

  )
