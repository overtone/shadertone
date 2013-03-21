// The shadertoy uniform variables are available by default.
// Overtone uses iChannel[0] for sound data
// iChannel[1,2,3] are available for textures

// It is necessary to add the Overtone vars.
uniform float iOvertoneVolume;

void main(void) {
  vec2 uv = (gl_FragCoord.xy / iResolution.xy);
  uv.x = uv.x + 0.5*sin(0.15*iGlobalTime);
  uv.y = uv.y + 0.5*cos(0.03*iGlobalTime);
  vec4 c1 = texture2D(iChannel[1],uv);
  vec4 c2 = texture2D(iChannel[2],uv);
  vec4 c = mix(c1,c2,1.0-c1.w);  // alpha blend between two textures
  gl_FragColor = c;
}
