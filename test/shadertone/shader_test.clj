(ns shadertone.shader-test
  (:use clojure.test)
  (:require [shadertone.shader :as s]))

(import 'javax.swing.JOptionPane)

(defn ask-user-tf
  "return True on yes, False on fail"
  [prompt]
  (= 0 (JOptionPane/showConfirmDialog
        nil prompt "shader-test" JOptionPane/YES_NO_OPTION)))

(deftest simple-file-test
  (testing "Simple program file acceptance test"
    (let [_ (s/start "test/shadertone/simple.glsl" :textures ["textures/wall.png"])
          good-start (ask-user-tf "Did a pulsing green textured window appear?")
          _ (s/stop)]
      (is good-start))))

(deftest simple-str-test
  (testing "Simple program string acceptance test"
    (let [color (atom [1.0 0.5 0.0 1.0])
          _ (s/start (atom "
uniform float iGlobalTime;
uniform vec4 iColor;
void main(void) {
  gl_FragColor = iColor * abs(sin(iGlobalTime));
}")
                     :user-data { "iColor" color})
          good-start (ask-user-tf "Did a pulsing orange window appear?")
          _ (s/stop)]
      (is good-start))))

(deftest simple-fullscreen-file-test
  (testing "Simple fullscreen program file acceptance test"
    (let [_ (s/start-fullscreen "test/shadertone/simple.glsl" :textures ["textures/wall.png"])
          good-start (ask-user-tf "Did a pulsing green textured fullscreen window appear?")
          _ (s/stop)]
      (is good-start))))

(deftest simple-fullscreen-str-test
  (testing "Simple fullscreen program string acceptance test"
    (let [color (atom [1.0 0.5 0.0 1.0])
          _ (s/start-fullscreen (atom "
uniform float iGlobalTime;
uniform vec4 iColor;
void main(void) {
  gl_FragColor = iColor * abs(sin(iGlobalTime));
}")
                                :user-data { "iColor" color})
          good-start (ask-user-tf "Did a pulsing orange fullscreen window appear?")
          _ (s/stop)]
      (is good-start))))
