uniform float iOvertoneVolume;
uniform float t0;
void main(void)
{
    vec3  c      = vec3(0.0);
    float act    = abs(cos(iTime));
    vec2  uv     = gl_FragCoord.xy/iResolution.xy;
    float aspect = iResolution.x / iResolution.y;
    vec2  ar     = vec2( (aspect < 1.0) ? 1.0/aspect : 1.0,
                         (aspect < 1.0) ? 1.0 : 1.0/aspect);
    uv *= ar;
    uv *= (3.0 + 2.0*sin(iTime/3.0));
    for(int i = 0; i < 6; i++) {
        vec2 xy  = vec2(sin(iTime-11.0*abs(t0)+6.28*(i/6.0)),
                        cos(iTime+23.0*abs(t0)+6.28*(i/6.0)));
        vec2 uv2 = uv - 2.0*ar + xy;
        float r  = sqrt(uv2.x*uv2.x + uv2.y*uv2.y);
        c += (vec3(0.0,0.15*(1.0-act),0.1*(act)) *
              vec3(min(0.9,pow(r,8.0)*2*iOvertoneVolume)));
        c += (vec3(1.0*act,0.0,0.5*act) *
              vec3(max(0.0,4.0*pow(8.0*t0+0.5,0.75)*(1.0-r)-1.6)));
    }
    gl_FragColor = vec4(c, 1.0);
}
