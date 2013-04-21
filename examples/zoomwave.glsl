float smoothbump(float center, float width, float x) {
  float w2 = width/2.0;
  float cp = center+w2;
  float cm = center-w2;
  float c = smoothstep(cm, center, x) * (1.0-smoothstep(center, cp, x));
  return c;
}

vec3 hsv2rgb(float h,float s,float v) {
  return mix(vec3(1.),clamp((abs(fract(h+vec3(3.,2.,1.)/3.)*6.-3.)-1.),0.,1.),s)*v;
}

void main(void)
{
    // draw a colorful waveform
    vec2  uv     = gl_FragCoord.xy/iResolution.xy;
    float wave   = texture2D(iChannel0,vec2(uv.x,0.75)).x;
    wave         = smoothbump(0.0,(6.0/iResolution.y), wave + uv.y - 0.5);
    vec3  wc     = wave*hsv2rgb(fract(iGlobalTime/2.0),0.9,0.9);

    // zoom into the previous frame
    float zf     = -0.05;
    vec2  uv2    = (1.0+zf)*uv-(zf/2.0,zf/2.0);
    vec3  pc     = 0.95*texture2D(iChannel1,uv2).rgb;

    // mix the two
    gl_FragColor = vec4(vec3(wc+pc),1.0);
}
