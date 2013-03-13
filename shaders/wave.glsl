float smoothbump(float center, float width, float x) {
  float w2 = width/2.0;
  float cp = center+w2;
  float cm = center-w2;
  float c = smoothstep(cm, center, x) * (1.0-smoothstep(center, cp, x));
  return c;
}

void main(void)
{
    vec2  uv     = gl_FragCoord.xy/iResolution.xy;
    // not quite sure what the units are on the fft...
    float freq   = -0.33 + (texture2D(iChannel[0],vec2(uv.x,0.25)).x/-6.0);
    float wave   = texture2D(iChannel[0],vec2(uv.x,0.75)).x;
    float freqc  = smoothbump(0.0,(3.0/iResolution.y), freq + uv.y - 0.5 );
    float wavec  = smoothbump(0.0,(3.0/iResolution.y), wave + uv.y - 0.5 );
    gl_FragColor = vec4(freqc, wavec, 0.25,1.0);
}
