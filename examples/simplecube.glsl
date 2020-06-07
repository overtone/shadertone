// inspired by https://www.shadertoy.com/view/Xdf3zM by Simon Green
vec3 rotateX(vec3 p, float a)
{
    float sa = sin(a);
    float ca = cos(a);
    return vec3(p.x, ca*p.y - sa*p.z, sa*p.y + ca*p.z);
}

vec3 rotateY(vec3 p, float a)
{
    float sa = sin(a);
    float ca = cos(a);
    return vec3(ca*p.x + sa*p.z, p.y, -sa*p.x + ca*p.z);
}
void main(void) {
    vec2  pixel = (gl_FragCoord.xy / iResolution.xy)*2.0-1.0;
    float asp = iResolution.x / iResolution.y;
    vec3  rd = normalize(vec3(asp*pixel.x, pixel.y, -2.0));
    vec2  mouse = iMouse.xy / iResolution.xy;
    float roty = 0.0;
    float rotx = 0.0;
    if (iMouse.z <= 0.0) {
        roty = iTime*0.25;
    } else {
        rotx = (mouse.y-0.5)*3.0;
        roty = -(mouse.x-0.5)*6.0;
    }
    rd = rotateX(rd, rotx);
    rd = rotateY(rd, roty);
    vec4 c = textureCube(iChannel0, rd);
    gl_FragColor = c;
}
