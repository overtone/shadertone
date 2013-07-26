(ns shadertone.translate-test
  (:use clojure.test
        shadertone.translate))


(deftest simplest-test
  (testing "The simplest shader."
    (let [_ (defshader test-shader
              '((defn void main []
                  (setq gl_FragColor (vec4 1.0 0.5 0.5 1.0)))))
          test-str (str test-shader)
          gold-str
"void main(void) {
gl_FragColor = vec4(1.0,0.5,0.5,1.0);
}
"
          ]
      (is (= test-str gold-str)))))

(deftest simple-test
  (testing "A simple shader."
    (let [_ (defshader test-shader
              '((uniform vec3 iResolution)
                (defn void main []
                  (setq vec2 uv (/ gl_FragCoord.xy iResolution.xy))
                  (setq gl_FragColor (vec4 uv.x uv.y 0.0 1.0)))))
          test-str (str test-shader)
          gold-str
"uniform vec3 iResolution;
void main(void) {
vec2 uv = (gl_FragCoord.xy / iResolution.xy);
gl_FragColor = vec4(uv.x,uv.y,0.0,1.0);
}
"
          ]
      (is (= test-str gold-str)))))

(deftest wave-test
  (testing "wave shader"
    (let [_ (defshader test-shader
              '((uniform vec3 iResolution)
                (uniform sampler2D iChannel0)
                (defn float smoothbump
                  [float center
                   float width
                   float x]
                  (setq float w2 (/ width 2.0))
                  (setq float cp (+ center w2))
                  (setq float cm (- center w2))
                  (return (* (smoothstep cm center x)
                             (- 1.0 (smoothstep center cp x)))))
                (defn void main []
                  (setq float uv     (/ gl_FragCoord.xy iResolution.xy))
                  (setq uv.y   (- 1.0 uv.y))
                  (setq float freq   (.x (texture2D iChannel0 (vec2 uv.x 0.25))))
                  (setq float wave   (.x (texture2D iChannel0 (vec2 uv.x 0.75))))
                  (setq float freqc  (smoothstep 0.0 (/ 1.0 iResolution.y) (+ freq uv.y -0.5)))
                  (setq float wavec  (smoothstep 0.0 (/ 4.0 iResolution.y) (+ wave uv.y -0.5)))
                  (setq gl_FragColor (vec4 freqc wavec 0.25 1.0)))))
          test-str (str test-shader)
          gold-str
"uniform vec3 iResolution;
uniform sampler2D iChannel0;
float smoothbump(float center,float width,float x) {
float w2 = (width / 2.0);
float cp = (center + w2);
float cm = (center - w2);
return((smoothstep(cm,center,x) * (1.0 - smoothstep(center,cp,x))));
}
void main(void) {
float uv = (gl_FragCoord.xy / iResolution.xy);
uv.y = (1.0 - uv.y);
float freq = (texture2D(iChannel0,vec2(uv.x,0.25))).x;
float wave = (texture2D(iChannel0,vec2(uv.x,0.75))).x;
float freqc = smoothstep(0.0,(1.0 / iResolution.y),(freq + uv.y + -0.5));
float wavec = smoothstep(0.0,(4.0 / iResolution.y),(wave + uv.y + -0.5));
gl_FragColor = vec4(freqc,wavec,0.25,1.0);
}
"
          ]
      (is (= test-str gold-str)))))

(deftest forloop-test
  (testing "forloop shader."
    (let [_ (defshader test-shader
              '((defn void main []
                  (setq vec3 c (vec3 0.0))
                  (forloop [ (setq int i 0)
                             (<= i 10)
                             (setq i (+ i 1)) ]
                           (setq c (+ c (vec3 0.1))))
                  (setq gl_FragColor (vec4 c 1.0)))))
          test-str (str test-shader)
          gold-str ;; FIXME this is a bit ugly text...
"void main(void) {
vec3 c = vec3(0.0);
for( int i = 0;
 (i <= 10); i = (i + 1);
 ) {
c = (c + vec3(0.1));
}
gl_FragColor = vec4(c,1.0);
}
"
          ]
      (is (= test-str gold-str)))))

(deftest whileloop-test
  (testing "whileloop shader."
    (let [_ (defshader test-shader
              '((defn void main []
                  (setq vec3 c (vec3 0.0))
                  (setq int i 0)
                  (while (<= i 10)
                    (setq i (+ i 1))
                    (setq c (+ c (vec3 0.1))))
                  (setq gl_FragColor (vec4 c 1.0)))))
          test-str (str test-shader)
          gold-str ;; FIXME this is a bit ugly text...
"void main(void) {
vec3 c = vec3(0.0);
int i = 0;
while(i <= 10) {
i = (i + 1);
c = (c + vec3(0.1));
}
gl_FragColor = vec4(c,1.0);
}
"
          ]
      (is (= test-str gold-str)))))
