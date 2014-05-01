(ns #^{:author "Roger Allen"
       :doc "Shadertoy-like core library."}
  shadertone.shader
  (:require [watchtower.core :as watcher]
            clojure.string)
  (:import (java.awt.image BufferedImage DataBuffer DataBufferByte)
           (java.io File FileInputStream)
           (java.nio IntBuffer FloatBuffer ByteOrder)
           (java.util Calendar)
           (javax.imageio ImageIO)
           (java.lang.reflect Field)
           (org.lwjgl BufferUtils)
           (org.lwjgl.input Mouse)
           (org.lwjgl.opengl ContextAttribs Display DisplayMode
                             GL11 GL12 GL13 GL15 GL20
                             PixelFormat)))

;; ======================================================================
;; some code cribbed from
;; https://github.com/ztellman/penumbra/blob/master/src/penumbra/opengl/core.clj
;; Causes errors in glGetError to throw an exception
(def containers [GL11 GL12 GL13 GL15 GL20])
(defn- get-fields [#^Class static-class]
  (. static-class getFields))
(defn- enum-name
  "Takes the numeric value of a gl constant (i.e. GL_LINEAR), and gives the name"
  [enum-value]
  (if (= 0 enum-value)
    "NONE"
    (.getName
     #^Field (some
              #(if (= enum-value (.get #^Field % nil)) % nil)
              (mapcat get-fields containers)))))
(defn- except-gl-errors
  [msg]
  (let [error (GL11/glGetError)
        error-string (str "OpenGL Error(" error "):" (enum-name error) ": " msg)]
    (if (not (zero? error))
      (throw (Exception. error-string)))))

;; ======================================================================
;; State Variables
;; The globals atom is a map of state variables for use in the gl thread
(defonce globals (atom {:active              :no  ;; :yes/:stopping/:no
                        :shader-good         true ;; false in error condition
                        :width               0
                        :height              0
                        :title               ""
                        :display-sync-hz     60
                        :start-time          0
                        :last-time           0
                        ;; mouse
                        :mouse-clicked       false
                        :mouse-pos-x         0
                        :mouse-pos-y         0
                        :mouse-ori-x         0
                        :mouse-ori-y         0
                        ;; geom ids
                        :vbo-id              0
                        :vertices-count      0
                        ;; shader program
                        :shader-filename     nil
                        :shader-str-atom     (atom nil)
                        :shader-str          ""
                        :vs-id               0
                        :fs-id               0
                        :pgm-id              0
                        ;; shader uniforms
                        :i-resolution-loc    0
                        :i-global-time-loc   0
                        :i-channel-time-loc  0
                        :i-mouse-loc         0
                        :i-channel-loc       [0 0 0 0]
                        :i-date-loc          0
                        :channel-time-buffer (-> (BufferUtils/createFloatBuffer 4)
                                                 (.put (float-array
                                                        [0.0 0.0 0.0 0.0]))
                                                 (.flip))
                        ;; textures
                        :tex-filenames       []
                        :tex-ids             []
                        :tex-types           [] ; :cubemap, :previous-frame
                        ;; a user draw function
                        :user-fn             nil
                        }))
;; The reload-shader ref communicates across the gl & watcher threads
(defonce reload-shader (atom false))
(defonce reload-shader-str (atom ""))
;; Atom for the directory watcher future
(defonce watcher-future (atom (future (fn [] nil))))
;; Flag to help avoid reloading shader right after loading it for the
;; first time.
(defonce watcher-just-started (atom true))

;; ======================================================================
(defn- fill-tex-filenames
  "return a vector of 4 items, always.  Use nil if no filename"
  [tex-filenames]
  (apply vector
         (for [i (range 4)]
           (if (< i (count tex-filenames))
             (nth tex-filenames i)))))

(defn- uniform-sampler-type-str
  [tex-types n]
  (format "uniform sampler%s iChannel%s;\n"
          (if (= :cubemap (nth tex-types 0)) "Cube" "2D")
          n))

(defn- slurp-fs
  "do whatever it takes to modify shadertoy fragment shader source to
  be useable"
  [filename]
  (let [{:keys [tex-types]} @globals
        ;;file-str (slurp filename)
        file-str (str "#version 120\n"
                      "uniform vec3      iResolution;\n"
                      "uniform float     iGlobalTime;\n"
                      "uniform float     iChannelTime[4];\n"
                      "uniform vec4      iMouse;\n"
                      (uniform-sampler-type-str tex-types 0)
                      (uniform-sampler-type-str tex-types 1)
                      (uniform-sampler-type-str tex-types 2)
                      (uniform-sampler-type-str tex-types 3)
                      "uniform vec4      iDate;\n"
                      "\n"
                      (slurp filename))]
    file-str))

(defn- cubemap-filename?
  "if a filename contains a '*' char, it is a cubemap"
  [filename]
  (if (string? filename)
    (not (nil? (re-find #"\*" filename)))
    false))

(defn- get-texture-type
  [tex-filename]
  (cond
   (cubemap-filename? tex-filename) :cubemap
   (= :previous-frame tex-filename) :previous-frame
   :default :twod))

(defn- init-window
  "Initialise a shader-powered window with the specified
   display-mode. If true-fullscreen? is true, fullscreen mode is
   attempted if the display-mode is compatible. See display-modes for a
   list of available modes and fullscreen-display-modes for a list of
   fullscreen compatible modes.."
  [display-mode title shader-filename shader-str-atom tex-filenames true-fullscreen? user-fn display-sync-hz]
  (let [width               (.getWidth display-mode)
        height              (.getHeight display-mode)
        pixel-format        (PixelFormat.)
        context-attributes  (-> (ContextAttribs. 2 1)) ;; GL2.1
        current-time-millis (System/currentTimeMillis)
        tex-filenames       (fill-tex-filenames tex-filenames)
        tex-types           (map get-texture-type tex-filenames)]
    (swap! globals
           assoc
           :active          :yes
           :width           width
           :height          height
           :title           title
           :display-sync-hz display-sync-hz
           :start-time      current-time-millis
           :last-time       current-time-millis
           :shader-filename shader-filename
           :shader-str-atom shader-str-atom
           :tex-filenames   tex-filenames
           :tex-types       tex-types
           :user-fn         user-fn)
    ;; slurp-fs requires :tex-types, so we need a 2 pass setup
    (let [shader-str (if (nil? shader-filename)
                       @shader-str-atom
                       (slurp-fs (:shader-filename @globals)))]
      (swap! globals assoc :shader-str shader-str)
      (Display/setDisplayMode display-mode)
      (when true-fullscreen?
        (Display/setFullscreen true))
      (Display/setTitle title)
      (Display/setVSyncEnabled true)
      (Display/setLocation 0 0)
      (Display/create pixel-format context-attributes))))

(defn- init-buffers
  []
  (let [vertices            (float-array
                             [-1.0 -1.0 0.0 1.0
                               1.0 -1.0 0.0 1.0
                              -1.0  1.0 0.0 1.0
                              -1.0  1.0 0.0 1.0
                               1.0 -1.0 0.0 1.0
                               1.0  1.0 0.0 1.0])
        vertices-buffer     (-> (BufferUtils/createFloatBuffer (count vertices))
                                (.put vertices)
                                (.flip))
        vertices-count      (count vertices)
        vbo-id              (GL15/glGenBuffers)
        _                   (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo-id)
        _                   (GL15/glBufferData GL15/GL_ARRAY_BUFFER
                                           vertices-buffer
                                           GL15/GL_STATIC_DRAW)
        _ (except-gl-errors "@ end of init-buffers")]
    (swap! globals
           assoc
           :vbo-id vbo-id
           :vertices-count vertices-count)))

(def vs-shader
  (str "#version 120\n"
       "void main(void) {\n"
       "    gl_Position = gl_Vertex;\n"
       "}\n"))

(defn- load-shader
  [shader-str shader-type]
  (let [shader-id         (GL20/glCreateShader shader-type)
        _                 (GL20/glShaderSource shader-id shader-str)
        _                 (GL20/glCompileShader shader-id)
        gl-compile-status (GL20/glGetShaderi shader-id GL20/GL_COMPILE_STATUS)
        _ (except-gl-errors "@ end of let load-shader")]
    (when (== gl-compile-status GL11/GL_FALSE)
      (println "ERROR: Loading a Shader:")
      (println (GL20/glGetShaderInfoLog shader-id 10000)))
    [gl-compile-status shader-id]))

(defn- init-shaders
  []
  (let [[ok? vs-id] (load-shader vs-shader GL20/GL_VERTEX_SHADER)
        _           (assert (== ok? GL11/GL_TRUE)) ;; something is really wrong if our vs is bad
        _           (if (nil? (:shader-filename @globals))
                      (println "Loading shader from string")
                      (println "Loading shader from file:" (:shader-filename @globals)))
        [ok? fs-id] (load-shader (:shader-str @globals) GL20/GL_FRAGMENT_SHADER)]
    (if (== ok? GL11/GL_TRUE)
      (let [pgm-id                (GL20/glCreateProgram)
            _ (except-gl-errors "@ let init-shaders glCreateProgram")
            _                     (GL20/glAttachShader pgm-id vs-id)
            _ (except-gl-errors "@ let init-shaders glAttachShader VS")
            _                     (GL20/glAttachShader pgm-id fs-id)
            _ (except-gl-errors "@ let init-shaders glAttachShader FS")
            _                     (GL20/glLinkProgram pgm-id)
            _ (except-gl-errors "@ let init-shaders glLinkProgram")
            gl-link-status        (GL20/glGetProgrami pgm-id GL20/GL_LINK_STATUS)
            _ (except-gl-errors "@ let init-shaders glGetProgram link status")
            _                     (when (== gl-link-status GL11/GL_FALSE)
                                    (println "ERROR: Linking Shaders:")
                                    (println (GL20/glGetProgramInfoLog pgm-id 10000)))
            _ (except-gl-errors "@ let before GetUniformLocation")
            i-resolution-loc      (GL20/glGetUniformLocation pgm-id "iResolution")
            i-global-time-loc     (GL20/glGetUniformLocation pgm-id "iGlobalTime")
            i-channel-time-loc    (GL20/glGetUniformLocation pgm-id "iChannelTime")
            i-mouse-loc           (GL20/glGetUniformLocation pgm-id "iMouse")
            i-channel0-loc        (GL20/glGetUniformLocation pgm-id "iChannel0")
            i-channel1-loc        (GL20/glGetUniformLocation pgm-id "iChannel1")
            i-channel2-loc        (GL20/glGetUniformLocation pgm-id "iChannel2")
            i-channel3-loc        (GL20/glGetUniformLocation pgm-id "iChannel3")
            i-date-loc            (GL20/glGetUniformLocation pgm-id "iDate")
            _ (except-gl-errors "@ end of let init-shaders")
            ]
        (swap! globals
               assoc
               :shader-good true
               :vs-id vs-id
               :fs-id fs-id
               :pgm-id pgm-id
               :i-resolution-loc i-resolution-loc
               :i-global-time-loc i-global-time-loc
               :i-channel-time-loc i-channel-time-loc
               :i-mouse-loc i-mouse-loc
               :i-channel-loc [i-channel0-loc i-channel1-loc i-channel2-loc i-channel3-loc]
               :i-date-loc i-date-loc))
      ;; we didn't load the shader, don't be drawing
      (swap! globals assoc :shader-good false))))

(defn- buffer-swizzle-0123-1230
  "given a ARGB pixel array, swizzle it to be RGBA.  Or, ABGR to BGRA"
  [^bytes data] ;; Wow!  That ^bytes changes this from 10s for a 256x256 tex to instantaneous.
  (dotimes [i (/ (alength data) 4)]
    (let [i0 (* i 4)
          i1 (inc i0)
          i2 (inc i1)
          i3 (inc i2)
          tmp (aget data i0)]
      (aset data i0 (aget data i1))
      (aset data i1 (aget data i2))
      (aset data i2 (aget data i3))
      (aset data i3 tmp)))
  data)

(defn- cubemap-filename
  [filename i]
  (clojure.string/replace filename "*" (str i)))

(defn- put-texture-data
  "put the data from the image into the buffer and return the buffer"
  [buffer image swizzle-0123-1230]
  (let [data (byte-array ^DataBufferByte
                         (-> (.getRaster image)
                             (.getDataBuffer)
                             (.getData)))
        data (if swizzle-0123-1230
               (buffer-swizzle-0123-1230 data)
               data)
        buffer (.put buffer data 0 (alength data))] ; (.order (ByteOrder/nativeOrder)) ?
    buffer))

(defn- tex-image-bytes
  "return the number of bytes per pixel in this image"
  [image]
  (let [image-type  (.getType image)
        image-bytes (if (or (= image-type BufferedImage/TYPE_3BYTE_BGR)
                            (= image-type BufferedImage/TYPE_INT_RGB))
                      3
                      (if (or (= image-type BufferedImage/TYPE_4BYTE_ABGR)
                              (= image-type BufferedImage/TYPE_INT_ARGB))
                        4
                        0)) ;; unhandled image type--what to do?
        ;; _           (println "image-type"
        ;;                          (cond
        ;;                           (= image-type BufferedImage/TYPE_3BYTE_BGR)  "TYPE_3BYTE_BGR"
        ;;                           (= image-type BufferedImage/TYPE_INT_RGB)    "TYPE_INT_RGB"
        ;;                           (= image-type BufferedImage/TYPE_4BYTE_ABGR) "TYPE_4BYTE_ABGR"
        ;;                           (= image-type BufferedImage/TYPE_INT_ARGB)   "TYPE_INT_ARGB"
        ;;                           :else image-type))
        _           (assert (pos? image-bytes))] ;; die on unhandled image
    image-bytes))

(defn- tex-internal-format
  "return the internal-format for the glTexImage2D call for this image"
  [image]
  (let [image-type      (.getType image)
        internal-format (cond
                         (= image-type BufferedImage/TYPE_3BYTE_BGR)  GL11/GL_RGB8
                         (= image-type BufferedImage/TYPE_INT_RGB)    GL11/GL_RGB8
                         (= image-type BufferedImage/TYPE_4BYTE_ABGR) GL11/GL_RGBA8
                         (= image-type BufferedImage/TYPE_INT_ARGB)   GL11/GL_RGBA8)]
    internal-format))

(defn- tex-format
  "return the format for the glTexImage2D call for this image"
  [image]
  (let [image-type (.getType image)
        format     (cond
                    (= image-type BufferedImage/TYPE_3BYTE_BGR)  GL12/GL_BGR
                    (= image-type BufferedImage/TYPE_INT_RGB)    GL11/GL_RGB
                    (= image-type BufferedImage/TYPE_4BYTE_ABGR) GL12/GL_BGRA
                    (= image-type BufferedImage/TYPE_INT_ARGB)   GL11/GL_RGBA)]
    format))

(defn- load-texture
  "load, bind texture from filename.  return tex-id.  returns nil if filename is nil"
  ([filename]
     (let [tex-id (GL11/glGenTextures)]
       (if (cubemap-filename? filename)
         (do
           (dotimes [i 6]
             (load-texture (cubemap-filename filename i) GL13/GL_TEXTURE_CUBE_MAP tex-id i))
           tex-id)
         (load-texture filename GL11/GL_TEXTURE_2D tex-id 0))))
  ([filename target tex-id i]
     (if (string? filename)
       ;; load from file
       (let [_                (println "Loading texture:" filename)
             image            (ImageIO/read (FileInputStream. filename))
             image-bytes      (tex-image-bytes image)
             internal-format  (tex-internal-format image)
             format           (tex-format image)
             nbytes           (* image-bytes (.getWidth image) (.getHeight image))
             buffer           (-> (BufferUtils/createByteBuffer nbytes)
                                  (put-texture-data image (= image-bytes 4))
                                  (.flip))
             tex-image-target (if (= target GL13/GL_TEXTURE_CUBE_MAP)
                                (+ i GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_X)
                                target)]
         (GL11/glBindTexture target tex-id)
         (GL11/glTexParameteri target GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
         (GL11/glTexParameteri target GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
         (if (== target GL11/GL_TEXTURE_2D)
           (do
             (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT)
             (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_T GL11/GL_REPEAT))
           (do ;; CUBE_MAP
             (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
             (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)))
         (GL11/glTexImage2D tex-image-target 0 internal-format
                            (.getWidth image)  (.getHeight image) 0
                            format
                            GL11/GL_UNSIGNED_BYTE
                            buffer)
         (except-gl-errors "@ end of load-texture if-stmt")
         tex-id)
       (when (= filename :previous-frame)
         ;; :previous-frame initial setup
         (println "setting up :previous-frame texture")
         (GL11/glBindTexture target tex-id)
         (GL11/glTexParameteri target GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
         (GL11/glTexParameteri target GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
         (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
         (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
         (except-gl-errors "@ end of load-texture else-stmt")
         tex-id))))

(defn- init-textures
  []
  (let [tex-ids (map load-texture (:tex-filenames @globals))]
    (swap! globals assoc :tex-ids tex-ids)))

(defn- init-gl
  []
  (let [{:keys [width height user-fn]} @globals]
    ;;(println "OpenGL version:" (GL11/glGetString GL11/GL_VERSION))
    (GL11/glClearColor 0.0 0.0 0.0 0.0)
    (GL11/glViewport 0 0 width height)
    (init-buffers)
    (init-textures)
    (init-shaders)
    (when user-fn
      (user-fn :init (:pgm-id @globals)))))

(defn- try-reload-shader
  []
  (let [{:keys [vs-id fs-id pgm-id shader-filename user-fn]} @globals
        vs-id (if (= vs-id 0)
                (let [[ok? vs-id] (load-shader vs-shader GL20/GL_VERTEX_SHADER)
                      _ (assert (== ok? GL11/GL_TRUE))]
                  vs-id)
                vs-id)
        fs-shader       (if (nil? shader-filename)
                          @reload-shader-str
                          (slurp-fs shader-filename))
        [ok? new-fs-id] (load-shader fs-shader GL20/GL_FRAGMENT_SHADER)
        _               (reset! reload-shader false)]
    (if (== ok? GL11/GL_FALSE)
      ;; we didn't reload a good shader. Go back to the old one if possible
      (when (:shader-good @globals)
        (GL20/glUseProgram pgm-id)
        (except-gl-errors "@ try-reload-shader useProgram1"))
      ;; the load shader went well, keep going...
      (let [new-pgm-id     (GL20/glCreateProgram)
            _ (except-gl-errors "@ try-reload-shader glCreateProgram")
            _              (GL20/glAttachShader new-pgm-id vs-id)
            _ (except-gl-errors "@ try-reload-shader glAttachShader VS")
            _              (GL20/glAttachShader new-pgm-id new-fs-id)
            _ (except-gl-errors "@ try-reload-shader glAttachShader FS")
            _              (GL20/glLinkProgram new-pgm-id)
            _ (except-gl-errors "@ try-reload-shader glLinkProgram")
            gl-link-status (GL20/glGetProgrami new-pgm-id GL20/GL_LINK_STATUS)
            _ (except-gl-errors "@ end of let try-reload-shader")]
        (if (== gl-link-status GL11/GL_FALSE)
          (do
            (println "ERROR: Linking Shaders: (reloading previous program)")
            (println (GL20/glGetProgramInfoLog new-pgm-id 10000))
            (GL20/glUseProgram pgm-id)
            (except-gl-errors "@ try-reload-shader useProgram2"))
          (let [_ (println "Reloading shader:" shader-filename)
                i-resolution-loc   (GL20/glGetUniformLocation new-pgm-id "iResolution")
                i-global-time-loc  (GL20/glGetUniformLocation new-pgm-id "iGlobalTime")
                i-channel-time-loc (GL20/glGetUniformLocation new-pgm-id "iChannelTime")
                i-mouse-loc        (GL20/glGetUniformLocation new-pgm-id "iMouse")
                i-channel0-loc     (GL20/glGetUniformLocation new-pgm-id "iChannel0")
                i-channel1-loc     (GL20/glGetUniformLocation new-pgm-id "iChannel1")
                i-channel2-loc     (GL20/glGetUniformLocation new-pgm-id "iChannel2")
                i-channel3-loc     (GL20/glGetUniformLocation new-pgm-id "iChannel3")
                i-date-loc         (GL20/glGetUniformLocation new-pgm-id "iDate")]
            (GL20/glUseProgram new-pgm-id)
            (except-gl-errors "@ try-reload-shader useProgram")
            (when user-fn
              (user-fn :init new-pgm-id))
            ;; cleanup the old program
            (when (not= pgm-id 0)
              (GL20/glDetachShader pgm-id vs-id)
              (GL20/glDetachShader pgm-id fs-id)
              (GL20/glDeleteShader fs-id))
            (except-gl-errors "@ try-reload-shader detach/delete")
            (swap! globals
                   assoc
                   :shader-good true
                   :fs-id new-fs-id
                   :pgm-id new-pgm-id
                   :i-resolution-loc i-resolution-loc
                   :i-global-time-loc i-global-time-loc
                   :i-channel-time-loc i-channel-time-loc
                   :i-mouse-loc i-mouse-loc
                   :i-channel-loc [i-channel0-loc i-channel1-loc i-channel2-loc i-channel3-loc]
                   :i-date-loc i-date-loc
                   :shader-str fs-shader)))))))

(defn- draw
  []
  (let [{:keys [width height i-resolution-loc
                start-time last-time i-global-time-loc
                i-date-loc
                pgm-id vbo-id
                vertices-count
                i-mouse-loc
                mouse-pos-x mouse-pos-y
                mouse-ori-x mouse-ori-y
                i-channel-time-loc i-channel-loc
                channel-time-buffer
                old-pgm-id old-fs-id
                tex-ids tex-types
                user-fn]} @globals
        cur-time    (/ (- last-time start-time) 1000.0)
        cur-date    (Calendar/getInstance)
        cur-year    (.get cur-date Calendar/YEAR)         ;; four digit year
        cur-month   (.get cur-date Calendar/MONTH)        ;; month 0-11
        cur-day     (.get cur-date Calendar/DAY_OF_MONTH) ;; day 1-31
        cur-seconds (+ (* (.get cur-date Calendar/HOUR_OF_DAY) 60.0 60.0)
                       (* (.get cur-date Calendar/MINUTE) 60.0)
                       (.get cur-date Calendar/SECOND))]
    (if @reload-shader
      (try-reload-shader)         ; this must call glUseProgram
      (GL20/glUseProgram pgm-id)) ; else, normal path...

    (except-gl-errors "@ draw before clear")

    (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)

    (when user-fn
      (user-fn :pre-draw pgm-id))

    ;; activate textures
    (dotimes [i (count tex-ids)]
      (when (nth tex-ids i)
        (GL13/glActiveTexture (+ GL13/GL_TEXTURE0 i))
        (cond
         (= :cubemap (nth tex-types i))
         (GL11/glBindTexture GL13/GL_TEXTURE_CUBE_MAP (nth tex-ids i))
         (= :previous-frame (nth tex-types i))
         (GL11/glBindTexture GL11/GL_TEXTURE_2D (nth tex-ids i))
         :default
         (GL11/glBindTexture GL11/GL_TEXTURE_2D (nth tex-ids i)))))

    (except-gl-errors "@ draw after activate textures")

    ;; setup our uniform
    (GL20/glUniform3f i-resolution-loc width height 1.0)
    (GL20/glUniform1f i-global-time-loc cur-time)
    (GL20/glUniform1 i-channel-time-loc
                     (-> channel-time-buffer
                         (.put (float-array
                                [(float cur-time)
                                 (float cur-time)
                                 (float cur-time)
                                 (float cur-time)]))
                         (.flip)))
    (GL20/glUniform4f i-mouse-loc
                      mouse-pos-x
                      mouse-pos-y
                      mouse-ori-x
                      mouse-ori-y)
    (GL20/glUniform1i (nth i-channel-loc 0) 0)
    (GL20/glUniform1i (nth i-channel-loc 1) 1)
    (GL20/glUniform1i (nth i-channel-loc 2) 2)
    (GL20/glUniform1i (nth i-channel-loc 3) 3)
    (GL20/glUniform4f i-date-loc cur-year cur-month cur-day cur-seconds)
    ;; get vertex array ready
    (GL11/glEnableClientState GL11/GL_VERTEX_ARRAY)
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo-id)
    (GL11/glVertexPointer 4 GL11/GL_FLOAT 0 0)

    (except-gl-errors "@ draw prior to DrawArrays")

    ;; Draw the vertices
    (GL11/glDrawArrays GL11/GL_TRIANGLES 0 vertices-count)

    (except-gl-errors "@ draw after DrawArrays")

    ;; Put everything back to default (deselect)
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
    (GL11/glDisableClientState GL11/GL_VERTEX_ARRAY)
    ;; unbind textures
    (dotimes [i (count tex-ids)]
      (when (nth tex-ids i)
        (GL13/glActiveTexture (+ GL13/GL_TEXTURE0 i))
        (GL11/glBindTexture GL13/GL_TEXTURE_CUBE_MAP 0)
        (GL11/glBindTexture GL11/GL_TEXTURE_2D 0)))

    (except-gl-errors "@ draw prior to post-draw")

    (when user-fn
      (user-fn :post-draw pgm-id))

    (except-gl-errors "@ draw after post-draw")

    (GL20/glUseProgram 0)

    ;; copy the rendered image
    (dotimes [i (count tex-ids)]
      (when (= :previous-frame (nth tex-types i))
        (GL11/glBindTexture GL11/GL_TEXTURE_2D (nth tex-ids i))
        (GL11/glCopyTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGBA8 0 0 width height 0)
        (GL11/glBindTexture GL11/GL_TEXTURE_2D 0)))

    (except-gl-errors "@ draw after copy")))

(defn- update
  []
  (let [{:keys [width height last-time
                mouse-pos-x mouse-pos-y
                mouse-clicked mouse-ori-x mouse-ori-y]} @globals
        cur-time (System/currentTimeMillis)
        cur-mouse-clicked (Mouse/isButtonDown 0)
        mouse-down-event (and cur-mouse-clicked (not mouse-clicked))
        cur-mouse-pos-x (if cur-mouse-clicked (Mouse/getX) mouse-pos-x)
        cur-mouse-pos-y (if cur-mouse-clicked (Mouse/getY) mouse-pos-y)
        cur-mouse-ori-x (if mouse-down-event
                          (Mouse/getX)
                          (if cur-mouse-clicked
                            mouse-ori-x
                            (- (Math/abs mouse-ori-x))))
        cur-mouse-ori-y (if mouse-down-event
                          (Mouse/getY)
                          (if cur-mouse-clicked
                            mouse-ori-y
                            (- (Math/abs mouse-ori-y))))]
    (swap! globals
           assoc
           :last-time cur-time
           :mouse-clicked cur-mouse-clicked
           :mouse-pos-x cur-mouse-pos-x
           :mouse-pos-y cur-mouse-pos-y
           :mouse-ori-x cur-mouse-ori-x
           :mouse-ori-y cur-mouse-ori-y)
    (if (:shader-good @globals)
      (draw)
      ;; just clear to prevent strobing awfulness
      (do
        (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
        (if @reload-shader
          (try-reload-shader))))))

(defn- destroy-gl
  []
  (let [{:keys [pgm-id vs-id fs-id vbo-id user-fn]} @globals]
    ;; Delete any user state
    (when user-fn
      (user-fn :destroy pgm-id))
    ;; Delete the shaders
    (GL20/glUseProgram 0)
    (GL20/glDetachShader pgm-id vs-id)
    (GL20/glDetachShader pgm-id fs-id)
    (GL20/glDeleteShader vs-id)
    (GL20/glDeleteShader fs-id)
    (GL20/glDeleteProgram pgm-id)
    ;; Delete the vertex VBO
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
    (GL15/glDeleteBuffers vbo-id)))

(defn- run-thread
  [mode shader-filename shader-str-atom tex-filenames title true-fullscreen? user-fn display-sync-hz]
  (init-window mode title shader-filename shader-str-atom tex-filenames true-fullscreen? user-fn display-sync-hz)
  (init-gl)
  (while (and (= :yes (:active @globals))
              (not (Display/isCloseRequested)))
    (update)
    (Display/update)
    (Display/sync (:display-sync-hz @globals)))
  (destroy-gl)
  (Display/destroy)
  (swap! globals assoc :active :no))

(defn- good-tex-count
  [textures]
  (if (<= (count textures) 4)
    true
    (do
      (println "ERROR: number of textures must be <= 4")
      false)))

(defn- expand-filename
  "if there is a cubemap filename, expand it 0..5 for the
  cubemaps. otherwise leave it alone."
  [filename]
  (if (cubemap-filename? filename)
    (for [i (range 6)] (cubemap-filename filename i))
    filename))

(defn- files-exist
  "check to see that the filenames actually exist.  One tweak is to
  allow nil or keyword 'filenames'.  Those are important placeholders.
  Another tweak is to expand names for cubemap textures."
  [filenames]
  (let [full-filenames (flatten (map expand-filename filenames))]
    (reduce #(and %1 %2) ; kibit keep
            (for [fn full-filenames]
              (if (or (nil? fn)
                      (and (keyword? fn) (= fn :previous-frame))
                      (.exists (File. fn)))
                true
                (do
                  (println "ERROR:" fn "does not exist.")
                  false))))))

(defn- sane-user-inputs
  [mode shader-filename shader-str textures title true-fullscreen? user-fn]
  (and (good-tex-count textures)
       (files-exist (flatten [shader-filename textures]))
       (not (and (nil? shader-filename) (nil? shader-str)))))

;; watch the shader-str-atom to reload on a change
(defn- watch-shader-str-atom
  [key identity old new]
  (when (not= old new)
    ;; if already reloading, wait for that to finish
    (while @reload-shader
      ;; FIXME this can hang.  We should timeout instead
      (Thread/sleep 100))
    (reset! reload-shader-str new)
    (reset! reload-shader true)))

;; watch the shader directory & reload the current shader if it changes.
(defn- if-match-reload-shader
  [files]
  (if @watcher-just-started
    ;; allow first, automatic call to pass unnoticed
    (reset! watcher-just-started false)
    ;; otherwise do the reload check
    (doseq [f files]
      (when (= (.getPath f) (:shader-filename @globals))
        ;; set a flag that the opengl thread will use
        (reset! reload-shader true)))))

(defn- start-watcher
  "create a watch for glsl shaders in the directory and return the global
  future atom for that watcher"
  [dir]
  (reset! watcher-just-started true)
  (watcher/watcher
   [dir]
   (watcher/rate 100)
   (watcher/file-filter watcher/ignore-dotfiles)
   (watcher/file-filter (watcher/extensions :glsl))
   (watcher/on-change if-match-reload-shader)))

(defn- stop-watcher
  "given a watcher-future f, put a stop to it"
  [f]
  (when-not (or (future-done? f) (future-cancelled? f))
    (if (not (future-cancel f))
      (println "ERROR: unable to stop-watcher!"))))

;; ======================================================================
;; allow shader to have user-data, just like tone.
;; I'd like to make this better follow DRY, but this seems okay for now
(defonce shader-user-data (atom {}))
(defonce shader-user-locs (atom {}))
(defn- shader-default-fn
  [dispatch pgm-id]
  (case dispatch ;; FIXME defmulti?
    :init ;; find Uniform Location
    (doseq [key (keys @shader-user-data)]
      (let [loc (GL20/glGetUniformLocation pgm-id key)]
        (swap! shader-user-locs assoc key loc)))
    :pre-draw
    (doseq [key (keys @shader-user-data)]
      (let [loc (@shader-user-locs key)
            val (deref (@shader-user-data key))]
        ;;(println key loc val)
        (if (float? val)
          (GL20/glUniform1f loc val)
          (when (vector? val)
            (case (count val)
              1 (GL20/glUniform1f loc (nth val 0))
              2 (GL20/glUniform2f loc (nth val 0) (nth val 1))
              3 (GL20/glUniform3f loc (nth val 0) (nth val 1) (nth val 2))
              4 (GL20/glUniform4f loc (nth val 0) (nth val 1) (nth val 2) (nth val 3)))))))
    :post-draw
    nil ;; nothing to do
    :destroy
    nil ;; nothing to do
    ))

;; Public API ===================================================

(defn display-modes
  "Returns a seq of display modes sorted by resolution size with highest
   resolution first and lowest last."
  []
  (sort (fn [a b]
          (let [res-a       (* (.getWidth a)
                               (.getHeight a))
                res-b       (* (.getWidth b)
                               (.getHeight b))
                bit-depth-a (.getBitsPerPixel a)
                bit-depth-b (.getBitsPerPixel b) ]
            (if (= res-a res-b)
              (> bit-depth-a bit-depth-b)
              (> res-a res-b))))
        (Display/getAvailableDisplayModes)))

(defn fullscreen-display-modes
  "Returns a seq of fullscreen compatible display modes sorted by
   resolution size with highest resolution first and lowest last."
  []
  (filter #(.isFullscreenCapable %) (display-modes)))

(defn undecorate-display!
  "All future display windows will be undecorated (i.e. no title bar)"
  []
  (System/setProperty "org.lwjgl.opengl.Window.undecorated" "true"))

(defn decorate-display!
  "All future display windows will be decorated (i.e. have a title bar)"
  []
  (System/setProperty "org.lwjgl.opengl.Window.undecorated" "false"))

(defn active?
  "Returns true if the shader display is currently running"
  []
  (= :yes (:active @globals)))

(defn inactive?
  "Returns true if the shader display is completely done running."
  []
  (= :no (:active @globals)))

(defn stop
  "Stop and destroy the current shader display. Blocks the current
   thread until completed."
  []
  (when (active?)
    (swap! globals assoc :active :stopping)
    (while (not (inactive?))
      (Thread/sleep 100)))
  (remove-watch (:shader-str-atom @globals) :shader-str-watch)
  (stop-watcher @watcher-future))

(defn start-shader-display
  "Start a new shader display with the specified mode. Prefer start or
   start-fullscreen for simpler usage."
  [mode shader-filename-or-str-atom textures title
   true-fullscreen? user-data user-fn display-sync-hz]
  (let [is-filename     (not (instance? clojure.lang.Atom shader-filename-or-str-atom))
        shader-filename (if is-filename
                          shader-filename-or-str-atom)
        ;; Fix for issue 15.  Normalize the given shader-filename to the
        ;; path separators that the system will use.  If user gives path/to/shader.glsl
        ;; and windows returns this as path\to\shader.glsl from .getPath, this
        ;; change should make comparison to path\to\shader.glsl work.
        shader-filename (if (and is-filename (not (nil? shader-filename)))
                          (.getPath (File. shader-filename)))
        shader-str-atom (if-not is-filename
                          shader-filename-or-str-atom
                          (atom nil))
        shader-str      (if-not is-filename
                          @shader-str-atom)]
    (when (sane-user-inputs mode shader-filename shader-str textures title true-fullscreen? user-fn)
      ;; stop the current shader
      (stop)
      ;; start the watchers
      (if is-filename
        (when-not (nil? shader-filename)
          (swap! watcher-future
                 (fn [x] (start-watcher (.getParent (File. shader-filename))))))
        (add-watch shader-str-atom :shader-str-watch watch-shader-str-atom))
      ;; set user data
      (reset! shader-user-data user-data)
      ;; start the requested shader
      (.start (Thread.
               (fn [] (run-thread mode
                                 shader-filename
                                 shader-str-atom
                                 textures
                                 title
                                 true-fullscreen?
                                 user-fn
                                 display-sync-hz)))))))

(defn start
  "Start a new shader display. Forces the display window to be
   decorated (i.e. have a title bar)."
  [shader-filename-or-str-atom
   &{:keys [width height title display-sync-hz
            textures user-data user-fn]
     :or {width           600
          height          600
          title           "shadertone"
          display-sync-hz 60
          textures        []
          user-data       {}
          user-fn         shader-default-fn}}]
  (let [mode (DisplayMode. width height)]
    (decorate-display!)
    (start-shader-display mode shader-filename-or-str-atom textures title false user-data user-fn display-sync-hz)))

(defn start-fullscreen
  "Start a new shader display in pseudo fullscreen mode. This creates a
   new borderless window which is the size of the current
   resolution. There are therefore no OS controls for closing the shader
   window. Use (stop) to close things manually. "
  [shader-filename-or-str-atom
   &{:keys [display-sync-hz textures user-data user-fn]
     :or {display-sync-hz 60
          textures        [nil]
          user-data       {}
          user-fn         shader-default-fn}}]
     (let [mode (Display/getDisplayMode)]
       (undecorate-display!)
       (start-shader-display mode shader-filename-or-str-atom textures "" false user-data user-fn display-sync-hz)))
