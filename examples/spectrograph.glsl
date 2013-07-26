// Spectrograph.glsl - display the FFT over time.
float SEC_PER_SCREEN = 30.0; // cursor crosses the screen every 30
                             // seconds
float AMP_SCALE      = (1.0/2.0);  // fft data should be in the 0-1 range,
                             // but everyone's sound level is slightly
                             // different.  Scale the fft results for
                             // display.
float FREQ_SCALE     = (4096.0/4096.0);  // By default, 0-20,000Hz is displayed,
                             // but often we care little about the
                             // highest frequencies.  e.g. ccale max
                             // freq by 1/2 to display 0-10,000.

// convert hue saturation & value to vec3(red, green, blue)
vec3 hsv2rgb(float h, float s, float v) {
  return mix(vec3(1.),clamp((abs(fract(h+vec3(3.,2.,1.)/3.)*6.-3.)-1.),0.,1.),s)*v;
}

// this routine is called once for every pixel in our window.
void main(void)
{
    // setup uv for indexing into a texture where we need
    // normalized [0.0,1.0) coordinate values.
    //
    // gl_FragCoord.xy communicates the current pixel location to the
    // shader.  gl_FragCoord.x will range from [0,1024), .y ranges
    // [0,512) iResolution.xy is a constant set to the window size = {
    // 1024, 512 }
    vec2  uv     = gl_FragCoord.xy/iResolution.xy;

    // the iChannel0 texture is :overtone-audio and that data is a 512x2 array
    // of sound data
    // row 1 is the fft data and row 2 is the current sound wave data
    // the texture2D call uses the 2nd argument to index into that data.
    //   the first arg is the current pixel's normalized Y location
    //   a value of 0.25 for the 2nd arg selects only FFT data
    // change the last line to
    //   gl_FragColor = vec4(vec3(fft),1.0);
    // and see the full window filled with the same sonogram data.
    float fi     = FREQ_SCALE*uv.y;
    // add 2x super-sampling to help with narrow frequencies
    float fid    = FREQ_SCALE/4096.0/2.0; // 1/2 texel
    float fft    = (AMP_SCALE * 0.5 *
                    (max(0.0, texture2D(iChannel0,vec2(fi,0.25)).x) +
                     max(0.0, texture2D(iChannel0,vec2(fi+fid,0.25)).x)));

    // let's have some fun with that fft value...change scalar into a
    // hue with red as the peak.  Also adjust the value so quiet
    // frequencies are black.
    vec3 fft3    = hsv2rgb(1.0-fft,0.5,fft);

    // But, we don't want the full screen to show the current FFT.  We
    // want to only update the data under the cursor.  So, we use the
    // iGlobalTime input to find a particular X value for a column we
    // want to update.  The following creates a value that ranges from
    // [0,1024) over 30 seconds.
    int   cur_x  = int(fract(iGlobalTime/SEC_PER_SCREEN)*iResolution.x);

    // For the data that is NOT under the cursor, we want the older
    // sonogram data that we rendered into the framebuffer.  get that
    // from the iChannel1 :previous-frame texture.  The uv coordinate
    // provide a 1:1 mapping from old to new.
    vec3  oc     = texture2D(iChannel1,uv).rgb;

    // At last, we can derive our current pixel's color.
    // the ternary logic here just selects:
    // if this pixel's x value is the cursor's x value
    //   the color is the current fft value
    // else if this pixel is one to the right of the cursor
    //   draw a yellow pixel (makes a vertical line)
    // else
    //   use the old color from the previous frame
    // Note that cur_x and int(gl_FragCoord.x) are integers ranging [0,1024)
    vec3  c      = ((cur_x == int(gl_FragCoord.x)) ?
                    fft3 :
                    (((cur_x+1) == int(gl_FragCoord.x)) ?
                     vec3(0.8,0.8,0.0) :
                     oc));

    // finally, tell OpenGL what the color of this pixel is.
    gl_FragColor = vec4(c,1.0);
}
