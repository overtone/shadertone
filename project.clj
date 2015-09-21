(defproject shadertone "0.2.6-SNAPSHOT"
  :description "A clojure library designed to mix musical synthesis via Overtone and dynamic visuals a la www.shadertoy.com"
  :url "http://github.com/overtone/shadertone"
  :license {:name "MIT License"
            :url "https://github.com/overtone/shadertone/blob/master/LICENSE"}
  :dependencies [;; 1.6.0 causes error with *warn-on-reflection*.  1.7.0-RC1 works
                 [org.clojure/clojure "1.5.1"]
                 [hello_lwjgl/lwjgl   "2.9.1"]
                 [overtone            "0.9.1"]
                 [watchtower          "0.1.1"]]
  :main ^{:skip-aot true} shadertone.core
  ;; add per WARNING: JVM argument TieredStopAtLevel=1 is active...
  :jvm-opts ^:replace []
  ;; add leipzig for use in examples
  :profiles {:dev {:dependencies [[leipzig "0.6.0" :exclusions [[overtone]]]]
                   ;; enabling this outputs a lot of spew. disable for normal runs
                   ;; :global-vars {*warn-on-reflection* true *assert* false}
                   }})
