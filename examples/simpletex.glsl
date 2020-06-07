// The shadertoy uniform variables are available by default.
// iChannel1,2 are should have 2D textures

// It is necessary to add the Overtone vars.
uniform float iOvertoneVolume;

void main(void) {
  vec2 uv = (gl_FragCoord.xy / iResolution.xy);
  uv.x = uv.x + 0.5*sin(0.15*iTime);
  uv.y = uv.y + 0.5*cos(0.03*iTime);
  vec4 c1 = texture2D(iChannel1,uv);
  vec4 c2 = texture2D(iChannel2,uv);
  vec4 c = mix(c1,c2,10.0*iOvertoneVolume);
  gl_FragColor = c;
}
