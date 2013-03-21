(ns shadertone.shader
  (:require [watchtower.core :as watcher])
  (:import (java.awt.image BufferedImage DataBuffer DataBufferByte)
           (java.io File FileInputStream)
           (java.nio IntBuffer FloatBuffer ByteOrder)
           (java.util Calendar)
           (javax.imageio ImageIO)
           (org.lwjgl BufferUtils)
           (org.lwjgl.input Mouse)
           (org.lwjgl.opengl ContextAttribs Display DisplayMode
                             GL11 GL12 GL13 GL15 GL20
                             PixelFormat)))

;; ======================================================================
;; State Variables
;; The globals atom is a map of state variables for use in the gl thread
(defonce globals (atom {:active              :no  ;; :yes/:stopping/:no
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
                        :shader-filename     ""
                        :vs-id               0
                        :fs-id               0
                        :pgm-id              0
                        ;; shader uniforms
                        :i-resolution-loc    0
                        :i-global-time-loc   0
                        :i-channel-time-loc  0
                        :i-mouse-loc         0
                        :i-channel-loc       0
                        :i-date-loc          0
                        :channel-time-buffer (-> (BufferUtils/createFloatBuffer 4)
                                                 (.put (float-array
                                                        [0.0 0.0 0.0 0.0]))
                                                 (.flip))
                        :channel-buffer      (-> (BufferUtils/createIntBuffer 4)
                                                 (.put (int-array [0 1 2 3]))
                                                 (.flip))
                        ;; textures
                        :tex-filenames       []
                        :tex-ids             []
                        ;; a user draw function
                        :user-fn             nil
                        }))
;; The reload-shader ref communicates across the gl & watcher threads
(defonce reload-shader (ref false))

;; ======================================================================

(defn- init-window
  "Initialise a shader-powered window with the specified
   display-mode. If true-fullscreen? is true, fullscreen mode is
   attempted if the display-mode is compatible. See display-modes for a
   list of available modes and fullscreen-display-modes for a list of
   fullscreen compatible modes.."
  [display-mode title shader-filename tex-filenames true-fullscreen? user-fn display-sync-hz]
  (let [width               (.getWidth display-mode)
        height              (.getHeight display-mode)
        pixel-format        (PixelFormat.)
        context-attributes  (-> (ContextAttribs. 2 1)) ;; GL2.1
        current-time-millis (System/currentTimeMillis)]
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
           :tex-filenames   tex-filenames
           :user-fn         user-fn)
    (Display/setDisplayMode display-mode)
    (when true-fullscreen?
      (Display/setFullscreen true))
    (Display/setTitle title)
    (Display/setVSyncEnabled true)
    (Display/setLocation 0 0)
    (Display/create pixel-format context-attributes)))

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
       "}\n"))

(defn- slurp-fs
  "do whatever it takes to modify shadertoy fragment shader source to
  be useable"
  [filename]
  (let [file-str (slurp filename)
        file-str (str "#version 120\n"
                      "uniform vec3      iResolution;\n"
                      "uniform float     iGlobalTime;\n"
                      "uniform float     iChannelTime[4];\n"
                      "uniform vec4      iMouse;\n"
                      "uniform sampler2D iChannel[4];\n"
                      "uniform vec4      iDate;\n"
                      "\n"
                      file-str)]
    file-str))

(defn- load-shader
  [shader-str shader-type]
  (let [shader-id         (GL20/glCreateShader shader-type)
        _                 (GL20/glShaderSource shader-id shader-str)
        _                 (GL20/glCompileShader shader-id)
        gl-compile-status (GL20/glGetShaderi shader-id GL20/GL_COMPILE_STATUS)]
    (when (== gl-compile-status GL11/GL_FALSE)
      (println "ERROR: Loading a Shader:")
      (println (GL20/glGetShaderInfoLog shader-id 10000)))
    shader-id))

(defn- init-shaders
  []
  (let [vs-id                 (load-shader vs-shader GL20/GL_VERTEX_SHADER)
        fs-shader             (slurp-fs (:shader-filename @globals))
        _                     (println "Loading shader:" (:shader-filename @globals))
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
        i-channel-time-loc    (GL20/glGetUniformLocation pgm-id "iChannelTime")
        i-mouse-loc           (GL20/glGetUniformLocation pgm-id "iMouse")
        i-channel-loc         (GL20/glGetUniformLocation pgm-id "iChannel")
        i-date-loc            (GL20/glGetUniformLocation pgm-id "iDate")
        ]
    (swap! globals
           assoc
           :vs-id vs-id
           :fs-id fs-id
           :pgm-id pgm-id
           :i-resolution-loc i-resolution-loc
           :i-global-time-loc i-global-time-loc
           :i-channel-time-loc i-channel-time-loc
           :i-mouse-loc i-mouse-loc
           :i-channel-loc i-channel-loc
           :i-date-loc i-date-loc)))

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
        buffer (-> buffer
                   ;;(.order (ByteOrder/nativeOrder)) ?
                   (.put data 0 (alength data)))]
    buffer))

(defn- load-texture
  "load, bind texture from filename.  return tex-id.  returns nil if filename is nil"
  [filename]
  (if filename
    (let [_               (println "Loading texture:" filename)
          image           (-> (FileInputStream. filename)
                              (ImageIO/read))
          image-type      (.getType image)
          image-bytes     (if (or (= image-type BufferedImage/TYPE_3BYTE_BGR)
                                  (= image-type BufferedImage/TYPE_INT_RGB))
                            3
                            (if (or (= image-type BufferedImage/TYPE_4BYTE_ABGR)
                                    (= image-type BufferedImage/TYPE_INT_ARGB))
                              4
                              0)) ;; unhandled image type--what to do?
          _               (println "image-type"
                                   (cond
                                     (= image-type BufferedImage/TYPE_3BYTE_BGR)  "TYPE_3BYTE_BGR"
                                     (= image-type BufferedImage/TYPE_INT_RGB)    "TYPE_INT_RGB"
                                     (= image-type BufferedImage/TYPE_4BYTE_ABGR) "TYPE_4BYTE_ABGR"
                                     (= image-type BufferedImage/TYPE_INT_ARGB)   "TYPE_INT_ARGB"
                                     :else image-type))
          _               (assert (> image-bytes 0)) ;; die on unhandled image
          internal-format (cond
                            (= image-type BufferedImage/TYPE_3BYTE_BGR)  GL11/GL_RGB8
                            (= image-type BufferedImage/TYPE_INT_RGB)    GL11/GL_RGB8
                            (= image-type BufferedImage/TYPE_4BYTE_ABGR) GL11/GL_RGBA8
                            (= image-type BufferedImage/TYPE_INT_ARGB)   GL11/GL_RGBA8)
          format          (cond
                            (= image-type BufferedImage/TYPE_3BYTE_BGR)  GL12/GL_BGR
                            (= image-type BufferedImage/TYPE_INT_RGB)    GL11/GL_RGB
                            (= image-type BufferedImage/TYPE_4BYTE_ABGR) GL12/GL_BGRA
                            (= image-type BufferedImage/TYPE_INT_ARGB)   GL11/GL_RGBA)
          nbytes          (* image-bytes (.getWidth image) (.getHeight image))
          buffer          (-> (BufferUtils/createByteBuffer nbytes)
                              (put-texture-data image (= image-bytes 4))
                              (.flip))
          tex-id          (GL11/glGenTextures)
          ]
      (GL11/glBindTexture GL11/GL_TEXTURE_2D tex-id)
      (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER
                            GL11/GL_LINEAR)
      (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER
                            GL11/GL_LINEAR) ;; mipmaps?
      (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S
                            GL11/GL_REPEAT)
      (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T
                            GL11/GL_REPEAT)
      (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 internal-format ;GL11/GL_RGB8
                         (.getWidth image)  (.getHeight image) 0
                         format ;GL12/GL_BGR ;; switch RGB/BGR
                         GL11/GL_UNSIGNED_BYTE
                         buffer)
      tex-id)))

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
    (init-shaders)
    (init-textures)
    (when user-fn
      (user-fn :init (:pgm-id @globals)))))

(defn- try-reload-shader
  []
  (let [{:keys [vs-id fs-id pgm-id shader-filename user-fn]} @globals
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
      (let [_ (println "Reloading shader:" shader-filename)
            i-resolution-loc   (GL20/glGetUniformLocation new-pgm-id "iResolution")
            i-global-time-loc  (GL20/glGetUniformLocation new-pgm-id "iGlobalTime")
            i-channel-time-loc (GL20/glGetUniformLocation new-pgm-id "iChannelTime")
            i-mouse-loc        (GL20/glGetUniformLocation new-pgm-id "iMouse")
            i-channel-loc      (GL20/glGetUniformLocation new-pgm-id "iChannel")
            i-date-loc         (GL20/glGetUniformLocation new-pgm-id "iDate")]
        (GL20/glUseProgram new-pgm-id)
        (when user-fn
          (user-fn :init new-pgm-id))
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
               :i-channel-time-loc i-channel-time-loc
               :i-mouse-loc i-mouse-loc
               :i-channel-loc i-channel-loc
               :i-date-loc i-date-loc)))))

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
                channel-time-buffer channel-buffer
                old-pgm-id old-fs-id
                tex-ids
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

    (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)

    (when user-fn
      (user-fn :pre-draw pgm-id))

    ;; activate textures
    (dotimes [i (count tex-ids)]
      (when (nth tex-ids i)
        (GL13/glActiveTexture (+ GL13/GL_TEXTURE0 i))
        (GL11/glBindTexture GL11/GL_TEXTURE_2D (nth tex-ids i))))

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
    (GL20/glUniform1 i-channel-loc channel-buffer)
    (GL20/glUniform4f i-date-loc cur-year cur-month cur-day cur-seconds)
    ;; get vertex array ready
    (GL11/glEnableClientState GL11/GL_VERTEX_ARRAY)
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo-id)
    (GL11/glVertexPointer 4 GL11/GL_FLOAT 0 0)
    ;; Draw the vertices
    (GL11/glDrawArrays GL11/GL_TRIANGLES 0 vertices-count)
    ;; Put everything back to default (deselect)
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
    (GL11/glDisableClientState GL11/GL_VERTEX_ARRAY)

    ;; unbind textures
    (dotimes [i (count tex-ids)]
      (when (nth tex-ids i)
        (GL13/glActiveTexture (+ GL13/GL_TEXTURE0 i))
        (GL11/glBindTexture GL11/GL_TEXTURE_2D 0)))

    (when user-fn
      (user-fn :post-draw pgm-id))

    (GL20/glUseProgram 0)
    ;;(println "draw errors?" (GL11/glGetError))
    ))

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
    (draw)))

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
  [mode shader-filename tex-filenames title true-fullscreen? user-fn display-sync-hz]
  (init-window mode title shader-filename tex-filenames true-fullscreen? user-fn display-sync-hz)
  (init-gl)
  (while (and (= :yes (:active @globals))
              (not (Display/isCloseRequested)))
    (update)
    (Display/update)
    (Display/sync (:display-sync-hz @globals)))
  (destroy-gl)
  (Display/destroy)
  (swap! globals assoc :active :no))

(defn good-tex-count
  [textures]
  (if (<= (count textures) 4)
    true
    (do
      (println "ERROR: number of textures must be <= 4")
      false)))

(defn files-exist
  "check to see that the filenames actually exist.  One tweak is to
  allow nil filenames.  Those are important placeholders"
  [filenames]
  (reduce #(and %1 %2)
          (for [fn filenames]
            (if (or (nil? fn)
                    (.exists (File. fn)))
              true
              (do
                (println "ERROR:" fn "does not exist.")
                false)))))

(defn sane-user-inputs
  [mode shader-filename textures title true-fullscreen? user-fn]
  (and (good-tex-count textures)
       (files-exist (flatten [shader-filename textures]))))

;; watch the shader directory & reload the current shader if it changes.
(defn- if-match-reload-shader
  [files]
  (doseq [f files]
    (when (= (.getPath f) (:shader-filename @globals))
      ;; set a flag that the opengl thread will use
      (dosync (ref-set reload-shader true)))))

(defonce __WATCH-SHADERS-FILE__
  (watcher/watcher
   ["shaders/" "examples/"]
   (watcher/rate 100)
   (watcher/file-filter watcher/ignore-dotfiles)
   (watcher/file-filter (watcher/extensions :glsl))
   (watcher/on-change #(if-match-reload-shader %))))

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
      (Thread/sleep 100))))

(defn start-shader-display
  "Start a new shader display with the specified mode. Prefer start or
   start-fullscreen for simpler usage."
  [mode shader-filename textures title
   true-fullscreen? user-fn display-sync-hz]
  (when (sane-user-inputs mode shader-filename textures title true-fullscreen? user-fn)
    ;; stop the current shader
    (stop)
    ;; start the requested shader
    (.start (Thread.
             (fn [] (run-thread mode
                               shader-filename
                               textures
                               title
                               true-fullscreen?
                               user-fn
                               display-sync-hz))))))

(defn start
  "Start a new shader display. Forces the display window to be
   decorated (i.e. have a title bar)."
  [shader-filename
   &{:keys [width height title display-sync-hz
            textures user-fn]
     :or {width   600
          height  600
          title   "shadertone"
          display-sync-hz 60
          textures []
          user-fn nil}}]
  (let [mode (DisplayMode. width height)]
    (decorate-display!)
    (start-shader-display mode shader-filename textures title false user-fn display-sync-hz)))

(defn start-fullscreen
  "Start a new shader display in pseudo fullscreen mode. This creates a
   new borderless window which is the size of the current
   resolution. There are therefore no OS controls for closing the shader
   window. Use (stop) to close things manually. "
  [shader-filename
   &{:keys [display-sync-hz textures user-fn]
     :or {display-sync-hz 60
          textures [nil]
          user-fn nil}}]
     (let [mode (first (display-modes))]
       (undecorate-display!)
       (start-shader-display mode shader-filename textures "" false user-fn display-sync-hz)))
