(defproject shadertone "0.2.2"
  :description "A clojure library designed to mix musical synthesis via Overtone and dynamic visuals a la www.shadertoy.com"
  :url "http://github.com/overtone/shadertone"
  :license {:name "MIT License"
            :url "https://github.com/overtone/shadertone/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.lwjgl.lwjgl/lwjgl "2.9.0"]
                 [org.lwjgl.lwjgl/lwjgl_util "2.9.0"]
                 [shadertone/lwjgl-natives "2.9.0"] ;; since org.lwjgl puts the natives in a bad spot
                 [overtone "0.9.1"]
                 [watchtower "0.1.1"]]
  :main ^{:skip-aot true} shadertone.core
  ;; add leipzig for use in examples
  :profiles {:dev {:dependencies [[leipzig "0.6.0"]]}})
