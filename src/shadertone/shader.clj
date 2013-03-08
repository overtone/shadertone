(ns shadertone.shader
  (:require [clojure.pprint :as pprint]
            [shadertone.voltap :as voltap]
            [watchtower.core :as watcher])
  (:import (java.nio ByteBuffer FloatBuffer)
           (org.lwjgl BufferUtils)
           (org.lwjgl.opengl ContextAttribs Display DisplayMode
                             GL11 GL15 GL20 GL30
                             PixelFormat)
           (org.lwjgl.util.glu GLU)))

;; ======================================================================
(def globals (ref {:active :no}))

;; ======================================================================
(defn init-window
  [width height title shader-filename]
  (let [pixel-format (PixelFormat.)
        context-attributes (-> (ContextAttribs. 3 2)
                               (.withForwardCompatible true)
                               (.withProfileCore true))
        current-time-millis (System/currentTimeMillis)]
    (def globals (ref {:active :yes  ;; :yes / :stop / :no
                       :width width
                       :height height
                       :title title
                       :start-time current-time-millis
                       :last-time current-time-millis
                       ;; geom ids
                       :vao-id 0
                       :vbo-id 0
                       :vboi-id 0
                       :indices-count 0
                       ;; shader program
                       :shader-filename shader-filename
                       :vs-id 0
                       :fs-id 0
                       :pgm-id 0
                       :reload-shader false
                       ;; shader program uniform
                       :i-resolution-loc 0
                       :i-global-time-loc 0
                       :i-overtone-volume-loc 0}))
    (Display/setDisplayMode (DisplayMode. width height))
    (Display/setTitle title)
    (Display/setVSyncEnabled true)
    (Display/setLocation 0 0)
    (Display/create pixel-format context-attributes)))

(defn init-buffers
  []
  (let [vertices (float-array
                  [-1.0 -1.0 0.0 1.0
                    1.0 -1.0 0.0 1.0
                   -1.0  1.0 0.0 1.0
                    1.0 -1.0 0.0 1.0
                    1.0  1.0 0.0 1.0])
        vertices-buffer (-> (BufferUtils/createFloatBuffer (count vertices))
                            (.put vertices)
                            (.flip))
        indices (byte-array
                 (map byte
                      [0 1 2  2 3 4]))
        indices-count (count indices)
        indices-buffer (-> (BufferUtils/createByteBuffer indices-count)
                           (.put indices)
                           (.flip))
        ;; create & bind Vertex Array Object
        vao-id (GL30/glGenVertexArrays)
        _ (GL30/glBindVertexArray vao-id)
        ;; create & bind Vertex Buffer Object for vertices
        vbo-id (GL15/glGenBuffers)
        _ (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo-id)
        _ (GL15/glBufferData GL15/GL_ARRAY_BUFFER vertices-buffer GL15/GL_STATIC_DRAW)
        _ (GL20/glVertexAttribPointer 0 4 GL11/GL_FLOAT false 0 0)
        _ (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
        ;; deselect the VAO
        _ (GL30/glBindVertexArray 0)
        ;; create & bind VBO for indices
        vboi-id (GL15/glGenBuffers)
        _ (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER vboi-id)
        _ (GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER indices-buffer GL15/GL_STATIC_DRAW)
        _ (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
        ;;_ (println "init-buffers errors?" (GL11/glGetError))
        ]
    (dosync (ref-set globals
                     (assoc @globals
                       :vao-id vao-id
                       :vbo-id vbo-id
                       :vboi-id vboi-id
                       :indices-count indices-count)))))

(def vs-shader
  (str "#version 150 core\n"
       "\n"
       "in vec4 in_Position;\n"
       "\n"
       "void main(void) {\n"
       "    gl_Position = in_Position;\n"
       "}\n"
       ))

(defn slurp-shadertoy
  "do whatever it takes to modify shadertoy fragment shader source to
  be useable"
  [filename]
  (let [file-str (slurp filename)
        file-str (.replace file-str "gl_FragColor" "o_FragColor")
        file-str (str "#version 150 core\n"
                      "uniform vec3      iResolution;\n"
                      "uniform float     iGlobalTime;\n" 
                      ;;TODO "uniform float     iChannelTime[4];\n"
                      ;;TODO "uniform vec4      iMouse;\n"
                      ;;TODO "uniform sampler2D iChannel[4];\n"
                      ;;TODO "uniform vec4      iDate;\n"
                      "uniform float     iOvertoneVolume;\n"
                      ;; silly opengl shenanigans
                      "out vec4 o_FragColor;\n\n" 
                      file-str)]
    file-str))
                      
(defn load-shader
  [shader-str shader-type]
  (let [shader-id (GL20/glCreateShader shader-type)
        _ (GL20/glShaderSource shader-id shader-str)
        _ (GL20/glCompileShader shader-id)
        gl-compile-status (GL20/glGetShaderi shader-id GL20/GL_COMPILE_STATUS)
        _ (when (== gl-compile-status GL11/GL_FALSE)
            (println "ERROR: Loading a Shader:")
            (println (GL20/glGetShaderInfoLog shader-id 10000)))
        ]
    shader-id))

(defn init-shaders
  []
  (let [vs-id (load-shader vs-shader GL20/GL_VERTEX_SHADER)
        fs-shader (slurp-shadertoy (:shader-filename @globals))
        _ (println "Loading" (:shader-filename @globals))
        fs-id (load-shader fs-shader GL20/GL_FRAGMENT_SHADER)
        pgm-id (GL20/glCreateProgram)
        _ (GL20/glAttachShader pgm-id vs-id)
        _ (GL20/glAttachShader pgm-id fs-id)
        _ (GL20/glLinkProgram pgm-id)
        gl-link-status (GL20/glGetShaderi pgm-id GL20/GL_LINK_STATUS)
        _ (when (== gl-link-status GL11/GL_FALSE)
            (println "ERROR: Linking Shaders:")
            (println (GL20/glGetProgramInfoLog pgm-id 10000)))
        i-resolution-loc (GL20/glGetUniformLocation pgm-id "iResolution")
        i-global-time-loc (GL20/glGetUniformLocation pgm-id "iGlobalTime")
        i-overtone-volume-loc (GL20/glGetUniformLocation pgm-id "iOvertoneVolume")
        ;; FIXME add rest of uniforms
        ]
    (dosync (ref-set globals
                     (assoc @globals
                       :vs-id vs-id
                       :fs-id fs-id
                       :pgm-id pgm-id
                       :i-resolution-loc i-resolution-loc
                       :i-global-time-loc i-global-time-loc
                       :i-overtone-volume-loc i-overtone-volume-loc
                       )))))

(defn init-gl
  []
  (let [{:keys [width height]} @globals]
    (println "OpenGL version:" (GL11/glGetString GL11/GL_VERSION))
    (GL11/glClearColor 0.0 0.0 0.0 0.0)
    (GL11/glViewport 0 0 width height)
    (init-buffers)
    (init-shaders)
    ;;(print "@globals")
    ;;(pprint/pprint @globals)
    ;;(println "")
    ))

(defn try-reload-shader
  []
  (let [{:keys [vs-id shader-filename]} @globals
        fs-shader (slurp-shadertoy shader-filename)
        fs-id (load-shader fs-shader GL20/GL_FRAGMENT_SHADER)
        pgm-id (GL20/glCreateProgram)
        _ (GL20/glAttachShader pgm-id vs-id)
        _ (GL20/glAttachShader pgm-id fs-id)
        _ (GL20/glLinkProgram pgm-id)
        gl-link-status (GL20/glGetShaderi pgm-id GL20/GL_LINK_STATUS)
        ]
    (if (== gl-link-status GL11/GL_FALSE)
      (do
        (println "ERROR: Linking Shaders:")
        (println (GL20/glGetProgramInfoLog pgm-id 10000))
        (dosync (ref-set globals (assoc @globals
                                   :reload-shader false))))
      (do
        (println "Reloading" shader-filename)
        (dosync (ref-set globals
                         (assoc @globals
                           :fs-id fs-id
                           :pgm-id pgm-id
                           :reload-shader false)))))))

(defn draw
  []
  (let [{:keys [width height i-resolution-loc
                start-time last-time i-global-time-loc
                i-overtone-volume-loc
                pgm-id vao-id vboi-id
                indices-count reload-shader]} @globals
                cur-time (/ (- last-time start-time) 1000.0)
                cur-volume (try
                             (float @(get-in voltap/v [:taps "system-vol"]))
                             (catch Exception e 0.0))
                ;;_ (println "cur-volume" cur-volume)
                ]
    (if reload-shader
      (try-reload-shader))
    
    (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT  GL11/GL_DEPTH_BUFFER_BIT))

    (GL20/glUseProgram pgm-id)
    ;; setup our uniform
    (GL20/glUniform3f i-resolution-loc width height 0) ;; FIXME what is 3rd iResolution param
    (GL20/glUniform1f i-global-time-loc cur-time)
    (GL20/glUniform1f i-overtone-volume-loc cur-volume)
    ;; Bind to the VAO that has all the information about the
    ;; vertices
    (GL30/glBindVertexArray vao-id)
    (GL20/glEnableVertexAttribArray 0)
    ;; Bind to the index VBO that has all the information about the
    ;; order of the vertices
    (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER vboi-id)
    ;; Draw the vertices
    (GL11/glDrawElements GL11/GL_TRIANGLES indices-count GL11/GL_UNSIGNED_BYTE 0)
    ;; Put everything back to default (deselect)
    (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
    (GL20/glDisableVertexAttribArray 0)
    (GL30/glBindVertexArray 0)
    (GL20/glUseProgram 0)
    ;;(println "draw errors?" (GL11/glGetError))
    ))

(defn update
  []
  (let [{:keys [width height last-time]} @globals
        cur-time (System/currentTimeMillis)]
    (dosync (ref-set globals (assoc @globals :last-time cur-time)))
    (draw)))

(defn destroy-gl
  []
  (let [{:keys [pgm-id vs-id fs-id vao-id vbo-id vboi-id]} @globals]
    ;; Delete the shaders
    (GL20/glUseProgram 0)
    (GL20/glDetachShader pgm-id vs-id)
    (GL20/glDetachShader pgm-id fs-id)
 
    (GL20/glDeleteShader vs-id)
    (GL20/glDeleteShader fs-id)
    (GL20/glDeleteProgram pgm-id)
 
    ;; Select the VAO
    (GL30/glBindVertexArray vao-id)
 
    ;; Disable the VBO index from the VAO attributes list
    (GL20/glDisableVertexAttribArray 0)
 
    ;; Delete the vertex VBO
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
    (GL15/glDeleteBuffers vbo-id)
 
    ;; Delete the index VBO
    (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
    (GL15/glDeleteBuffers vboi-id)
 
    ;; Delete the VAO
    (GL30/glBindVertexArray 0)
    (GL30/glDeleteVertexArrays vao-id)
    ))

(defn run
  [width height shader-filename]
  (init-window width height "shadertone" shader-filename)
  (init-gl)
  (while (and (= :yes (:active @globals))
              (not (Display/isCloseRequested)))
    (update)
    (Display/update)
    (Display/sync 60))
  (destroy-gl)
  (Display/destroy)
  (dosync (ref-set globals (assoc @globals :active :no))))

(defn start-run-thread [width height shader-filename]
  (let [inactive? (= :no (:active @globals))]
    ;; stop the current shader
    (when (not inactive?)
      (dosync (ref-set globals (assoc @globals :active :stop)))
      (while (not= :no (:active @globals))
        (Thread/sleep 100)))
    ;; start the requested shader
    ;; FIXME -- find a more clojure-tastic way
    (.start (Thread.
             (fn [] (run width height shader-filename))))))

;; (start-run-thread 800 800 "shaders/simple.glsl")
;; (start-run-thread 800 800 "shaders/quasicrystal.glsl")

(defn if-match-reload-shader
  [files]
  (doseq [f files]
    (when (= (.getPath f) (:shader-filename @globals))
      ;; set a flag that the opengl thread will use
      (dosync (ref-set globals (assoc @globals :reload-shader true))))))

(watcher/watcher
 ["shaders/"]
 (watcher/rate 100)
 (watcher/file-filter watcher/ignore-dotfiles)
 (watcher/file-filter (watcher/extensions :glsl))
 (watcher/on-change #(if-match-reload-shader %)))
              
                 
