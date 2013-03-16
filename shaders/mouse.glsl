// just a dumb test shader to be sure the mouse works
// click and drag the mouse

float smoothbump(float center, float width, float x) {
  float w2 = width/2.0;
  float cp = center+w2;
  float cm = center-w2;
  float c = smoothstep(cm, center, x) * (1.0-smoothstep(center, cp, x));
  return c;
}

void main(void) {
  vec2  uv = (gl_FragCoord.xy / iResolution.xy);
  vec4  m  = iMouse / iResolution.xyxy;
  float m0 = (m.z > 0.0) ? 0.25 : 0.0;
  float m1 = smoothbump(m.x,0.05,uv.x) *
             smoothbump(m.y,0.05,uv.y);
  float m2 = smoothbump(abs(m.z),0.05,uv.x) *
             smoothbump(abs(m.w),0.05,uv.y);
  gl_FragColor = vec4(m1,m0,m2,1.0);
}
