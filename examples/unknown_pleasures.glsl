float smoothbump(float center, float width, float x) {
  float w2 = width/2.0;
  float cp = center+w2;
  float cm = center-w2;
  float c = smoothstep(cm, center, x) * (1.0-smoothstep(center, cp, x));
  return c;
}

void main(void)
{
    float deltay = 0.02;

    vec2  uv     = gl_FragCoord.xy/iResolution.xy;
    float u      = (uv.x-0.35)/5;
    float wave   = -0.6*abs(texture2D(iChannel0,vec2(u,0.75)).x);
    float inside = smoothbump(0.5, 0.5,uv.x);
    wave         = wave*inside;
    float line   = smoothbump(0.0,(9.0/iResolution.y), wave + uv.y - deltay);
    vec3  lcolor = line*vec3(0.9,0.9,0.9);
    float mask   = smoothstep(0.0, (9.0/iResolution.y)/2.0, wave + uv.y - deltay);

    // scroll the previous frame
    vec2  uv2    = uv-vec2(0.0,deltay);
    vec3  pcolor = 1.0*texture2D(iChannel1,uv2).rgb;

    // mix the two, masking out lower part of wave
    gl_FragColor = vec4(mix(lcolor,pcolor,mask),1.0);
}
