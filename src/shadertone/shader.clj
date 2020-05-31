(ns #^{:author "Roger Allen"
       :doc "Shadertoy-like core library."}
  shadertone.shader
  (:require [watchtower.core :as watcher]
            clojure.string)
  (:import (java.awt.image BufferedImage DataBuffer DataBufferByte WritableRaster)
           (java.io File FileInputStream)
           (java.nio IntBuffer ByteBuffer FloatBuffer ByteOrder)
           (java.util Calendar)
           (javax.imageio ImageIO)
           (java.lang.reflect Field)
           (org.lwjgl BufferUtils)
           (org.lwjgl.input Mouse)
           (org.lwjgl.opengl ContextAttribs Display DisplayMode
                             GL11 GL12 GL13 GL15 GL20
                             PixelFormat)))

;; ======================================================================
;; State Variables
;; a map of state variables for use in the gl thread
(defonce default-state-values
  {:active              :no  ;; :yes/:stopping/:no
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
   :shader-good         true ;; false in error condition
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
   :i-channel-res-loc   0
   :i-date-loc          0
   :channel-time-buffer (-> (BufferUtils/createFloatBuffer 4)
                            (.put (float-array
                                   [0.0 0.0 0.0 0.0]))
                            (.flip))
   :channel-res-buffer (-> (BufferUtils/createFloatBuffer (* 3 4))
                            (.put (float-array
                                   [0.0 0.0 0.0
                                    0.0 0.0 0.0
                                    0.0 0.0 0.0
                                    0.0 0.0 0.0]))
                            (.flip))
   ;; textures
   :tex-filenames       []
   :tex-ids             []
   :tex-types           [] ; :cubemap, :previous-frame
   ;; a user draw function
   :user-fn             nil
   ;; pixel read
   :pixel-read-enable   false
   :pixel-read-pos-x    0
   :pixel-read-pos-y    0
   :pixel-read-data      (-> (BufferUtils/createByteBuffer 3)
                            (.put (byte-array (map byte [0 0 0])))
                            (.flip))
   })

;; GLOBAL STATE ATOMS
;; Tried to get rid of this atom, but LWJGL is limited to only
;; one window.  So, we just keep a single atom containing the
;; current window state here.
(defonce the-window-state (atom default-state-values))
;; The reload-shader atom communicates across the gl & watcher threads
(defonce reload-shader (atom false))
(defonce reload-shader-str (atom ""))
;; Atom for the directory watcher future
(defonce watcher-future (atom (future (fn [] nil))))
;; Flag to help avoid reloading shader right after loading it for the
;; first time.
(defonce watcher-just-started (atom true))
(defonce throw-on-gl-error (atom true))
;;
(defonce pixel-value (atom [0.0 0.0 0.0]))

;; ======================================================================
;; code modified from
;; https://github.com/ztellman/penumbra/blob/master/src/penumbra/opengl/core.clj
(defn- get-fields [#^Class static-class]
  (. static-class getFields))
(defn- gl-enum-name
  "Takes the numeric value of a gl constant (i.e. GL_LINEAR), and gives the name"
  [enum-value]
  (if (= 0 enum-value)
    "NONE"
    (.getName #^Field (some
                       #(if (= enum-value (.get #^Field % nil)) % nil)
                       (mapcat get-fields [GL11 GL12 GL13 GL15 GL20])))))
(defn- except-gl-errors
  [msg]
  (let [error (GL11/glGetError)
        error-string (str "OpenGL Error(" error "):"
                          (gl-enum-name error) ": " msg)]
    (if (and (not (zero? error)) @throw-on-gl-error)
      (throw (Exception. error-string)))))

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
  [locals filename]
  (let [{:keys [tex-types]} @locals
        ;;file-str (slurp filename)
        file-str (str "#version 140\n"
                      "uniform vec3      iResolution;\n"
                      "uniform float     iTime;\n"
                      "uniform float     iChannelTime[4];\n"
                      "uniform vec3      iChannelResolution[4];\n"
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
  [locals display-mode title shader-filename shader-str-atom tex-filenames true-fullscreen? user-fn display-sync-hz]
  (let [width               (.getWidth ^DisplayMode display-mode)
        height              (.getHeight ^DisplayMode display-mode)
        pixel-format        (PixelFormat.)
        context-attributes  (-> (ContextAttribs. 2 1)) ;; GL2.1
        current-time-millis (System/currentTimeMillis)
        tex-filenames       (fill-tex-filenames tex-filenames)
        tex-types           (map get-texture-type tex-filenames)]
    (swap! locals
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
                       (slurp-fs locals (:shader-filename @locals)))]
      (swap! locals assoc :shader-str shader-str)
      (Display/setDisplayMode display-mode)
      (when true-fullscreen?
        (Display/setFullscreen true))
      (Display/setTitle title)
      (Display/setVSyncEnabled true)
      (Display/setLocation 0 0)
      (Display/create pixel-format context-attributes))))

(defn- init-buffers
  [locals]
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
                                           ^FloatBuffer vertices-buffer
                                           GL15/GL_STATIC_DRAW)
        _ (except-gl-errors "@ end of init-buffers")]
    (swap! locals
           assoc
           :vbo-id vbo-id
           :vertices-count vertices-count)))

(def vs-shader
  (str "#version 140\n"
       "in vec3 pos;\n"
       "void main(void) {\n"
       "    gl_Position = vec4(pos, 1.0);\n"
       "}\n"))

(defn- load-shader
  [^String shader-str ^Integer shader-type]
  (let [shader-id         (GL20/glCreateShader shader-type)
        _ (except-gl-errors "@ load-shader glCreateShader ")
        _                 (GL20/glShaderSource shader-id shader-str)
        _ (except-gl-errors "@ load-shader glShaderSource ")
        _                 (GL20/glCompileShader shader-id)
        _ (except-gl-errors "@ load-shader glCompileShader ")
        gl-compile-status (GL20/glGetShaderi shader-id GL20/GL_COMPILE_STATUS)
        _ (except-gl-errors "@ end of let load-shader")]
    (when (== gl-compile-status GL11/GL_FALSE)
      (println "ERROR: Loading a Shader:")
      (println (GL20/glGetShaderInfoLog shader-id 10000)))
    [gl-compile-status shader-id]))

(defn- init-shaders
  [locals]
  (let [[ok? vs-id] (load-shader vs-shader GL20/GL_VERTEX_SHADER)
        _           (assert (== ok? GL11/GL_TRUE)) ;; something is really wrong if our vs is bad
        _           (if (nil? (:shader-filename @locals))
                      (println "Loading shader from string")
                      (println "Loading shader from file:" (:shader-filename @locals)))
        [ok? fs-id] (load-shader (:shader-str @locals) GL20/GL_FRAGMENT_SHADER)]
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
            _ (except-gl-errors "@ let before EnableVertexAttribArray")
            _                     (GL20/glVertexAttribPointer 0, 4, GL11/GL_FLOAT, false, 16, 0)
            _                     (GL20/glEnableVertexAttribArray 0)
            _ (except-gl-errors "@ let before GetUniformLocation")
            i-resolution-loc      (GL20/glGetUniformLocation pgm-id "iResolution")
            i-global-time-loc     (GL20/glGetUniformLocation pgm-id "iTime")
            i-channel-time-loc    (GL20/glGetUniformLocation pgm-id "iChannelTime")
            i-mouse-loc           (GL20/glGetUniformLocation pgm-id "iMouse")
            i-channel0-loc        (GL20/glGetUniformLocation pgm-id "iChannel0")
            i-channel1-loc        (GL20/glGetUniformLocation pgm-id "iChannel1")
            i-channel2-loc        (GL20/glGetUniformLocation pgm-id "iChannel2")
            i-channel3-loc        (GL20/glGetUniformLocation pgm-id "iChannel3")
            i-channel-res-loc     (GL20/glGetUniformLocation pgm-id "iChannelResolution")
            i-date-loc            (GL20/glGetUniformLocation pgm-id "iDate")
            _ (except-gl-errors "@ end of let init-shaders")
            ]
        (swap! locals
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
               :i-channel-res-loc i-channel-res-loc
               :i-date-loc i-date-loc))
      ;; we didn't load the shader, don't be drawing
      (swap! locals assoc :shader-good false))))

(defn- buffer-swizzle-0123-1230
  "given a ARGB pixel array, swizzle it to be RGBA.  Or, ABGR to BGRA"
  ^bytes [^bytes data] ;; Wow!  That ^bytes changes this from 10s for a 256x256 tex to instantaneous.
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
  ^ByteBuffer
  [^ByteBuffer buffer ^BufferedImage image ^Boolean swizzle-0123-1230]
  (let [data ^bytes (-> ^WritableRaster (.getRaster image)
                         ^DataBufferByte (.getDataBuffer)
                         (.getData))
        data (if swizzle-0123-1230
               (buffer-swizzle-0123-1230 data)
               data)
        buffer (.put buffer data 0 (alength data))] ; (.order (ByteOrder/nativeOrder)) ?
    buffer))

(defn- tex-image-bytes
  "return the number of bytes per pixel in this image"
  [^BufferedImage image]
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
  ^Integer
  [^BufferedImage image]
  (let [image-type      (.getType image)
        internal-format (cond
                         (= image-type BufferedImage/TYPE_3BYTE_BGR)  GL11/GL_RGB8
                         (= image-type BufferedImage/TYPE_INT_RGB)    GL11/GL_RGB8
                         (= image-type BufferedImage/TYPE_4BYTE_ABGR) GL11/GL_RGBA8
                         (= image-type BufferedImage/TYPE_INT_ARGB)   GL11/GL_RGBA8)]
    internal-format))

(defn- tex-format
  "return the format for the glTexImage2D call for this image"
  ^Integer
  [^BufferedImage image]
  (let [image-type (.getType image)
        format     (cond
                    (= image-type BufferedImage/TYPE_3BYTE_BGR)  GL12/GL_BGR
                    (= image-type BufferedImage/TYPE_INT_RGB)    GL11/GL_RGB
                    (= image-type BufferedImage/TYPE_4BYTE_ABGR) GL12/GL_BGRA
                    (= image-type BufferedImage/TYPE_INT_ARGB)   GL11/GL_RGBA)]
    format))

(defn- load-texture
  "load, bind texture from filename.  returns a texture info vector
   [tex-id width height z].  returns nil tex-id if filename is nil"
  ([^String filename]
     (let [tex-id (GL11/glGenTextures)]
       (if (cubemap-filename? filename)
         (do
           (dotimes [i 6]
             (load-texture (cubemap-filename filename i)
                           GL13/GL_TEXTURE_CUBE_MAP tex-id i))
           [tex-id 0.0 0.0 0.0]) ;; cubemaps don't update w/h
         (load-texture filename GL11/GL_TEXTURE_2D tex-id 0))))
  ([^String filename ^Integer target ^Integer tex-id ^Integer i]
     (if (string? filename)
       ;; load from file
       (let [_                (println "Loading texture:" filename)
             image            (ImageIO/read (FileInputStream. filename))
             image-bytes      (tex-image-bytes image)
             internal-format  (tex-internal-format image)
             format           (tex-format image)
             nbytes           (* image-bytes (.getWidth image) (.getHeight image))
             buffer           ^ByteBuffer (-> (BufferUtils/createByteBuffer nbytes)
                                              (put-texture-data image (= image-bytes 4))
                                              (.flip))
             tex-image-target ^Integer (if (= target GL13/GL_TEXTURE_CUBE_MAP)
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
         (GL11/glTexImage2D ^Integer tex-image-target 0 ^Integer internal-format
                            ^Integer (.getWidth image)  ^Integer (.getHeight image) 0
                            ^Integer format
                            GL11/GL_UNSIGNED_BYTE
                            ^ByteBuffer buffer)
         (except-gl-errors "@ end of load-texture if-stmt")
         [tex-id (.getWidth image) (.getHeight image) 1.0])
       (if (= filename :previous-frame)
         (do ;; :previous-frame initial setup
           (println "setting up :previous-frame texture")
           (GL11/glBindTexture target tex-id)
           (GL11/glTexParameteri target GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
           (GL11/glTexParameteri target GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
           (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
           (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
           (except-gl-errors "@ end of load-texture else-stmt")
           ;; use negative as flag to indicate using window width, height
           [tex-id -1.0 -1.0 1.0])
         ;; else must be nil texture
         [nil 0.0 0.0 0.0]))))

(defn- init-textures
  [locals]
  (let [tex-infos (map load-texture (:tex-filenames @locals))
        ;;_ (println "raw" tex-infos)
        tex-ids   (map first tex-infos)
        tex-whd   (map rest tex-infos)
        tex-whd   (flatten
                   (map #(if (< (first %) 0.0)
                           [(:width @locals) (:height @locals) 1.0]
                           %)
                        tex-whd))
        ;; update channel-res-buffer
        _         (-> ^FloatBuffer (:channel-res-buffer @locals)
                      (.put ^floats (float-array tex-whd))
                      (.flip))
        ]
    (swap! locals assoc
           :tex-ids tex-ids)))

(defn- init-gl
  [locals]
  (let [{:keys [width height user-fn]} @locals]
    ;;(println "OpenGL version:" (GL11/glGetString GL11/GL_VERSION))
    (GL11/glClearColor 0.0 0.0 0.0 0.0)
    (GL11/glViewport 0 0 width height)
    (init-buffers locals)
    (init-textures locals)
    (init-shaders locals)
    (when (and (not (nil? user-fn)) (:shader-good @locals))
      (user-fn :init (:pgm-id @locals)))))

(defn- try-reload-shader
  [locals]
  (let [{:keys [vs-id fs-id pgm-id shader-filename user-fn]} @locals
        vs-id (if (= vs-id 0)
                (let [[ok? vs-id] (load-shader vs-shader GL20/GL_VERTEX_SHADER)
                      _ (assert (== ok? GL11/GL_TRUE))]
                  vs-id)
                vs-id)
        fs-shader       (if (nil? shader-filename)
                          @reload-shader-str
                          (slurp-fs locals shader-filename))
        [ok? new-fs-id] (load-shader fs-shader GL20/GL_FRAGMENT_SHADER)
        _               (reset! reload-shader false)]
    (if (== ok? GL11/GL_FALSE)
      ;; we didn't reload a good shader. Go back to the old one if possible
      (when (:shader-good @locals)
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
                i-global-time-loc  (GL20/glGetUniformLocation new-pgm-id "iTime")
                i-channel-time-loc (GL20/glGetUniformLocation new-pgm-id "iChannelTime")
                i-mouse-loc        (GL20/glGetUniformLocation new-pgm-id "iMouse")
                i-channel0-loc     (GL20/glGetUniformLocation new-pgm-id "iChannel0")
                i-channel1-loc     (GL20/glGetUniformLocation new-pgm-id "iChannel1")
                i-channel2-loc     (GL20/glGetUniformLocation new-pgm-id "iChannel2")
                i-channel3-loc     (GL20/glGetUniformLocation new-pgm-id "iChannel3")
                i-channel-res-loc  (GL20/glGetUniformLocation new-pgm-id "iChannelResolution")
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
            (swap! locals
                   assoc
                   :shader-good true
                   :fs-id new-fs-id
                   :pgm-id new-pgm-id
                   :i-resolution-loc i-resolution-loc
                   :i-global-time-loc i-global-time-loc
                   :i-channel-time-loc i-channel-time-loc
                   :i-mouse-loc i-mouse-loc
                   :i-channel-loc [i-channel0-loc i-channel1-loc i-channel2-loc i-channel3-loc]
                   :i-channel-res-loc i-channel-res-loc
                   :i-date-loc i-date-loc
                   :shader-str fs-shader)))))))

(defn- get-pixel-value
  [^ByteBuffer rgb-bytes]
  (let [rf (/ (float (int (bit-and 0xFF (.get rgb-bytes 0)))) 255.0)
        gf (/ (float (int (bit-and 0xFF (.get rgb-bytes 1)))) 255.0)
        bf (/ (float (int (bit-and 0xFF (.get rgb-bytes 2)))) 255.0)]
    [rf gf bf]))

(defn- draw
  [locals]
  (let [{:keys [width height i-resolution-loc
                start-time last-time i-global-time-loc
                i-date-loc
                pgm-id vbo-id
                vertices-count
                i-mouse-loc
                mouse-pos-x mouse-pos-y
                mouse-ori-x mouse-ori-y
                i-channel-time-loc i-channel-loc
                i-channel-res-loc
                channel-time-buffer channel-res-buffer
                old-pgm-id old-fs-id
                tex-ids tex-types
                user-fn
                pixel-read-enable
                pixel-read-pos-x pixel-read-pos-y
                pixel-read-data]} @locals
        cur-time    (/ (- last-time start-time) 1000.0)
        _           (.put ^FloatBuffer channel-time-buffer 0 (float cur-time))
        _           (.put ^FloatBuffer channel-time-buffer 1 (float cur-time))
        _           (.put ^FloatBuffer channel-time-buffer 2 (float cur-time))
        _           (.put ^FloatBuffer channel-time-buffer 3 (float cur-time))
        cur-date    (Calendar/getInstance)
        cur-year    (.get cur-date Calendar/YEAR)         ;; four digit year
        cur-month   (.get cur-date Calendar/MONTH)        ;; month 0-11
        cur-day     (.get cur-date Calendar/DAY_OF_MONTH) ;; day 1-31
        cur-seconds (+ (* (.get cur-date Calendar/HOUR_OF_DAY) 60.0 60.0)
                       (* (.get cur-date Calendar/MINUTE) 60.0)
                       (.get cur-date Calendar/SECOND))]

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
    (GL20/glUniform1  ^Integer i-channel-time-loc ^FloatBuffer channel-time-buffer)
    (GL20/glUniform4f i-mouse-loc
                      mouse-pos-x
                      mouse-pos-y
                      mouse-ori-x
                      mouse-ori-y)
    (GL20/glUniform1i (nth i-channel-loc 0) 0)
    (GL20/glUniform1i (nth i-channel-loc 1) 1)
    (GL20/glUniform1i (nth i-channel-loc 2) 2)
    (GL20/glUniform1i (nth i-channel-loc 3) 3)
    (GL20/glUniform3  ^Integer i-channel-res-loc ^FloatBuffer channel-res-buffer)
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
    (except-gl-errors "@ draw after copy")

    ;; read a pixel value
    (when pixel-read-enable
      (GL11/glReadPixels ^Integer pixel-read-pos-x ^Integer pixel-read-pos-y
                        1 1
                        GL11/GL_RGB GL11/GL_UNSIGNED_BYTE
                        ^ByteBuffer pixel-read-data)
      (except-gl-errors "@ draw after pixel read")
      (reset! pixel-value (get-pixel-value ^ByteBuffer pixel-read-data)))))

(defn- update-and-draw
  [locals]
  (let [{:keys [width height last-time pgm-id
                mouse-pos-x mouse-pos-y
                mouse-clicked mouse-ori-x mouse-ori-y]} @locals
        cur-time (System/currentTimeMillis)
        cur-mouse-clicked (Mouse/isButtonDown 0)
        mouse-down-event (and cur-mouse-clicked (not mouse-clicked))
        cur-mouse-pos-x (if cur-mouse-clicked (Mouse/getX) mouse-pos-x)
        cur-mouse-pos-y (if cur-mouse-clicked (Mouse/getY) mouse-pos-y)
        cur-mouse-ori-x (if mouse-down-event
                          (Mouse/getX)
                          (if cur-mouse-clicked
                            mouse-ori-x
                            (- (Math/abs ^float mouse-ori-x))))
        cur-mouse-ori-y (if mouse-down-event
                          (Mouse/getY)
                          (if cur-mouse-clicked
                            mouse-ori-y
                            (- (Math/abs ^float mouse-ori-y))))]
    (swap! locals
           assoc
           :last-time cur-time
           :mouse-clicked cur-mouse-clicked
           :mouse-pos-x cur-mouse-pos-x
           :mouse-pos-y cur-mouse-pos-y
           :mouse-ori-x cur-mouse-ori-x
           :mouse-ori-y cur-mouse-ori-y)
    (if (:shader-good @locals)
      (do
        (if @reload-shader
          (try-reload-shader locals)  ; this must call glUseProgram
          (GL20/glUseProgram pgm-id)) ; else, normal path...
        (draw locals))
      ;; else clear to prevent strobing awfulness
      (do
        (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
        (except-gl-errors "@ bad-draw glClear ")
        (if @reload-shader
          (try-reload-shader locals))))))

(defn- destroy-gl
  [locals]
  (let [{:keys [pgm-id vs-id fs-id vbo-id user-fn]} @locals]
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
    (GL15/glDeleteBuffers ^Integer vbo-id)))

(defn- run-thread
  [locals mode shader-filename shader-str-atom tex-filenames title true-fullscreen? user-fn display-sync-hz]
  (init-window locals mode title shader-filename shader-str-atom tex-filenames true-fullscreen? user-fn display-sync-hz)
  (init-gl locals)
  (while (and (= :yes (:active @locals))
              (not (Display/isCloseRequested)))
    (update-and-draw locals)
    (Display/update)
    (Display/sync (:display-sync-hz @locals)))
  (destroy-gl locals)
  (Display/destroy)
  (swap! locals assoc :active :no))

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
                      (.exists (File. ^String fn)))
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
  [shader-filename files]
  (if @watcher-just-started
    ;; allow first, automatic call to pass unnoticed
    (reset! watcher-just-started false)
    ;; otherwise do the reload check
    (doseq [f files]
      (when (= (.getPath ^File f) shader-filename)
        ;; set a flag that the opengl thread will use
        (reset! reload-shader true)))))

(defn- start-watcher
  "create a watch for glsl shaders in the directory and return the global
  future atom for that watcher"
  [shader-filename]
  (let [dir (.getParent (File. ^String shader-filename))]
    (reset! watcher-just-started true)
    (watcher/watcher
     [dir]
     (watcher/rate 100)
     (watcher/file-filter watcher/ignore-dotfiles)
     (watcher/file-filter (watcher/extensions :glsl))
     (watcher/on-change (partial if-match-reload-shader shader-filename)))))

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
      (let [loc (GL20/glGetUniformLocation ^Integer pgm-id ^String key)]
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
  (sort (fn [^DisplayMode a ^DisplayMode b]
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
  (filter #(.isFullscreenCapable ^DisplayMode %) (display-modes)))

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
  (= :yes (:active @the-window-state)))

(defn inactive?
  "Returns true if the shader display is completely done running."
  []
  (= :no (:active @the-window-state)))

(defn stop
  "Stop and destroy the shader display. Blocks until completed."
  []
  (when (active?)
    (swap! the-window-state assoc :active :stopping)
    (while (not (inactive?))
      (Thread/sleep 100)))
  (remove-watch (:shader-str-atom @the-window-state) :shader-str-watch)
  (stop-watcher @watcher-future))

(defn start-shader-display
  "Start a new shader display with the specified mode. Prefer start or
   start-fullscreen for simpler usage."
  [mode shader-filename-or-str-atom textures title true-fullscreen?
   user-data user-fn display-sync-hz]
  (let [is-filename     (not (instance? clojure.lang.Atom shader-filename-or-str-atom))
        shader-filename (if is-filename
                          shader-filename-or-str-atom)
        ;; Fix for issue 15.  Normalize the given shader-filename to the
        ;; path separators that the system will use.  If user gives path/to/shader.glsl
        ;; and windows returns this as path\to\shader.glsl from .getPath, this
        ;; change should make comparison to path\to\shader.glsl work.
        shader-filename (if (and is-filename (not (nil? shader-filename)))
                          (.getPath (File. ^String shader-filename)))
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
                 (fn [x] (start-watcher shader-filename))))
        (add-watch shader-str-atom :shader-str-watch watch-shader-str-atom))
      ;; set a global window-state instead of creating a new one
      (reset! the-window-state default-state-values)
      ;; set user data
      (reset! shader-user-data user-data)
      ;; start the requested shader
      (.start (Thread.
               (fn [] (run-thread the-window-state
                                 mode
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
  "Start a new shader display in pseudo fullscreen mode. This creates
   a new borderless window which is the size of the current
   resolution. There are therefore no OS controls for closing the
   shader window. Use (stop) to close things manually."
  [shader-filename-or-str-atom
   &{:keys [display-sync-hz textures user-data user-fn]
     :or {display-sync-hz 60
          textures        [nil]
          user-data       {}
          user-fn         shader-default-fn}}]
     (let [mode (Display/getDisplayMode)]
       (undecorate-display!)
       (start-shader-display mode shader-filename-or-str-atom textures "" false user-data user-fn display-sync-hz)))

(defn throw-exceptions-on-gl-errors!
  "When v is true, throw exceptions when glGetError() returns
  non-zero.  This is the default setting.  When v is false, do not
  throw the exception.  Perhaps setting to false during a performance
  will allow you to avoid over-agressive exceptions.  Leave this true
  otherwise."  [v]
  (reset! throw-on-gl-error v))

(defn pixel-read-enable!
  "Enable reading a pixel each frame from location x,y.  Be sure x,y
   are valid or things may crash!  Results are available via the
   function (pixel) and via the atom @pixel-value"
  [x y]
  (swap! the-window-state assoc
         :pixel-read-enable true
         :pixel-read-pos-x  x
         :pixel-read-pos-y  y)
  nil)

(defn pixel-read-disable!
  "Disable reading pixel values each frame."
  []
  (swap! the-window-state assoc
         :pixel-read-enable false)
  nil)

(defn pixel
  "Return the data that was read from the currently drawn frame at the
  x,y location specified in the (pixel-read-enable! x y) call.  When
  enabled, a [red green blue] vector of floating point [0.0,1.0]
  values is returned.  Otherwise, [0.0 0.0 0.0] is returned."
  []
  (if (:pixel-read-enable @the-window-state)
    (get-pixel-value (:pixel-read-data @the-window-state))
    [0.0 0.0 0.0]))
