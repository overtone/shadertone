// The shadertoy uniform variables are available by default.
// Overtone uses iChannel[0] for sound data
// iChannel[1,2,3] are available for textures

// It is necessary to add the Overtone vars.
uniform float iOvertoneVolume;

void main(void) {
  vec2 uv = (gl_FragCoord.xy / iResolution.xy);
  uv.x = uv.x + 0.25*sin(iGlobalTime);
  uv.y = uv.y + 0.25*cos(iGlobalTime);
  vec4 c = texture2D(iChannel[1],uv);
  gl_FragColor = c;
}
