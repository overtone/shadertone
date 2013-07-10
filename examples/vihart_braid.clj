;; Overtone Translation of Vi Hart's Sound Braid by Roger Allen
;; http://www.youtube.com/watch?v=VB6a4nI0BPA
(ns vihart-braid
  (:require [overtone.live           :as o]
            [overtone.synth.stringed :as strings]
            [shadertone.tone         :as t]
            [leipzig.canon           :as lc]
            [leipzig.live            :as ll]
            [leipzig.melody          :as lm]
            [leipzig.scale           :as ls]))

;; instrument routines...
(defn pick [amp {pitch :pitch, start :time, length :duration, pan :pan}]
    (let [synth-id (o/at start (strings/ektara pitch :amp amp :gate 1 :pan pan))]
      (o/at (+ start length) (o/ctl synth-id :gate 0))))
(defmethod ll/play-note :melody [note] (pick 0.6 note))

(def sol-do   (o/sample (o/freesound-path 44929)))
(def sol-re   (o/sample (o/freesound-path 44934)))
(def sol-mi   (o/sample (o/freesound-path 44933)))
(def sol-fa   (o/sample (o/freesound-path 44931)))
(def sol-so   (o/sample (o/freesound-path 44935)))
(def sol-la   (o/sample (o/freesound-path 44932)))
(def sol-ti   (o/sample (o/freesound-path 44936)))
(def sol-do-2 (o/sample (o/freesound-path 44930)))

(defn sing-note [note]
  ;;(println note)
  (case (:pitch note)
    -4 (sol-do   :vol 0.75 :pan (:pan note))
    -3 (sol-re   :vol 0.85 :pan (:pan note))
    -2 (sol-mi   :vol 0.85 :pan (:pan note))
    -1 (sol-fa   :vol 0.85 :pan (:pan note))
    0  (sol-so   :vol 0.65 :pan (:pan note))
    1  (sol-la   :vol 0.65 :pan (:pan note))
    2  (sol-ti   :vol 0.75 :pan (:pan note))
    3  (sol-do-2 :vol 0.85 :pan (:pan note))
    4  (sol-ti   :vol 0.85 :pan (:pan note)
                 :rate (/ (Math/pow 2 (/ 14 12))
                          (Math/pow 2 (/ 11 12))))))

(def voice-1-atom (atom 0))
(def voice-2-atom (atom 0))
(def voice-3-atom (atom 0))

(defmethod ll/play-note :voice-1 [note]
  (swap! voice-1-atom (fn [x] (* 1.0 (:pitch note))))
  (sing-note note))
(defmethod ll/play-note :voice-2 [note]
  (swap! voice-2-atom (fn [x] (* 1.0 (:pitch note))))
  (sing-note note))
(defmethod ll/play-note :voice-3 [note]
  (swap! voice-3-atom (fn [x] (* 1.0 (:pitch note))))
  (sing-note note))

;; utility for Vi's panning technique
(defn add-pan
  "given a min and max pitch and notes, pan from -0.99 to 0.99
  depending on the pitch"
  [min-pitch max-pitch ns]
  (let [pans (map #(* 0.9 (- (* 2 (/ (- (:pitch %) min-pitch) (- max-pitch min-pitch))) 1)) ns)]
    (->> ns (lm/having :pan pans))))

;; gather her strands of notes into a braid
(defn make-braid [N]
  (let [strand (lm/phrase [ 1  1 2,  3 1,  2 2]
                          [nil 0 1,  2 3,  4 3])
        strand-0 (add-pan -4 4 strand)
        strand-1 (add-pan -4 4 (lc/mirror strand-0))
        strand-a (lm/times N (->> strand-0 (lm/then strand-1)))
        strand-b (lm/times N (->> strand-1 (lm/then strand-0)))]
    (->> (->> strand-a
              (lm/where :part (lm/is :voice-1)))
         (lm/with (->> strand-b
                       (lm/where :part (lm/is :voice-2))
                       (lm/after 4)))
         (lm/with (->> strand-a
                       (lm/where :part (lm/is :voice-3))
                       (lm/after 8))))))

(defn play-song [speed key song]
  (->> song
       (lm/where :part     (lm/is :melody))
       (lm/where :time     speed)
       (lm/where :duration speed)
       (lm/where :pitch    key)
       ll/play))

(defn sing-song [speed song]
  (->> song
       (lm/where :time     speed)
       (lm/where :duration speed)
       ll/play))

(t/start "examples/vihart_braid.glsl"
         :width 1280 :height 720
         :textures [:previous-frame]
         :user-data {"v1" voice-1-atom,
                     "v2" voice-2-atom,
                     "v3" voice-3-atom})

;; Vi Hart's version
(play-song (lm/bpm 120) (comp ls/low ls/C ls/major) (make-braid 5))
(sing-song (lm/bpm 120) (make-braid 3))

(comment
  ;; but with Overtone & Leipzig you can change things about...
  (play-song (lm/bpm 172) (comp ls/low ls/D ls/flat ls/mixolydian) (make-braid 3))
  (play-song (lm/bpm 92) (comp ls/low ls/C ls/minor) (make-braid 3))
)
