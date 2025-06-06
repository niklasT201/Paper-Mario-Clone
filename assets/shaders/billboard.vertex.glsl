// billboard.vertex.glsl
#ifdef GL_ES
#define LOWP lowp
#define MED mediump
#define HIGH highp
precision mediump float;
#else
#define MED
#define LOWP
#define HIGH
#endif

attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec2 a_texCoord0;

uniform mat4 u_worldTrans;
uniform mat4 u_projViewTrans;
uniform vec3 u_cameraPosition;

varying vec2 v_texCoords;
varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec3 v_viewDir;

void main() {
    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);
    v_worldPos = worldPos.xyz;

    // Transform normal to world space
    v_normal = normalize(mat3(u_worldTrans) * a_normal);

    // Calculate view direction
    v_viewDir = normalize(u_cameraPosition - v_worldPos);

    v_texCoords = a_texCoord0;

    gl_Position = u_projViewTrans * worldPos;
}
