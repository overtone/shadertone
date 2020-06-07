// The shadertoy uniform variables are available by default.
// iChannel0,2 should contain 2D textures
// use iChannel0's alpha to blend between 1 & 2

void main(void) {
  vec2 uv = (gl_FragCoord.xy / iResolution.xy);
  uv.x = uv.x + 0.5*sin(0.15*iTime);
  uv.y = uv.y + 0.5*cos(0.03*iTime);
  vec4 c1 = texture2D(iChannel0,uv);
  vec4 c2 = texture2D(iChannel1,uv);
  vec4 c = mix(c1,c2,1.0-c1.w);  // alpha blend between two textures
  gl_FragColor = c;
}
