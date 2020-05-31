// The shadertoy uniform variables are available by default.

// It is necessary to add the Overtone vars.
uniform float iOvertoneVolume;

void main(void) {
  vec2 uv = (gl_FragCoord.xy / iResolution.xy);
  // find the center and use distance from the center to vary the
  // green component
  vec2 uv2 = uv - 0.5;
  float r = sqrt(uv2.x*uv2.x + uv2.y*uv2.y);
  gl_FragColor = vec4(uv.x,
                      20.0*iOvertoneVolume*(1-r),
                      0.5*sin(3.0*iTime)+0.5,
                      1.0);
}
