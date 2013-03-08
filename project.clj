(defproject shadertone "0.1.0-SNAPSHOT"
  :description "shadertoy + overtone"
  :url "http://github.com/rogerallen/shadertoneo"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.lwjgl.lwjgl/lwjgl "2.8.5"]
                 [org.lwjgl.lwjgl/lwjgl_util "2.8.5"]
                 [org.lwjgl.lwjgl/lwjgl-platform "2.8.5"
                  :classifier "natives-osx"]
                 [overtone "0.9.0-SNAPSHOT"]
                 [watchtower "0.1.1"]])
