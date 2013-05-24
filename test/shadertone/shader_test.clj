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
  (testing "Simple acceptance test"
    (let [_ (s/start "test/shadertone/simple.glsl")
          good-start (ask-user-tf "Did a pulsing green window appear?")
          _ (s/stop)]
      (is good-start))))

(deftest simple-str-test
  (testing "Simple acceptance test"
    (let [_ (s/start nil :shader-str "
uniform float iGlobalTime;
void main(void) {
  gl_FragColor = vec4(1.0,0.5,0.0,1.0) * abs(sin(iGlobalTime));
}")
          good-start (ask-user-tf "Did a pulsing orange window appear?")
          _ (s/stop)]
      (is good-start))))

(deftest simple-fullscreen-file-test
  (testing "Simple acceptance test"
    (let [_ (s/start-fullscreen "test/shadertone/simple.glsl")
          good-start (ask-user-tf "Did a pulsing green fullscreen window appear?")
          _ (s/stop)]
      (is good-start))))

(deftest simple-fullscreen-str-test
  (testing "Simple acceptance test"
    (let [_ (s/start-fullscreen nil :shader-str "
uniform float iGlobalTime;
void main(void) {
  gl_FragColor = vec4(1.0,0.5,0.0,1.0) * abs(sin(iGlobalTime));
}")
          good-start (ask-user-tf "Did a pulsing orange fullscreen window appear?")
          _ (s/stop)]
      (is good-start))))
