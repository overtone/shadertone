// Displays a constant color.  See 00demo_intro_tour.clj for explanation.
// iOvertoneVolume is a user-data atom that is added implicitly by tone.clj
uniform float iOvertoneVolume;
// iRGB is added by the example code in 00demo_intro_tour.clj
uniform vec3 iRGB;

void main(void)
{
    vec3 c = iRGB + 5*vec3(iOvertoneVolume);
    gl_FragColor = vec4(c, 1.0);
}
