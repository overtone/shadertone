uniform float v1, v2, v3;
//uniform float iTime;
uniform float iOvertoneVolume;

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

float smoothbump(float center, float width, float x) {
  float w2 = width/2.0;
  float cp = center+w2;
  float cm = center-w2;
  float c = smoothstep(cm, center, x) * (1.0-smoothstep(center, cp, x));
  return c;
}

float get_dot(float x, float y, float v) {
    float y1 = smoothbump(0.3,0.6,y);
    float x1 = smoothbump(0.0,0.6,x+v/4.0);
    return y1*x1*x1*(4*iOvertoneVolume);
}

void main(void) {
    vec2 uv = getScreenUV(gl_FragCoord.xy);
    vec3 c1 = get_dot(uv.x, uv.y, v1)*vec3(0.6, 0.1, 0.5);
    vec3 c2 = get_dot(uv.x, uv.y, v2)*vec3(0.5, 0.6, 0.1);
    vec3 c3 = get_dot(uv.x, uv.y, v3)*vec3(0.1, 0.5, 0.6);

    vec2 uv2 = (uv+1.0)/2.0;
    uv2.y += 0.07 + 0.05*sin(iTime);
    uv2.x += 0.008*sin(19.0*sin(uv2.y)*mod(iTime,8.0));
    //uv2.x += 0.004*noise1(iTime*3.0);
    vec3 pc = 0.91*texture2D(iChannel0,uv2).rgb;

    vec3 c = c1 + c2 + c3 + pc;
    gl_FragColor = vec4(c,1.0);
}
