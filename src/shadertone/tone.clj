(ns shadertone.tone
  (:use [overtone.live]
        [overtone.studio.util])
  (:import (org.lwjgl BufferUtils)
           (org.lwjgl.opengl GL11 GL12 GL13 GL20 ARBTextureRg)))

;; ----------------------------------------------------------------------
;; volume tap inspired by
;;   https://github.com/samaaron/arnold/blob/master/src/arnold/voltap.clj
(defsynth vol []
  (tap "system-vol" 60 (lag (abs (in:ar 0)) 0.1)))

(defonce voltap-synth
  (vol :target (foundation-monitor-group)))

;; ----------------------------------------------------------------------
;; The default Overtone user-draw-fn
;; add:
;;   uniform float iOvertoneVolume;
;; to the top of your glsl shader.  Then, pass this function via the start call.
(defn overtone-volume
  "The shader display will call this routine on every draw.  Update
  the iOvertoneVolume uniform variable."
  [dispatch pgm-id]
  (when (= :pre-draw dispatch)
    (let [i-overtone-volume-loc (GL20/glGetUniformLocation pgm-id
                                                           "iOvertoneVolume")
          cur-volume (try
                       (float @(get-in voltap-synth [:taps "system-vol"]))
                       (catch Exception e 0.0))]
      (GL20/glUniform1f i-overtone-volume-loc cur-volume))))

;; ----------------------------------------------------------------------
;; Waveform data fns (cribbed from overtone/gui/scope.clj)
;; going to need init/draw/destroy fns...
(defonce WAVE-BUF-SIZE 512) ; size must be a power of 2 for FFT
(defonce init-wave-array (float-array (repeat WAVE-BUF-SIZE 0.0)))
(defonce wave-buf (buffer WAVE-BUF-SIZE))
(def wave-float-buf (-> (BufferUtils/createFloatBuffer WAVE-BUF-SIZE)
                        (.put init-wave-array)
                        (.flip)))
(defonce wave-bus-synth (bus->buf :target (foundation-monitor-group) 0 wave-buf))
(defonce wave-tex-id (atom 0))

;; FIXME add check
(defn- ensure-internal-server!
  "Throws an exception if the server isn't internal - wave relies on
  fast access to shared buffers with the server which is currently only
  available with the internal server. Also ensures server is connected."
  []
  (when (server-disconnected?)
    (throw (Exception. "Cannot use waves until a server has been booted or connected")))
  (when (external-server?)
    (throw (Exception. (str "Sorry, it's only possible to use waves with an internal server. Your server connection info is as follows: " (connection-info))))))

(defn overtone-waveform
  "The shader display will call this routine on every draw.  Update
  the waveform texture."
  [dispatch pgm-id]
  (cond
   ;; FIXME need 4? routines? gl-init, gl-pre-draw, gl-post-draw, gl-destroy
   (= :pre-draw dispatch)
   (do
     (if (buffer-live? wave-buf)
       (-> wave-float-buf
           (.put (buffer-data wave-buf))
           (.flip)))
     (GL13/glActiveTexture GL13/GL_TEXTURE0)
     (GL11/glBindTexture GL11/GL_TEXTURE_2D @wave-tex-id)
     (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 ARBTextureRg/GL_R32F
                        512 1 0 GL11/GL_RED GL11/GL_FLOAT
                        wave-float-buf))

   (= :init dispatch)
   (let [tex-id (GL11/glGenTextures)]
     (ensure-internal-server!)
     (reset! wave-tex-id tex-id)
     (GL11/glBindTexture GL11/GL_TEXTURE_2D tex-id)
     (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR )
     (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR )
     (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE )
     (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
     (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 ARBTextureRg/GL_R32F
                        512 1 0 GL11/GL_RED GL11/GL_FLOAT
                        wave-float-buf)
     (GL11/glBindTexture GL11/GL_TEXTURE_2D 0))))
