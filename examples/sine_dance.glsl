// Doodle based on Sound Visualizer https://www.shadertoy.com/view/Xds3Rr
// and http://vimeo.com/51993089 @ the 0min 44s mark
// For Shadertone, tap into Overtone's volume...
uniform float iOvertoneVolume;
void main(void)
{
    vec2 uv = 2.0*(gl_FragCoord.xy/iResolution.xy) - 1.0;
    // equvalent to the video's spec.y, I think
    float spec_y = 0.01 + 5.0*iOvertoneVolume;
    float col = 0.0;
    uv.x += sin(iGlobalTime * 6.0 + uv.y*1.5)*spec_y;
    col += abs(0.066/uv.x) * spec_y;
    gl_FragColor = vec4(col,col,col,1.0);
}
