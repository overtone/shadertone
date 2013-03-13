(ns shadertone.tone
  ;; FIXME whittle this :use down to requirements
  (:use [overtone.helpers lib]
        [overtone.libs event deps]
        [overtone.sc defaults server synth ugens buffer node foundation-groups bus]
        [overtone.sc.cgens buf-io tap]
        [overtone.studio core util])
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
;; Waveform & FFT data fns cribbed from overtone/gui/scope.clj
(defonce WAVE-BUF-SIZE 512) ; stick to powers of 2 for fft and GL
(defonce FFTWAVE-BUF-SIZE 1024)
(defonce init-wave-array (float-array (repeat WAVE-BUF-SIZE 0.0)))
(defonce init-fft-array (float-array (repeat WAVE-BUF-SIZE 0.0)))
;; synths pour data into these bufs
(defonce wave-buf (buffer WAVE-BUF-SIZE))
(defonce fft-buf (buffer WAVE-BUF-SIZE))
;; on request from ogl, stuff wave-buf & fft-buf into fftwave-float-buf
;; and use that FloatBuffer for texturing
(defonce fftwave-tex-id (atom 0))
(def fftwave-float-buf (-> (BufferUtils/createFloatBuffer FFTWAVE-BUF-SIZE)
                           (.put init-fft-array)
                           (.put init-wave-array)
                           (.flip)))
(defonce wave-bus-synth (bus->buf :target (foundation-monitor-group) 0 wave-buf))

;; FIXME -- I need to review this code because I don't quite
;; understand the data it produces.  It seems inverted (+ for quieter
;; freqs) and I don't understand the units
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
  (case dispatch
   ;; FIXME need 4 routines: gl-init, gl-pre-draw, gl-post-draw, gl-destroy
   :pre-draw (do
               (if (buffer-live? wave-buf) ;; FIXME? assume fft-buf is live
                 (-> fftwave-float-buf
                     (.put (buffer-data fft-buf))
                     (.put (buffer-data wave-buf))
                     (.flip)))
               (GL13/glActiveTexture GL13/GL_TEXTURE0)
               (GL11/glBindTexture GL11/GL_TEXTURE_2D @fftwave-tex-id)
               (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 ARBTextureRg/GL_R32F
                                  WAVE-BUF-SIZE 2 0 GL11/GL_RED GL11/GL_FLOAT
                                  fftwave-float-buf))

   :init (let [tex-id (GL11/glGenTextures)]
           (println "init!")
           (ensure-internal-server!)
           (reset! fftwave-tex-id tex-id)
           (GL11/glBindTexture GL11/GL_TEXTURE_2D tex-id)
           (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER
                                 GL11/GL_LINEAR )
           (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER
                                 GL11/GL_LINEAR )
           (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S
                                 GL12/GL_CLAMP_TO_EDGE )
           (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T
                                 GL12/GL_CLAMP_TO_EDGE)
           (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 ARBTextureRg/GL_R32F
                              WAVE-BUF-SIZE 2 0 GL11/GL_RED GL11/GL_FLOAT
                              fftwave-float-buf)
           (GL11/glBindTexture GL11/GL_TEXTURE_2D 0))))
