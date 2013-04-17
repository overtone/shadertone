// Displays a constant color.  See 00demo_intro_tour.clj for explanation.
uniform vec3 iRGB;

void main(void)
{
    gl_FragColor = vec4(iRGB, 1.0);
}
