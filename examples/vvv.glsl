// Simple example to show how variables from a synth can communicate
// to the shader
uniform float iOvertoneVolume;
uniform float iA;
uniform float iB;
void main(void)
{
    vec2  uv = gl_FragCoord.xy/iResolution.xy;
    // put iA,iB into 0,1 range
    float a  = (iA - 300.0)/50.0/2.0 + 0.5;
    float b  = (iB - 300.0)/100.0/2.0 + 0.5;
    // make a vertical step for iA
    float ac = smoothstep(0.0,0.05,uv.x - a);
    // make a horizontal step for iB
    float bc = smoothstep(0.0,0.05,uv.y - b);
    vec3  c  = vec3(0.0,0.5,1.0)*vec3(ac);
    c       += vec3(1.0,0.5,0.0)*vec3(bc);
    gl_FragColor = vec4(c, 1.0);
}
