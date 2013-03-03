(ns shadertone.shader
  (:require [clojure.pprint :as pprint])
  (:import (java.nio ByteBuffer FloatBuffer)
           (org.lwjgl BufferUtils)
           (org.lwjgl.opengl ContextAttribs Display DisplayMode GL11 GL15 GL20 GL30 PixelFormat)
           (org.lwjgl.util.glu GLU)))

;; ======================================================================
(defn init-window
  [width height title]
  (let [pixel-format (PixelFormat.)
        context-attributes (-> (ContextAttribs. 3 2)
                               (.withForwardCompatible true)
                               (.withProfileCore true))
        current-time-millis (System/currentTimeMillis)]
    (def globals (ref {:width width
                       :height height
                       :title title
                       :start-time current-time-millis
                       :last-time current-time-millis
                       ;; geom ids
                       :vao-id 0
                       :vbo-id 0
                       :vboi-id 0
                       :indices-count 0
                       ;; shader program ids
                       :vs-id 0
                       :fs-id 0
                       :p-id 0
                       ;; shader program uniform
                       :i-resolution-loc 0
                       :i-global-time-loc 0}))
    (Display/setDisplayMode (DisplayMode. width height))
    (Display/setTitle title)
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

(def fs-shader
  (str "#version 150 core\n"
       "\n"
       "uniform vec3      iResolution;\n"
       "uniform float     iGlobalTime;\n" 
       ;;"uniform float     iChannelTime[4];\n"
       ;;"uniform vec4      iMouse;\n"
       ;;"uniform sampler2D iChannel[4];\n"
       ;;"uniform vec4      iDate;\n"
       "out vec4 o_FragColor;\n"
       "\n"
       "void main(void) {\n"
       "  vec2 vUV = (gl_FragCoord.xy / iResolution.xy);\n"
       "  vec4 c = vec4(vUV, sin(iGlobalTime), 1.0);\n"
       "  o_FragColor = c;\n"
       "}\n"
       ))
                     
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
        fs-id (load-shader fs-shader GL20/GL_FRAGMENT_SHADER)
        p-id (GL20/glCreateProgram)
        _ (GL20/glAttachShader p-id vs-id)
        _ (GL20/glAttachShader p-id fs-id)
        _ (GL20/glLinkProgram p-id)
        gl-link-status (GL20/glGetShaderi p-id GL20/GL_LINK_STATUS)
        _ (when (== gl-link-status GL11/GL_FALSE)
            (println "ERROR: Linking Shaders:")
            (println (GL20/glGetProgramInfoLog p-id 10000)))
        i-resolution-loc (GL20/glGetUniformLocation p-id "iResolution")
        i-global-time-loc (GL20/glGetUniformLocation p-id "iGlobalTime")
        ;; FIXME add rest of uniforms
        ]
    (dosync (ref-set globals
                     (assoc @globals
                       :vs-id vs-id
                       :fs-id fs-id
                       :p-id p-id
                       :i-resolution-loc i-resolution-loc
                       :i-global-time-loc i-global-time-loc 
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

(defn draw
  []
  (let [{:keys [width height i-resolution-loc
                start-time last-time i-global-time-loc
                p-id vao-id vboi-id
                indices-count]} @globals
                w2 (/ width 2.0)
                h2 (/ height 2.0)
                cur-time (/ (- last-time start-time) 1000.0)]
    
    (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT  GL11/GL_DEPTH_BUFFER_BIT))

    (GL20/glUseProgram p-id)
    ;; setup our uniform
    (GL20/glUniform3f i-resolution-loc width height 0) ;; FIXME what is 3rd iResolution param
    (GL20/glUniform1f i-global-time-loc cur-time)
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
  (let [{:keys [p-id vs-id fs-id vao-id vbo-id vboi-id]} @globals]
    ;; Delete the shaders
    (GL20/glUseProgram 0)
    (GL20/glDetachShader p-id vs-id)
    (GL20/glDetachShader p-id fs-id)
 
    (GL20/glDeleteShader vs-id)
    (GL20/glDeleteShader fs-id)
    (GL20/glDeleteProgram p-id)
 
    ;; Select the VAO
    (GL30/glBindVertexArray vao-id)
 
    ;; Disable the VBO index from the VAO attributes list
    (GL20/glDisableVertexAttribArray 0)
    (GL20/glDisableVertexAttribArray 1)
 
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
  []
  (init-window 800 600 "shadertone")
  (init-gl)
  (Thread/sleep 2000)
  (while (not (Display/isCloseRequested))
    (update)
    (Display/update)
    (Display/sync 60))
  (destroy-gl)
  (Display/destroy))

(defn start-run-thread []
  (.start (Thread. run)))

;; (start-run-thread)
