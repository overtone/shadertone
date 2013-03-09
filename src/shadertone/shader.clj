(ns shadertone.shader
  (:require ;;[clojure.pprint :as pprint]
            [shadertone.voltap :as voltap]
            [watchtower.core :as watcher])
  (:import (java.nio ByteBuffer FloatBuffer)
           (org.lwjgl BufferUtils)
           (org.lwjgl.opengl ContextAttribs Display DisplayMode
                             GL11 GL15 GL20 
                             PixelFormat)
           (org.lwjgl.util.glu GLU)))

;; ======================================================================
;; State Variables
;; The globals atom is for use in the gl thread
(defonce globals (atom {:active                :no  ;; :yes/:stop/:no
                        :width                 0
                        :height                0
                        :title                 ""
                        :start-time            0
                        :last-time             0
                        ;; geom ids
                        :vbo-id                0
                        :vertices-count        0
                        ;; shader program
                        :shader-filename       ""
                        :vs-id                 0
                        :fs-id                 0
                        :pgm-id                0
                        ;; shader program uniform
                        :i-resolution-loc      0
                        :i-global-time-loc     0
                        :i-overtone-volume-loc 0
                        }))
;; The reload-shader ref communicates across the gl & watcher threads
(defonce reload-shader (ref false))      

;; ======================================================================
(defn init-window
  [width height title shader-filename]
  (let [pixel-format (PixelFormat.)
        context-attributes (-> (ContextAttribs. 2 1)) ;; GL2.1
        current-time-millis (System/currentTimeMillis)]
    (swap! globals
           assoc
           :active          :yes
           :width           width
           :height          height
           :title           title
           :start-time      current-time-millis
           :last-time       current-time-millis
           :shader-filename shader-filename)
    (Display/setDisplayMode (DisplayMode. width height))
    (Display/setTitle title)
    (Display/setVSyncEnabled true)
    (Display/setLocation 0 0)
    (Display/create pixel-format context-attributes)))

(defn init-buffers
  []
  (let [vertices        (float-array
                         [-1.0 -1.0 0.0 1.0
                           1.0 -1.0 0.0 1.0
                          -1.0  1.0 0.0 1.0
                          -1.0  1.0 0.0 1.0
                           1.0 -1.0 0.0 1.0
                           1.0  1.0 0.0 1.0])
        vertices-buffer (-> (BufferUtils/createFloatBuffer (count vertices))
                            (.put vertices)
                            (.flip))
        vertices-count  (count vertices) ;; FIXME
        vbo-id          (GL15/glGenBuffers)
        _               (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo-id)
        _               (GL15/glBufferData GL15/GL_ARRAY_BUFFER
                                           vertices-buffer
                                           GL15/GL_STATIC_DRAW)
        ;;_ (println "init-buffers errors?" (GL11/glGetError))
        ]
    (swap! globals
           assoc
           :vbo-id vbo-id
           :vertices-count vertices-count)))

(def vs-shader
  (str "#version 120\n"
       "attribute vec4 in_Position;\n"
       "void main(void) {\n"
       "    gl_Position = in_Position;\n"
       "}\n"
       ))

(defn slurp-fs
  "do whatever it takes to modify shadertoy fragment shader source to
  be useable"
  [filename]
  (let [file-str (slurp filename)
        file-str (str "#version 120\n"
                      "uniform vec3      iResolution;\n"
                      "uniform float     iGlobalTime;\n" 
                      ;;TODO "uniform float     iChannelTime[4];\n"
                      ;;TODO "uniform vec4      iMouse;\n"
                      ;;TODO "uniform sampler2D iChannel[4];\n"
                      ;;TODO "uniform vec4      iDate;\n"
                      "uniform float     iOvertoneVolume;\n"
                      "\n"
                      file-str)]
    file-str))
                      
(defn load-shader
  [shader-str shader-type]
  (let [shader-id         (GL20/glCreateShader shader-type)
        _                 (GL20/glShaderSource shader-id shader-str)
        _                 (GL20/glCompileShader shader-id)
        gl-compile-status (GL20/glGetShaderi shader-id GL20/GL_COMPILE_STATUS)]
    (when (== gl-compile-status GL11/GL_FALSE)
      (println "ERROR: Loading a Shader:")
      (println (GL20/glGetShaderInfoLog shader-id 10000)))
    shader-id))

(defn init-shaders
  []
  (let [vs-id                 (load-shader vs-shader GL20/GL_VERTEX_SHADER)
        fs-shader             (slurp-fs (:shader-filename @globals))
        _                     (println "Loading" (:shader-filename @globals))
        fs-id                 (load-shader fs-shader GL20/GL_FRAGMENT_SHADER)
        pgm-id                (GL20/glCreateProgram)
        _                     (GL20/glAttachShader pgm-id vs-id)
        _                     (GL20/glAttachShader pgm-id fs-id)
        _                     (GL20/glLinkProgram pgm-id)
        gl-link-status        (GL20/glGetShaderi pgm-id GL20/GL_LINK_STATUS)
        _                     (when (== gl-link-status GL11/GL_FALSE)
                                (println "ERROR: Linking Shaders:")
                                (println (GL20/glGetProgramInfoLog pgm-id 10000)))
        i-resolution-loc      (GL20/glGetUniformLocation pgm-id "iResolution")
        i-global-time-loc     (GL20/glGetUniformLocation pgm-id "iGlobalTime")
        i-overtone-volume-loc (GL20/glGetUniformLocation pgm-id "iOvertoneVolume")
        ;; FIXME add rest of uniforms
        ]
    (swap! globals
           assoc
           :vs-id vs-id
           :fs-id fs-id
           :pgm-id pgm-id
           :i-resolution-loc i-resolution-loc
           :i-global-time-loc i-global-time-loc
           :i-overtone-volume-loc i-overtone-volume-loc)))

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
  (let [{:keys [vs-id fs-id pgm-id shader-filename]} @globals
        fs-shader      (slurp-fs shader-filename)
        new-fs-id      (load-shader fs-shader GL20/GL_FRAGMENT_SHADER)
        new-pgm-id     (GL20/glCreateProgram)
        _              (GL20/glAttachShader new-pgm-id vs-id)
        _              (GL20/glAttachShader new-pgm-id new-fs-id)
        _              (GL20/glLinkProgram new-pgm-id)
        gl-link-status (GL20/glGetShaderi new-pgm-id GL20/GL_LINK_STATUS)]
    (dosync (ref-set reload-shader false))
    (if (== gl-link-status GL11/GL_FALSE)
      (do
        (println "ERROR: Linking Shaders:")
        (println (GL20/glGetProgramInfoLog new-pgm-id 10000))
        (GL20/glUseProgram pgm-id))
      (let [_ (println "Reloading" shader-filename)
            i-resolution-loc (GL20/glGetUniformLocation pgm-id "iResolution")
            i-global-time-loc (GL20/glGetUniformLocation pgm-id "iGlobalTime")
            i-overtone-volume-loc (GL20/glGetUniformLocation pgm-id "iOvertoneVolume")]
        (GL20/glUseProgram new-pgm-id)
        ;; cleanup the old program 
        (GL20/glDetachShader pgm-id vs-id)
        (GL20/glDetachShader pgm-id fs-id)
        (GL20/glDeleteShader fs-id)
        (swap! globals
               assoc
               :fs-id new-fs-id
               :pgm-id new-pgm-id
               :i-resolution-loc i-resolution-loc
               :i-global-time-loc i-global-time-loc
               :i-overtone-volume-loc i-overtone-volume-loc)))))

(defn draw
  []
  (let [{:keys [width height i-resolution-loc
                start-time last-time i-global-time-loc
                i-overtone-volume-loc
                pgm-id vbo-id
                vertices-count
                old-pgm-id old-fs-id]} @globals
                cur-time (/ (- last-time start-time) 1000.0)
                cur-volume (try
                             (float @(get-in voltap/v [:taps "system-vol"]))
                             (catch Exception e 0.0))]
    (if @reload-shader
      (try-reload-shader)         ; this must call glUseProgram
      (GL20/glUseProgram pgm-id)) ; else, normal path...

    (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT  GL11/GL_DEPTH_BUFFER_BIT))
    ;; setup our uniform
    (GL20/glUniform3f i-resolution-loc width height 0) ;; FIXME what is 3rd iResolution param
    (GL20/glUniform1f i-global-time-loc cur-time)
    (GL20/glUniform1f i-overtone-volume-loc cur-volume)
    ;; get vertex array ready
    (GL11/glEnableClientState GL11/GL_VERTEX_ARRAY)
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo-id)
    (GL11/glVertexPointer 4 GL11/GL_FLOAT 0 0)
    ;; Draw the vertices
    (GL11/glDrawArrays GL11/GL_TRIANGLES 0 vertices-count)
    ;; Put everything back to default (deselect)
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
    (GL11/glDisableClientState GL11/GL_VERTEX_ARRAY)
    (GL20/glUseProgram 0)
    ;;(println "draw errors?" (GL11/glGetError))
    ))

(defn update
  []
  (let [{:keys [width height last-time]} @globals
        cur-time (System/currentTimeMillis)]
    (swap! globals assoc :last-time cur-time)
    (draw)))

(defn destroy-gl
  []
  (let [{:keys [pgm-id vs-id fs-id vbo-id]} @globals]
    ;; Delete the shaders
    (GL20/glUseProgram 0)
    (GL20/glDetachShader pgm-id vs-id)
    (GL20/glDetachShader pgm-id fs-id)
    (GL20/glDeleteShader vs-id)
    (GL20/glDeleteShader fs-id)
    (GL20/glDeleteProgram pgm-id)
    ;; Delete the vertex VBO
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
    (GL15/glDeleteBuffers vbo-id)
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
  (swap! globals assoc :active :no))

(defn start-run-thread [width height shader-filename]
  (let [inactive? (= :no (:active @globals))]
    ;; stop the current shader
    (when (not inactive?)
      (swap! globals assoc :active :stop)
      (while (not= :no (:active @globals))
        (Thread/sleep 100)))
    ;; start the requested shader
    ;; FIXME is there a more clojure-tastic way?
    (.start (Thread.
             (fn [] (run width height shader-filename))))))

;; watch the shader directory & reload the current shader if it changes.
(defn if-match-reload-shader
  [files]
  (doseq [f files]
    (when (= (.getPath f) (:shader-filename @globals))
      ;; set a flag that the opengl thread will use
      (dosync (ref-set reload-shader true)))))

(watcher/watcher
 ["shaders/"]
 (watcher/rate 100)
 (watcher/file-filter watcher/ignore-dotfiles)
 (watcher/file-filter (watcher/extensions :glsl))
 (watcher/on-change #(if-match-reload-shader %)))
              
                 
