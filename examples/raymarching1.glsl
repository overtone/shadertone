// Roger's Intro to Raymarching
// code adapted from
//   http://www.geeks3d.com/20130524/building-worlds-with-distance-functions-in-glsl-raymarching-glslhacker-tutorial-opengl
// with explicit names for easier understanding.

// Also see:
// http://www.iquilezles.org/www/material/nvscene2008/rwwtt.pdf starting at page 21.
// http://www.iquilezles.org/www/articles/distfunctions/distfunctions.htm

float PI = 3.14159265;

vec2 obj_union(in vec2 obj0, in vec2 obj1)
{
    if (obj0.x < obj1.x) {
        return obj0;
    } else {
        return obj1;
    }
}

// FIXME?  switch to transform p instead of "center"?
// returns vec2(distance,id)
vec2 obj_sphere(in vec3 p,
                in vec3 center, in float radius,
                in float id)
{
    float d = length(distance(p,center))-radius;
    return vec2(d,id);
}

// returns vec2(distance,id)
vec2 obj_floor(in vec3 p,
               in vec3 select, in vec3 center,
               in float id)
{
    // FIXME why doesn't length work?
    //return vec2(length(p*select-center),id);
    float d = dot(p,select)-dot(center,select);
    //d += 1.0*sin(length(vec2(p.x,p.z)));//length(vec2(p.z,p.z)));
    return vec2(d,id);
}

vec2 distance_to_obj(in vec3 p)
{
    vec2 dist_obj =
        obj_union(
            obj_floor(
                p,
                vec3(0,1,0),  // select
                vec3(0,-10,0),  // center
                0),           // obj id
            obj_sphere(
                p,
                vec3(0,0,0),  // center
                2.5,          // radius
                1             // obj id
                ));
    return dist_obj;
}

vec3 floor_color(in vec3 p)
{
    //return vec3(0.3,0,0);
    if (fract(p.x*0.2)>0.2) {
        if (fract(p.z*0.2)>0.2) {
            return vec3(0,0.1,0.2);
        } else {
            return vec3(1,1,1);
        }
    } else {
        if (fract(p.z*.2)>.2) {
            return vec3(1,1,1);
        } else {
            return vec3(0.3,0,0);
        }
    }
}

vec3 prim_color(in vec3 p)
{
    return vec3(0.0,0.3,0.3);
}

vec3 obj_normal(vec3 p, float distance)
{
    const vec3 epsilonx = vec3(0.02,0,0);
    return normalize(
        vec3(distance-distance_to_obj(p-epsilonx.xyy).x,
             distance-distance_to_obj(p-epsilonx.yxy).x,
             distance-distance_to_obj(p-epsilonx.yyx).x));
}

void main(void)
{
    vec2 window_pos        = -1.0 + 2.0*(gl_FragCoord.xy/iResolution.xy);
    vec3 camera_up         = vec3(0,1,0);
    vec3 camera_lookat     = vec3(0,0,0);
    vec3 camera_pos        = vec3(5.0*cos(0.1*iTime),
                                5.0,
                                5.0*sin(0.1*iTime));
    vec3 light_pos         = vec3(5.0,10.0,5.0);
    vec3 norm_camera_dir   = normalize(camera_lookat-camera_pos);
    vec3 u                 = normalize(cross(camera_up,norm_camera_dir));
    vec3 v                 = cross(norm_camera_dir,u);
    vec3 near_plane_center = (camera_pos+norm_camera_dir);
    vec3 near_plane_coord  = (near_plane_center +
                              window_pos.x*u*iResolution.x/iResolution.y +
                              window_pos.y*v);
    vec3 norm_eye_ray      = normalize(near_plane_coord-camera_pos);

    const float max_depth  = 100.0;
    vec2 dist_id           = vec2(0.02,0.0);
    float cur_depth        = 1.0;
    vec3 cur_color,p;

    // ray-march to find the depth and object intersected
    for(int i = 0; i < 256; i++) {
        if ((abs(dist_id.x) < .001) || (cur_depth > max_depth))
            break;
        cur_depth += dist_id.x;
        p = camera_pos + norm_eye_ray*cur_depth;
        dist_id = distance_to_obj(p);
    }

    // light the pixel with simple diffuse/specular
    gl_FragColor = vec4(0,0,0,1);
    if (cur_depth < max_depth) {
        if (dist_id.y == 0) {
            cur_color = floor_color(p);
        } else {
            cur_color = prim_color(p);
        }
        vec3 N = obj_normal(p, dist_id.x);
        vec3 L = normalize(light_pos-p);
        vec3 V = -norm_eye_ray;
        vec3 H = (V+L)/2.0;
        vec3 diffuse_color  = cur_color*max(0,dot(N,L));
        vec3 specular_color = vec3(1.0,1.0,1.0);
        specular_color     *= pow(max(0,dot(N,H)),20.0);
        gl_FragColor = vec4(diffuse_color+specular_color,1.0);
    }
}
