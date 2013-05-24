(ns shadertone.tone-test
  (:use clojure.test
        overtone.live)
  (:require [shadertone.tone :as t]))

(import 'javax.swing.JOptionPane)

(defn ask-user-tf
  "return True on yes, False on fail"
  [prompt]
  (= 0 (JOptionPane/showConfirmDialog
        nil prompt "shader-test" JOptionPane/YES_NO_OPTION)))

(defsynth simple-synth
  [gate 1]
  (let [d (* 255 (abs (sin-osc:kr (/ 6.28))))
        _ (tap "d" 60 d)]
    (out 0 (pan2 (* (env-gen (adsr 1.0 0.1 0.8 1.0) :gate gate :action FREE)
                    (sin-osc (+ 255 d)))))))

(deftest simple-file-test
  (testing "Simple acceptance test"
    (let [s (simple-synth)
          _ (t/start "test/shadertone/simple.glsl")
          good-start (ask-user-tf "Do you hear a siren-ish sound AND did a pulsing green window appear?")
          _ (ctl s :gate 0)
          _ (t/stop)]
      (is good-start))))

(deftest simple-str-test
  (testing "Simple acceptance test"
    (let [s (simple-synth)
          _ (t/start nil :shader-str "
uniform float iGlobalTime;
void main(void) {
  gl_FragColor = vec4(1.0,0.5,0.0,1.0) * abs(sin(iGlobalTime));
}")
          good-start (ask-user-tf "Do you hear a siren-ish sound AND did a pulsing orange window appear?")
          _ (ctl s :gate 0)
          _ (t/stop)]
      (is good-start))))

(deftest simple-fullscreen-file-test
  (testing "Simple acceptance test"
    (let [s (simple-synth)
          _ (t/start-fullscreen "test/shadertone/simple.glsl")
          good-start (ask-user-tf "Do you hear a siren-ish sound AND did a fullscreen pulsing green window appear?")
          _ (ctl s :gate 0)
          _ (t/stop)]
      (is good-start))))

(deftest simple-fullscreen-str-test
  (testing "Simple acceptance test"
    (let [s (simple-synth)
          _ (t/start-fullscreen nil :shader-str "
uniform float iGlobalTime;
void main(void) {
  gl_FragColor = vec4(1.0,0.5,0.0,1.0) * abs(sin(iGlobalTime));
}")
          good-start (ask-user-tf "Do you hear a siren-ish sound AND did a fullscreen pulsing orange window appear?")
          _ (ctl s :gate 0)
          _ (t/stop)]
      (is good-start))))
