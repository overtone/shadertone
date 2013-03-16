(ns shadertone.tone
  ;; FIXME whittle this :use down to requirements
  (:use [overtone.helpers lib]
        [overtone.libs event deps]
        [overtone.sc defaults synth ugens buffer node foundation-groups bus]
        [overtone.sc.cgens buf-io tap]
        [overtone.studio core util])
  (:require [shadertone.shader :as s]
            [overtone.sc.server :as server]) ;; has a "stop" in it
  (:import (org.lwjgl BufferUtils)
           (org.lwjgl.opengl GL11 GL12 GL13 GL20 ARBTextureRg)))

;; ----------------------------------------------------------------------
;; Tap into the Overtone output volume and send it to iOvertoneVolume
;; volume tap synth inspired by
;;   https://github.com/samaaron/arnold/blob/master/src/arnold/voltap.clj
(defsynth vol []
  (tap "system-vol" 60 (lag (abs (in:ar 0)) 0.1)))

(defonce voltap-synth
  (vol :target (foundation-monitor-group)))

;; ----------------------------------------------------------------------
;; Grab Waveform & FFT data and send it to the iChannel[0] texture.
;; data capture fns cribbed from overtone/gui/scope.clj
(defonce WAVE-BUF-SIZE 512) ; stick to powers of 2 for fft and GL
(defonce FFTWAVE-BUF-SIZE (* 2 WAVE-BUF-SIZE))
(defonce init-wave-array (float-array (repeat WAVE-BUF-SIZE 0.0)))
(defonce init-fft-array (float-array (repeat WAVE-BUF-SIZE 0.0)))
;; synths pour data into these bufs
(defonce wave-buf (buffer WAVE-BUF-SIZE))
(defonce fft-buf (buffer WAVE-BUF-SIZE))
;; on request from ogl, stuff wave-buf & fft-buf into fftwave-float-buf
;; and use that FloatBuffer for texturing
(defonce fftwave-tex-id (atom 0))
(defonce fftwave-tex-num (atom 0))
(defonce fftwave-float-buf (-> (BufferUtils/createFloatBuffer FFTWAVE-BUF-SIZE)
                               (.put init-fft-array)
                               (.put init-wave-array)
                               (.flip)))
(defonce wave-bus-synth (bus->buf :target (foundation-monitor-group) 0 wave-buf))

(defn- ensure-internal-server!
  "Throws an exception if the server isn't internal - wave relies on
  fast access to shared buffers with the server which is currently only
  available with the internal server. Also ensures server is connected."
  []
  (when (server/server-disconnected?)
    (throw (Exception. "Cannot use waves until a server has been booted or connected")))
  (when (server/external-server?)
    (throw (Exception. (str "Sorry, it's only possible to use waves with an internal server. Your server connection info is as follows: " (server/connection-info))))))

;; FIXME -- I need to review this code because I don't quite
;; understand the data it produces.  It seems inverted (+ for quieter
;; freqs) and I don't understand the units
;; [some misunderstanding was from the guitar synth.  since it is 'live', there
;; is a low level of output at almost all times]
(defsynth bus-freqs->buf
  [in-bus 0 scope-buf 1 fft-buf-size WAVE-BUF-SIZE rate 1 db-factor 0.02]
  (let [phase     (- 1 (* rate (reciprocal fft-buf-size)))
        fft-buf   (local-buf fft-buf-size 1)
        n-samples (* 0.5 (- (buf-samples:ir fft-buf) 2))
        signal    (in in-bus 1)
        freqs     (fft fft-buf signal 0.75 HANN)
        smoothed  (pv-mag-smear fft-buf 1)
        indexer   (+ n-samples 2
                     (* (lf-saw (/ rate (buf-dur:ir fft-buf)) phase)
                        n-samples))
        indexer   (round indexer 2)
        src       (buf-rd 1 fft-buf indexer 1 1)
        freq-vals (+ 1 (* db-factor (ampdb (* src 0.00285))))]
    (record-buf freq-vals scope-buf)))

(defonce fft-bus-synth
  (bus-freqs->buf :target (foundation-monitor-group) 0 fft-buf))

;; user-fn for shader display of waveform and fft
(defn tone-fftwave-fn
  "The shader display will call this routine on every draw.  Update
  the waveform texture with FFT data in the first row and waveform
  data in the 2nd row."
  [dispatch pgm-id]
  (case dispatch ;; FIXME defmulti?
    :init ;; create & bind the texture
    (let [tex-id (GL11/glGenTextures)]
      (ensure-internal-server!)
      (reset! fftwave-tex-id tex-id)
      (GL11/glBindTexture GL11/GL_TEXTURE_2D tex-id)
      (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER
                            GL11/GL_LINEAR)
      (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER
                            GL11/GL_LINEAR)
      (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S
                            GL12/GL_CLAMP_TO_EDGE)
      (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T
                            GL12/GL_CLAMP_TO_EDGE)
      (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 ARBTextureRg/GL_R32F
                         WAVE-BUF-SIZE 2 0 GL11/GL_RED GL11/GL_FLOAT
                         fftwave-float-buf)
      (GL11/glBindTexture GL11/GL_TEXTURE_2D 0))
    :pre-draw ;; grab the data and put it in the texture for drawing.
    (do
      (if (buffer-live? wave-buf) ;; FIXME? assume fft-buf is live
        (-> fftwave-float-buf
            (.put (buffer-data fft-buf))
            (.put (buffer-data wave-buf))
            (.flip)))
      (GL13/glActiveTexture (+ GL13/GL_TEXTURE0 @fftwave-tex-num))
      (GL11/glBindTexture GL11/GL_TEXTURE_2D @fftwave-tex-id)
      (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 ARBTextureRg/GL_R32F
                         WAVE-BUF-SIZE 2 0 GL11/GL_RED GL11/GL_FLOAT
                         fftwave-float-buf))
    :post-draw ;; unbind the texture
    (do
      (GL13/glActiveTexture (+ GL13/GL_TEXTURE0 @fftwave-tex-num))
      (GL11/glBindTexture GL11/GL_TEXTURE_2D 0))
    :destroy ;;
    (do
      (GL11/glBindTexture GL11/GL_TEXTURE_2D 0)
      (GL11/glDeleteTextures @fftwave-tex-id))))

(defn- fix-fftwav-texture
  "look for the :iOvertoneAudio keyword, set the fftwave-tex-num atom"
  [[i tex]]
  (if (string? tex)
    tex ;; just return the string untouched
    (do
      (assert (keyword? tex))
      (assert (= :iOvertoneAudio tex))
      (reset! fftwave-tex-num i) ;; NOTE: multiple entries will only use last one
      nil))) ;; return nil

(defn- fix-texture-list
  "look for the :iOvertoneAudio keyword and replace it with nil"
  [textures]
  (reset! fftwave-tex-num 0)
  (map fix-fftwav-texture (map-indexed vector textures)))

;; ======================================================================
;; Simple API
(defonce tone-user-data (atom {}))
(defonce tone-user-locs (atom {}))

;; The default Overtone user-draw-fn expects:
;;   uniform float iOvertoneVolume;
;; at the top of your glsl shader.
;; Also calls tone-fftwave-fn to put waveform and fft data into:
;;   iChannel[0]
(defn tone-default-fn
  [dispatch pgm-id]
  (case dispatch ;; FIXME defmulti?
    :init ;; find Uniform Location
    (doseq [key (keys @tone-user-data)]
      (let [loc (GL20/glGetUniformLocation pgm-id key)]
        (swap! tone-user-locs assoc key loc)))
    :pre-draw
    (doseq [key (keys @tone-user-data)]
      (let [loc (@tone-user-locs key)
            val (if (= key "iOvertoneVolume")
                  ;; special handling for volume
                  (try
                    (float @(get-in voltap-synth [:taps "system-vol"]))
                    (catch Exception e 0.0))
                  ;; other user data
                  (deref (@tone-user-data key)))]
        ;;(println key loc val)
        (GL20/glUniform1f loc val))) ;; FIXME support vec2f, vec3f, vec4
    :post-draw
    nil ;; nothing to do
    :destroy
    nil ;; nothing to do
    )
  (tone-fftwave-fn dispatch pgm-id))

(defn start
  "Start a new shader display.  Pass in optional user-data and user-fn
  for custom control"
  [shader-filename
   &{:keys [width height title textures user-data user-fn]
     :or {width      600
          height     600
          title      "shadertone"
          textures   []
          user-data  {"iOvertoneVolume" (atom 0.0)}
          user-fn    tone-default-fn}}]
  (let [textures (fix-texture-list textures)]
    (reset! tone-user-data user-data)
    (s/start shader-filename
             :width    width
             :height   height
             :title    title
             :textures textures
             :user-fn  user-fn)))

(defn start-fullscreen
  [shader-filename
   &{:keys [textures user-data user-fn]
     :or {textures   []
          user-data  {"iOvertoneVolume" (atom 0.0)}
          user-fn    tone-default-fn}}]
  (let [textures (fix-texture-list textures)]
    (reset! tone-user-data user-data)
    (s/start-fullscreen shader-filename
                        :textures textures
                        :user-fn  user-fn)))

(defn stop
  []
  (s/stop))
