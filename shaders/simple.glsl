void main(void) {
  vec2 vUV = (gl_FragCoord.xy / iResolution.xy);
  gl_FragColor = vec4(vUV.x, 
                      10.0*iOvertoneVolume, 
                      0.5*sin(5.0*iGlobalTime) + 0.5, 
                      1.0);
}
