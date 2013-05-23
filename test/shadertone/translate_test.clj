(ns shadertone.translate-test
  (:use clojure.test
        shadertone.translate))


(deftest simplest-test
  (testing "The simplest shader."
    (let [_ (defshader test-shader
              '((slfn void main []
                      (slet [nil gl_FragColor (vec4 1.0 0.5 0.5 1.0)]
                            nil))))
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
                (slfn void main []
                      (slet [vec2 uv (/ gl_FragCoord.xy iResolution.xy)
                             nil gl_FragColor (vec4 uv.x uv.y 0.0 1.0)]
                            nil))))
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
                (slfn float smoothbump
                      [float center
                       float width
                       float x]
                      (slet [float w2 (/ width 2.0)
                             float cp (+ center w2)
                             float cm (- center w2)]
                            (* (smoothstep cm center x)
                               (- 1.0 (smoothstep center cp x)))))
                (slfn void main
                      []
                      (slet [float uv     (/ gl_FragCoord.xy iResolution.xy)
                             nil   uv.y   (- 1.0 uv.y)
                             float freq   (.x (texture2D iChannel0 (vec2 uv.x 0.25)))
                             float wave   (.x (texture2D iChannel0 (vec2 uv.x 0.75)))
                             float freqc  (smoothstep 0.0 (/ 1.0 iResolution.y) (+ freq uv.y -0.5))
                             float wavec  (smoothstep 0.0 (/ 4.0 iResolution.y) (+ wave uv.y -0.5))
                             nil   gl_FragColor (vec4 freqc wavec 0.25 1.0)]
                            nil))))
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
