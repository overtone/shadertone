void main(void) {
  vec2 uv = (gl_FragCoord.xy / iChannelResolution[0].xy);
  gl_FragColor = texture2D(iChannel0,uv) * vec4(0.0,1.0,0.0,1.0) * abs(sin(iTime));
}
