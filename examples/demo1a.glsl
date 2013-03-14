uniform float iOvertoneVolume;
void main(void) {
  float v = 10.0 * iOvertoneVolume;
  vec3 c = vec3(v, 0.5*v, 0.2);
  gl_FragColor = vec4(c, 1.0); // red, green, blue, alpha
}
