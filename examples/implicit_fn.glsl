// ideas from http://www.iquilezles.org/www/articles/distance/distance.htm
const float PI = 3.1415926535; // is this in glsl somewhere?

// some vars that you also may want to adjust
float PETALS = 5;
float SPIRAL = 1.5;

// convert the fragment coordinates (fc.x,fc.y) to a screen u,v space.
vec2 getScreenUV(vec2 fc) {
    vec2 uv = fc/iResolution.xy;  // uv = [0,1)
    uv = 2.0*uv-1.0;              // uv = [-1,1)
    float aspect_ratio = iResolution.x / iResolution.y;
    if(aspect_ratio < 1.0) {
        uv.x /= aspect_ratio;     // u  = [-ar,ar), v = [-1,1)
    } else {
        uv.y /= aspect_ratio;     // u  = [-1,1),  v = [-ar,ar)
    }
    return(uv);
}

// convert x,y rectangular coordinates to polar r,theta
vec2 getPolar(vec2 uv) {
     vec2 rt;
     rt.x = length(uv);
     rt.y = atan(uv.y,uv.x);
     return(rt);
}
// ======================================================================
// a fun function to display.  Try changing PETALS & SPIRAL
float fn(vec2 rt)
{
    float r = rt.x;
    float t = rt.y;
    float v = r - 1 + 0.5*sin(PETALS*t+SPIRAL*r*r);
    return v;
}
// ======================================================================
// a series of different ways to color that function
//   try each one in turn.
// ======================================================================
// simple function lookup to color = greyscale pattern
vec3 color0(vec2 rt)
{
    float v = abs(fn(rt));
    return vec3(v,v,v);
}
// convert to a binary black/white pattern
vec3 color1(vec2 rt)
{
    float v = abs(fn(rt));
    float c = smoothstep(0.19, 0.20, v);
    return vec3(c,c,c);
}
// grad+color2 are an estimated distance function for a pattern
// that is a little more controlled.
// see the webpage for explanation.
vec2 grad(vec2 p)
{
    vec2 h = vec2(0.01,0.0);
    return vec2(fn(p+h.xy) - fn(p-h.xy),
                fn(p+h.yx) - fn(p-h.yx)) / (2.0*h.x);
}
vec3 color2(vec2 x)
{
    float v = fn(x);
    vec2  g = grad(x);
    float distance_est = abs(v)/length(g);
    float c = smoothstep(0.015, 0.020, distance_est);
    return vec3(c,c,c);
}
// simple function lookup to color = greyscale pattern
float fract2(float x) {
    float v = 2*(fract(x)-0.5);
    return abs(v);
}
vec3 color3(vec2 rt)
{
    float v = fract2(abs(fn(rt)));
    return vec3(v,v,v);
}
vec3 color4(vec2 rt)
{
    float v = fract2(abs(fn(rt)));
    v = texture2D(iChannel0,vec2(v,0.5)).x;
    return vec3(v,v,v);
}
vec3 color5(vec2 rt)
{
    float u = fract2(abs(fn(rt)));
    float v = fract2(iTime/50);
    return texture2D(iChannel0,vec2(u,v)).xyz;
}
// ======================================================================
// In this main function, have fun playing about with
void main(void)
{
    // select one
    vec2 uv = 3*getScreenUV(gl_FragCoord.xy);
    //vec2 uv = 2.5*(0.1+fract2(iTime/5))*getScreenUV(gl_FragCoord.xy); // zoom!

    vec2 rt = getPolar(uv);

    // uncomment to spin...
    //rt.y += iTime/4.0;

    // Select one colorN routine at a time
    //vec3 c = vec3(uv.x,uv.y,0);  // see x, y
    //vec3 c = vec3(rt.x,rt.y,0);  // see r, theta
    vec3 c = color0(rt);
    //vec3 c = color1(rt);
    //vec3 c = color2(rt);
    //vec3 c = color3(rt);
    //vec3 c = color4(rt);
    //vec3 c = color5(rt);

    // select to provide some room for text while live-coding
    //c *= smoothstep(0.25,0.75,gl_FragCoord.x/iResolution.x);

    gl_FragColor = vec4(c, 1.0);
}
