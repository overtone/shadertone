(ns shadertone.tone
  (:use [overtone.live])
  (:import (org.lwjgl.opengl GL20)))

;; volume tap inspired by
;;   https://github.com/samaaron/arnold/blob/master/src/arnold/voltap.clj
(defsynth vol []
  (tap "system-vol" 60 (lag (abs (in:ar 0)) 0.1)))

(defonce voltap-synth
  (vol :target (foundation-monitor-group)))

;; The default Overtone user-draw-fn
;; add:
;;   uniform float iOvertoneVolume;
;; to the top of your glsl shader.  Then, pass this function via the start call.
(defn overtone-volume
  "The shader display will call this routine on every draw.  Update
  the iOvertoneVolume uniform variable."
  [pgm-id]
  (let [i-overtone-volume-loc (GL20/glGetUniformLocation pgm-id
                                                         "iOvertoneVolume")
        cur-volume (try
                     (float @(get-in voltap-synth [:taps "system-vol"]))
                     (catch Exception e 0.0))]
    (GL20/glUniform1f i-overtone-volume-loc cur-volume)))
