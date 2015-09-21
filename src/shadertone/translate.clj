(ns #^{:author "Roger Allen"
       :doc "Shadertone lisp-to-glsl translation."}
  shadertone.translate
  (:require [clojure.walk :as walk]
            [clojure.string :as string]))

;; This is only a single-pass "lisp" to GLSL translator.  Very basic.
;; If useful, then perhaps we can work to improve.  Send feedback.
;;
;; Basic "docs".  See testcases, too.
;;  * define functions
;;    (defn <return-type> <function-name> <function-args-vector> <body-stmt1> ... )
;;  * function calls
;;    (<name> <arg1> <arg2> ... )
;;  * variable creation/assignment
;;    (uniform <type> <name>)
;;    (setq <type> <name> <statement>)
;;    (setq <name> <statement>)
;;  * for(;;) {}
;;    (forloop [ <init-stmt> <test-stmt> <step-stmt> ] <body-stmt1> ... )
;;  * while() {}
;;    (while <test-stmt> <body-stmt1> ... )
;;  * break;
;;    (break)
;;  * continue;
;;    (continue)
;;  * return value;
;;    (return <statement>)
;;  * if() {}
;;    (if <test> <stmt>)
;;    (if <test> (do <body-stmt1> ...))
;;  * if() {} else {}
;;    (if <test> <stmt> <else-stmt>)
;;    (if <test> (do <body-stmt1> ...) (do <else-stmt1> ...))
;;  * switch () { case integer: ... break; ... default: ... }
;;    (switch <test> <case-int-1> <case-stmt-1> ...)
;;    cases can only be integer or :default keyword
;; TODO:
;;   o indent spacing
;;   o tests
;;   o assertions?
;;   o defensive coding
;;   o error reports

;; ======================================================================
;; translation functions for a dialect of clojure-like s-expressions
(declare shader-walk)

(defn- shader-typed-assign-str [z]
  (let [[type name value] z
        _ (assert (= 3 (count z)))
        ;;_ (println "shader-assign-str-0:" type name value)
        asn-str (format "%s %s = %s;\n"
                          type name
                          (shader-walk (list value)))]
    ;;(println "shader-assign-str-1:" asn-str)
    asn-str))

(defn- shader-assign-str [z]
  (let [[name value] z
        _ (assert (= 2 (count z)))
        ;;_ (println "shader-assign-str-0:" name value)
        asn-str (format "%s = %s;\n"
                        name
                        (shader-walk (list value)))]
    ;;(println "shader-assign-str-1:" asn-str)
    asn-str))

(defn- shader-walk-assign [x]
  ;;(println "shader-walk-assign-0:" x)
  (case (count (rest x))
    2 (shader-assign-str (rest x))
    3 (shader-typed-assign-str (rest x))
    :else (assert false "incorrect number of args for setq statement")))

(defn- shader-walk-defn-args [x]
  ;;(println "shader-walk-defn-args-0" x (empty? x))
  (assert (vector? x))
  (if (empty? x)
    "void"
    (string/join \, (map #(apply (partial format "%s %s") %) (partition 2 x)))))

(defn- shader-walk-defn [x]
  ;;(println "shader-walk-defn-0:" x)
  (let [fn-str (format "%s %s(%s) {\n%s}\n"
                       (nth x 1)
                       (nth x 2)
                       (shader-walk-defn-args (nth x 3))
                       (string/join (shader-walk (drop 4 x))))]  ;; FIXME add indentation level?
   ;;(print "shader-walk-defn-1:" fn-str)
   fn-str))

(defn- shader-walk-fn [x]
  ;;(println "shader-walk-fn-0:" x)
  (let [pre-fn (if (= (first (str (first x))) \.) "" (str (first x)))
        post-fn (if (= (first (str (first x))) \.) (str (first x)) "")
        fn-str (format "%s(%s)%s"
                       pre-fn
                       (string/join
                        \,
                        (map #(shader-walk (list %)) (rest x)))
                       post-fn)]
    ;;(println "shader-walk-fn-1:" fn-str)
    fn-str))

(defn- shader-walk-infix [x]
  ;;(println "shader-walk-infix-0:" x)
  (let [identity-value (case (str (first x))
                           "*" 1.0
                           "/" 1.0
                           0.0)
        vals   (if (> (count (rest x)) 1)
                 (rest x)
                 (cons identity-value (rest x)))
        fn-str (format "(%s)"
                       (string/join
                        (format " %s " (str (first x)))
                        (map #(shader-walk (list %)) vals)))]
    ;;(println "shader-walk-infix-1:" fn-str)
    fn-str))

(defn- infix-operator? [x]
  (not (nil? (get #{ "+" "-" "*" "/" "=" "<" ">" "<=" ">=" "==" "!=" ">>" "<<"} x))))

(defn- shader-stmt [x]
  (format "%s;\n" (string/join \space x)))

;; (forloop [ init-stmt test-stmt step-stmt ] body )
(defn- shader-walk-forloop [x]
  ;;(println "shader-walk-forloop-0:" x)
  (let [[init-stmt test-stmt step-stmt] (nth x 1)
        fl-str (format "for( %s %s; %s ) {\n%s}\n"
                       (shader-walk (list init-stmt))
                       (shader-walk (list test-stmt))
                       (shader-walk (list step-stmt))
                       (string/join (shader-walk (drop 2 x))))]
    ;;(print "shader-walk-forloop-1:" fl-str)
    fl-str))

;; (whileloop test-stmt body )
(defn- shader-walk-while [x]
  ;;(println "shader-walk-while-0:" x)
  (let [w-str (format "while%s {\n%s}\n"
                      (shader-walk (list (nth x 1)))
                      (string/join (shader-walk (drop 2 x))))]
    ;;(println "shader-walk-while-1:" w-str)
    w-str))

(defn- shader-walk-do [x]
  ;;(println "shader-walk-do-0:" x)
  (let [w-str (format "{\n%s}\n" (string/join (shader-walk (drop 1 x))))]
    ;;(println "shader-walk-do-1:" w-str)
    w-str))

(defn- shader-walk-if [x]
  ;;(println "shader-walk-if-0:" x)
  (case (count (rest x))
    2  (let [w-str (format "if%s\n%s" ;; if() {}
                           (shader-walk (list (nth x 1)))
                           (shader-walk (list (nth x 2))))]
         ;;(println "shader-walk-if-1a:" w-str)
         w-str)
    3  (let [w-str (format "if%s\n%selse\n%s" ;; if() {} else {}
                           (shader-walk (list (nth x 1)))
                           (shader-walk (list (nth x 2)))
                           (shader-walk (list (nth x 3))))]
         ;;(println "shader-walk-if-1b:" w-str)
         w-str)
    :else (assert false "incorrect number of args for if statement")))

(defn- shader-walk-case [x]
  ;;(println "shader-walk-case-0:" x)
  (let [[v s] x
        _ (assert (= 2 (count x)))
        c-str (if (number? v)
                (format "case %d:" v)
                (if (= v :default)
                  "default:"
                  (assert false (format "expected integer or default:, got: %s" v))))
        w-str (format "%s\n%s"
                      c-str
                      (shader-walk (list s)))]
    ;;(println "shader-walk-case-1:" w-str)
    w-str))

(defn- shader-walk-switch [x]
  ;;(println "shader-walk-switch-0:" x)
  (let [v     (nth x 1)
        v-str (if (list? v)
                (shader-walk (list v))
                (format "(%s)" (shader-walk (list v))))
        w-str (format "switch%s {\n%s}\n"
                      v-str
                      (string/join (map shader-walk-case (partition 2 (drop 2 x)))))]
    ;;(println "shader-walk-switch-1:" w-str)
    w-str))

(defn- shader-walk-return [x]
  (format "%s;\n" (shader-walk-fn x)))

(defn- inner-walk
  [x]
  ;;(println "in:  " x)
  (cond
   (list? x)    (let [sfx (str (first x))]
                  (cond
                   (= "defn" sfx)        (shader-walk-defn x)
                   (= "setq" sfx)        (shader-walk-assign x)
                   (= "forloop" sfx)     (shader-walk-forloop x)
                   (= "while" sfx)       (shader-walk-while x)
                   (= "if" sfx)          (shader-walk-if x)
                   (= "do" sfx)          (shader-walk-do x)
                   (= "switch" sfx)      (shader-walk-switch x)
                   (= "break" sfx)       (shader-stmt x)
                   (= "continue" sfx)    (shader-stmt x)
                   (= "uniform" sfx)     (shader-stmt x)
                   (= "return" sfx)      (shader-walk-return x)
                   (infix-operator? sfx) (shader-walk-infix x)
                   :else                 (shader-walk-fn x)))
   (symbol? x)  (identity x)
   (float? x)   (identity x)
   (integer? x) (identity x)
   :else        (shader-walk x)))

(defn- outer-walk [x]
  ;;(println "out: " x)
  (cond
   (list? x)     (string/join x)
   :else         (identity x)))

(defn- shader-walk [form]
  (walk/walk inner-walk outer-walk form))

;; ======================================================================
;; Public API
(defn create-shader
  [& params]
  (shader-walk (first params)))

;; ??? Maybe we should just call create-shader and get a string back?
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
    '((defn void main [] (setq gl_FragColor (vec4 1.0 0.5 0.5 1.0)))))
  (print simplest)

  ;; simple test
  (defshader simple
    '((uniform vec3 iResolution)
      (defn void main []
            (setq vec2 uv (/ gl_FragCoord.xy iResolution.xy))
            (setq gl_FragColor (vec4 uv.x uv.y 0.0 1.0)))))
  (print simple)

  ;; simple unary test
  (defshader simple
    '((uniform vec3 iResolution)
      (defn void main []
        (setq vec2 uv (/ gl_FragCoord.xy iResolution.xy))
        (setq float vs (- uv.y))
        (setq float va (+ vs))
        (setq float vm (* va))
        (setq float vd (/ vm))
        (setq float vn (- 0.0 vd))
        (setq gl_FragColor (vec4 uv.x vn 0.0 1.0)))))
  (print simple)

  ;; preliminary translation of wave.glsl
  (defshader wave
    '((uniform vec3 iResolution)
      (uniform sampler2D iChannel0)
      (defn float smoothbump
        [float center
         float width
         float x]
        (setq float w2 (/ width 2.0))
        (setq float cp (+ center w2))
        (setq float cm (- center w2))
        (return (* (smoothstep cm center x)
                   (- 1.0 (smoothstep center cp x)))))
      (defn void main []
        (setq float uv     (/ gl_FragCoord.xy iResolution.xy))
        (setq uv.y   (- 1.0 uv.y))
        (setq float freq   (.x (texture2D iChannel0 (vec2 uv.x 0.25))))
        (setq float wave   (.x (texture2D iChannel0 (vec2 uv.x 0.75))))
        (setq float freqc  (smoothstep 0.0 (/ 1.0 iResolution.y) (+ freq uv.y -0.5)))
        (setq float wavec  (smoothstep 0.0 (/ 4.0 iResolution.y) (+ wave uv.y -0.5)))
        (setq gl_FragColor (vec4 freqc wavec 0.25 1.0)))))
  (print wave)

  (defshader forloop0
    '((defn void main []
        (setq vec3 c (vec3 0.0))
        (forloop [ (setq int i 0)
                   (<= i 10)
                   (setq i (+ i 1)) ]
                 (setq c (+ c (vec3 0.1))))
        (setq gl_FragColor (vec4 c 1.0)))))
  (print forloop0)

  (defshader iftest0
    '((defn void main []
        (if (< i 0) (setq i 0))
        (if (< j 10)
          (do
            (setq i 5)
            (setq j 10)))
        (if (< k 10)
          (setq i 5)
          (setq j 10))
        (if (< k 10)
          (do
            (setq i 1)
            (setq j 2))
          (do
            (setq i 3)
            (setq j 4))))))
   (print iftest0)

  (defshader swtest0
    '((defn void main []
        (switch j
         0 (do (setq i 0)
               (break))
         1 (do (setq i 1)
               (break))
         :default (break))
        (switch (+ j k)
         0 (do (setq l 0)
               (break))
         :default (break)))))
  (print swtest0)

  )
