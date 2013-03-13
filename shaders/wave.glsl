float smoothbump(float center, float width, float x) {
  float w2 = width/2.0;
  float cp = center+w2;
  float cm = center-w2;
  float c = smoothstep(cm, center, x) * (1.0-smoothstep(center, cp, x));
  return c;
}
void main(void)
{
    vec2  uv      = gl_FragCoord.xy/iResolution.xy;
    float wave    = texture2D(iChannel[0], vec2(uv.x,0.25) ).x;
    vec3  col     = vec3(0.5,0.5,0.5);
    col          += smoothbump(0.0,(3.0/iResolution.y), wave + uv.y - 0.5 );
    gl_FragColor  = vec4(col,1.0);
}
