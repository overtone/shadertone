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
uniform float iTime;
uniform vec4 iColor;
void main(void) {
  gl_FragColor = iColor * abs(sin(iTime));
}")
                     :user-data { "iColor" color})
          good-start (ask-user-tf "Did a pulsing orange window appear?")
          _ (s/stop)]
      (is good-start))))

(deftest pixel-read-test
  (testing "Simple pixel-read test"
    (let [color (atom [1.0 (/ 128.0 255.0) 0.0 1.0])
          _ (s/start (atom "
uniform vec4 iColor;
void main(void) {
  gl_FragColor = iColor;
}")
                     :user-data { "iColor" color})
          _ (s/pixel-read-enable! 10 10)
          _ (Thread/sleep 1000)
          rgb1 (s/pixel)
          rgb2 (deref s/pixel-value)
          _ (s/pixel-read-disable!)
          _ (Thread/sleep 100)
          rgb3 (s/pixel)
          _ (s/stop)]
      (is (= (vec (take 3 @color)) rgb1))
      (is (= (vec (take 3 @color)) rgb2))
      (is (= [0.0 0.0 0.0] rgb3)))))

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
uniform float iTime;
uniform vec4 iColor;
void main(void) {
  gl_FragColor = iColor * abs(sin(iTime));
}")
                                :user-data { "iColor" color})
          good-start (ask-user-tf "Did a pulsing orange fullscreen window appear?")
          _ (s/stop)]
      (is good-start))))

(deftest switch-str-test
  (testing "Test switching shaders"
    (let [shader-atom (atom "
uniform float iTime;
void main(void) {
  gl_FragColor = vec4(1.0,1.0,1.0,1.0) * abs(sin(iTime));
}")
          _ (s/start shader-atom)
          good-start (ask-user-tf "Did a pulsing window appear?")
          _ (reset! shader-atom "
void main(void) {
  gl_FragColor = vec4(0.8,0.8,0.0,1.0);
}")
          good-start2 (ask-user-tf "Did a steady yellow window draw?")
          _ (reset! shader-atom "
uniform float iTime;
void main(void) {
  gl_FragColor = vec4(1.0,1.0,1.0,1.0) * abs(sin(iTime));
}")
          good-start3 (ask-user-tf "Did the pulsing start again?")
          _ (s/stop)]
      (is (and good-start good-start2 good-start3)))))

(deftest error-str-test
  (testing "Test error handling"
    (let [shader-atom (atom "
uniform float iTime;
void main(void) {
  gl_FragColor = vec4(1.0,1.0,1.0,1.0) * noise * abs(sin(iTime));
}")
          _ (s/start shader-atom)
          good-start (ask-user-tf "Did an error occur? That is expected, but we should draw a black screen and not throw an exception.")
          _ (reset! shader-atom "
uniform float iTime;
void main(void) {
  gl_FragColor = vec4(1.0,1.0,1.0,1.0) * abs(sin(iTime));
}")
          good-start2 (ask-user-tf "Did a pulsing window appear?")
          _ (reset! shader-atom "
uniform float iTime;
void main(void) {
  gl_FragColor = vec4(1.0,1.0,1.0,1.0) * noise * abs(sin(iTime));
}")
          good-start3 (ask-user-tf "Did another error occur? That is expected, but we should just keep playing the pulsing window.")
          _ (s/stop)]
      (is (and good-start good-start2 good-start3)))))

(deftest error-str-test2
  (testing "Test error handling with uniforms"
    (let [color (atom [1.0 0.5 0.0 1.0])
          shader-atom (atom "
uniform float iTime;
//uniform vec4 iColor;
void main(void) {
  gl_FragColor = iColor * abs(sin(iTime));
}")
          _ (s/start shader-atom :user-data { "iColor" color})
          good-start (ask-user-tf "Did an error occur? That is expected, but we should draw a black screen and not throw an exception.")
          _ (reset! shader-atom "
uniform float iTime;
uniform vec4 iColor;
void main(void) {
  gl_FragColor = iColor * abs(sin(iTime));
}")
          good-start2 (ask-user-tf "Did a pulsing orange window appear?")
          _ (reset! shader-atom "
uniform float iTime;
//uniform vec4 iColor;
void main(void) {
  gl_FragColor = iColor * abs(sin(iTime));
}")
          good-start3 (ask-user-tf "Did another error occur? That is expected, but we should just keep playing the pulsing window.")
          _ (s/stop)]
      (is (and good-start good-start2 good-start3)))))
