(defproject shadertone "0.2.4-SNAPSHOT"
  :description "A clojure library designed to mix musical synthesis via Overtone and dynamic visuals a la www.shadertoy.com"
  :url "http://github.com/overtone/shadertone"
  :license {:name "MIT License"
            :url "https://github.com/overtone/shadertone/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [hello_lwjgl/lwjgl "2.9.1"]
                 [overtone "0.9.1"]
                 [watchtower "0.1.1"]]
  :main ^{:skip-aot true} shadertone.core
  ;; add leipzig for use in examples
  :profiles {:dev {:dependencies [[leipzig "0.6.0"]]}})
