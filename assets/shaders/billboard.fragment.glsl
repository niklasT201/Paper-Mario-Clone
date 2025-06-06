// billboard.fragment.glsl
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

#define MAX_POINT_LIGHTS 8
#define MAX_DIRECTIONAL_LIGHTS 2

varying vec2 v_texCoords;
varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec3 v_viewDir;

uniform sampler2D u_diffuseTexture;

// Lighting uniforms
uniform vec3 u_ambientLight;
uniform vec3 u_dirLights[MAX_DIRECTIONAL_LIGHTS];
uniform vec3 u_dirLightColors[MAX_DIRECTIONAL_LIGHTS];
uniform vec3 u_pointLightPositions[MAX_POINT_LIGHTS];
uniform vec3 u_pointLightColors[MAX_POINT_LIGHTS];
uniform float u_pointLightIntensities[MAX_POINT_LIGHTS];
uniform int u_numPointLights;
uniform int u_numDirLights;

// Billboard-specific uniforms
uniform float u_billboardLightingStrength; // How much to apply billboard lighting (0.0 to 1.0)
uniform float u_minLightLevel; // Minimum light level to prevent completely dark sprites

vec3 calculatePointLight(vec3 lightPos, vec3 lightColor, float intensity, vec3 fragPos, vec3 normal, vec3 viewDir) {
    vec3 lightDir = lightPos - fragPos;
    float distance = length(lightDir);
    lightDir = normalize(lightDir);

    // Attenuation
    float attenuation = intensity / (1.0 + 0.09 * distance + 0.032 * distance * distance);

    // For billboards, we want softer lighting that doesn't depend heavily on normals
    // Use a combination of normal-based lighting and distance-based lighting
    float normalDot = dot(normal, lightDir);

    // Standard diffuse calculation
    float diffuseStandard = max(normalDot, 0.0);

    // Billboard lighting: always receive some light if close to light source
    float billboardDiffuse = max(abs(normalDot), 0.3); // Use absolute value for both sides

    // Blend between standard and billboard lighting
    float diffuse = mix(diffuseStandard, billboardDiffuse, u_billboardLightingStrength);

    // Apply minimum light level
    diffuse = max(diffuse, u_minLightLevel);

    return lightColor * diffuse * attenuation;
}

vec3 calculateDirectionalLight(vec3 lightDir, vec3 lightColor, vec3 normal) {
    lightDir = normalize(-lightDir);

    // Similar billboard lighting approach for directional lights
    float normalDot = dot(normal, lightDir);
    float diffuseStandard = max(normalDot, 0.0);
    float billboardDiffuse = max(abs(normalDot), 0.3);

    float diffuse = mix(diffuseStandard, billboardDiffuse, u_billboardLightingStrength);
    diffuse = max(diffuse, u_minLightLevel);

    return lightColor * diffuse;
}

void main() {
    vec4 texColor = texture2D(u_diffuseTexture, v_texCoords);

    // Discard transparent pixels
    if (texColor.a < 0.1) {
        discard;
    }

    // Start with ambient light
    vec3 finalColor = u_ambientLight;

    // Add directional lights
    for (int i = 0; i < MAX_DIRECTIONAL_LIGHTS; i++) {
        if (i >= u_numDirLights) break;
        finalColor += calculateDirectionalLight(u_dirLights[i], u_dirLightColors[i], v_normal);
    }

    // Add point lights
    for (int i = 0; i < MAX_POINT_LIGHTS; i++) {
        if (i >= u_numPointLights) break;
        finalColor += calculatePointLight(
            u_pointLightPositions[i],
            u_pointLightColors[i],
            u_pointLightIntensities[i],
            v_worldPos,
            v_normal,
            v_viewDir
        );
    }

    // Apply lighting to texture
    gl_FragColor = vec4(texColor.rgb * finalColor, texColor.a);
}
