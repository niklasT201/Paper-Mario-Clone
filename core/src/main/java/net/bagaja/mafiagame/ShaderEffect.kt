package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.math.Vector2

enum class ShaderEffect(val displayName: String) {
    NONE("Default"),
    BRIGHT_VIBRANT("Bright & Vibrant"),
    RETRO_PIXEL("Retro Pixel"),
    DREAMY_SOFT("Dreamy Soft"),
    NEON_GLOW("Neon Glow"),
    OLD_MOVIE("1920s Cartoon"),
    BLACK_WHITE("Schwarz-Weiß"),
    FILM_NOIR("Film Noir"),
    SIN_CITY("Sin City"),
    GRINDHOUSE("Grindhouse Film"),
    CYBERPUNK("Cyberpunk 2077"),
    VINTAGE_SEPIA("Vintage Sepia"),
    THERMAL_VISION("Thermal Vision"),
    GLITCH_MATRIX("Digital Glitch"),
    COMIC_BOOK("Comic Book"),
    UNDERWATER("Underwater"),
    NIGHT_VISION("Night Vision"),
}

class ShaderEffectManager {
    private var currentEffect = ShaderEffect.NONE
    private lateinit var frameBuffer: FrameBuffer
    private lateinit var postProcessBatch: SpriteBatch
    private lateinit var postProcessCamera: OrthographicCamera
    private lateinit var fullScreenQuad: Mesh

    // Shaders for different effects
    private val shaders = mutableMapOf<ShaderEffect, ShaderProgram>()
    var isEffectsEnabled = true
        private set

    // Shader source code
    private val vertexShader = """
        attribute vec4 a_position;
        attribute vec2 a_texCoord0;
        varying vec2 v_texCoord;

        void main() {
            v_texCoord = a_texCoord0;
            gl_Position = a_position;
        }
    """

    // Fragment shaders for different effects
    private val brightVibrantShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform float u_time;
        varying vec2 v_texCoord;

        void main() {
            vec4 color = texture2D(u_texture, v_texCoord);

            // Increase brightness and saturation
            color.rgb = pow(color.rgb, vec3(0.8)); // Gamma correction for brightness

            // Boost saturation
            float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            color.rgb = mix(vec3(luminance), color.rgb, 1.5);

            // Add slight warm tint
            color.r *= 1.1;
            color.g *= 1.05;

            // Subtle brightness oscillation
            color.rgb *= 1.0 + 0.1 * sin(u_time * 2.0) * 0.5;

            gl_FragColor = vec4(color.rgb, color.a);
        }
    """

    private val retroPixelShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform vec2 u_resolution;
        uniform float u_time;
        varying vec2 v_texCoord;

        void main() {
            // Pixelation effect
            float pixelSize = 4.0;
            vec2 pixelCoord = floor(v_texCoord * u_resolution / pixelSize) * pixelSize / u_resolution;
            vec4 color = texture2D(u_texture, pixelCoord);

            // Retro color palette reduction
            color.r = floor(color.r * 8.0) / 8.0;
            color.g = floor(color.g * 8.0) / 8.0;
            color.b = floor(color.b * 8.0) / 8.0;

            // Add scanlines
            float scanline = sin(v_texCoord.y * u_resolution.y * 2.0) * 0.1;
            color.rgb -= scanline;

            // Slight CRT curve effect (simplified)
            vec2 center = v_texCoord - 0.5;
            float vignette = 1.0 - dot(center, center) * 0.5;
            color.rgb *= vignette;

            gl_FragColor = color;
        }
    """

    private val dreamySoftShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform vec2 u_resolution;
        uniform float u_time;
        varying vec2 v_texCoord;

        void main() {
            vec4 color = vec4(0.0);
            vec2 offset = 1.0 / u_resolution;

            // Gaussian blur approximation
            for(int x = -2; x <= 2; x++) {
                for(int y = -2; y <= 2; y++) {
                    vec2 sampleCoord = v_texCoord + vec2(float(x), float(y)) * offset * 1.5;
                    float weight = 1.0 / (1.0 + float(x*x + y*y));
                    color += texture2D(u_texture, sampleCoord) * weight;
                }
            }
            color /= 9.0;

            // Soft, pastel color adjustment
            color.rgb = pow(color.rgb, vec3(1.2)); // Softer contrast

            // Add dreamy color tint
            color.r *= 1.1;
            color.b *= 1.2;
            color.g *= 1.05;

            // Gentle brightness pulsing
            float pulse = 0.95 + 0.05 * sin(u_time * 1.5);
            color.rgb *= pulse;

            // Soft vignette
            vec2 center = v_texCoord - 0.5;
            float vignette = 1.0 - dot(center, center) * 0.3;
            color.rgb *= vignette;

            gl_FragColor = color;
        }
    """

    private val neonGlowShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform vec2 u_resolution;
        uniform float u_time;
        varying vec2 v_texCoord;

        void main() {
            vec4 originalColor = texture2D(u_texture, v_texCoord);
            vec4 glowColor = vec4(0.0);
            vec2 offset = 1.0 / u_resolution;

            // Create glow effect by sampling around the pixel
            for(int x = -3; x <= 3; x++) {
                for(int y = -3; y <= 3; y++) {
                    vec2 sampleCoord = v_texCoord + vec2(float(x), float(y)) * offset * 2.0;
                    vec4 sampleColor = texture2D(u_texture, sampleCoord);
                    float distance = length(vec2(float(x), float(y)));
                    float weight = 1.0 / (1.0 + distance * 0.5);
                    glowColor += sampleColor * weight;
                }
            }
            glowColor /= 25.0;

            // Edge detection for enhanced neon effect
            vec4 edgeColor = vec4(0.0);
            edgeColor += texture2D(u_texture, v_texCoord + vec2(-offset.x, 0.0));
            edgeColor += texture2D(u_texture, v_texCoord + vec2(offset.x, 0.0));
            edgeColor += texture2D(u_texture, v_texCoord + vec2(0.0, -offset.y));
            edgeColor += texture2D(u_texture, v_texCoord + vec2(0.0, offset.y));
            edgeColor = abs(edgeColor - originalColor * 4.0);

            // Neon color enhancement
            vec4 neonColor = originalColor;
            neonColor.r *= 1.3 + 0.3 * sin(u_time * 3.0);
            neonColor.g *= 1.2 + 0.2 * sin(u_time * 3.5 + 1.0);
            neonColor.b *= 1.4 + 0.4 * sin(u_time * 2.5 + 2.0);

            // Combine effects
            vec4 finalColor = neonColor + glowColor * 0.5 + edgeColor * 2.0;

            // Dark background enhancement for neon effect
            float luminance = dot(originalColor.rgb, vec3(0.299, 0.587, 0.114));
            if(luminance < 0.3) {
                finalColor.rgb *= 0.3; // Darken dark areas
            }

            gl_FragColor = vec4(finalColor.rgb, originalColor.a);
        }
    """

    private val passthroughShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        varying vec2 v_texCoord;

        void main() {
            gl_FragColor = texture2D(u_texture, v_texCoord);
        }
    """

    private val blackWhiteShader = """
    #ifdef GL_ES
    precision mediump float;
    #endif

    uniform sampler2D u_texture;
    uniform float u_time;
    varying vec2 v_texCoord;

    void main() {
        vec4 color = texture2D(u_texture, v_texCoord);

        // Convert to grayscale using luminance formula
        float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));

        // Apply high contrast for that classic cartoon look
        gray = smoothstep(0.4, 0.6, gray);

        // Add slight film grain effect
        float noise = fract(sin(dot(v_texCoord, vec2(12.9898, 78.233))) * 43758.5453);
        gray += (noise - 0.5) * 0.1;

        // Optional: Add subtle vignette for old film look
        vec2 center = v_texCoord - 0.5;
        float vignette = 1.0 - dot(center, center) * 0.8;
        gray *= vignette;

        // Clamp to ensure we stay in valid range
        gray = clamp(gray, 0.0, 1.0);

        gl_FragColor = vec4(vec3(gray), color.a);
    }
"""

    private val oldmovieShader = """
    #ifdef GL_ES
    precision mediump float;
    #endif

    uniform sampler2D u_texture;
    varying vec2 v_texCoord;

    void main() {
        vec4 color = texture2D(u_texture, v_texCoord);

        // Simple grayscale conversion using luminance formula
        float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));

        gl_FragColor = vec4(vec3(gray), color.a);
    }
"""

    private val filmNoirShader = """
    #ifdef GL_ES
    precision mediump float;
    #endif

    uniform sampler2D u_texture;
    uniform float u_time;
    uniform vec2 u_resolution;
    varying vec2 v_texCoord;

    void main() {
        vec4 color = texture2D(u_texture, v_texCoord);

        // Calculate the base luminance (how bright the pixel is in grayscale)
        float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));

        // Detect colored lighting by finding the difference between the
        // original color and its grayscale equivalent.
        // A colorful pixel will have a large deviation. A gray pixel will have zero.
        vec3 colorDeviation = color.rgb - vec3(luminance);

        // Amplify the colored lighting effect to make it pop.
        // You can change '3.0' to make the color more or less intense.
        vec3 coloredLighting = colorDeviation * 3.0;

        // Create a high-contrast grayscale base using smoothstep.
        // This crushes the blacks and blows out the whites for a classic noir look.
        float contrastGray = smoothstep(0.25, 0.75, luminance);

        // Combine the high-contrast grayscale base with the colored lighting.
        vec3 finalColor = vec3(contrastGray) + coloredLighting;

        // --- Optional but highly recommended "Old Film" effects ---

        // Add film grain that jitters over time
        float grain = fract(sin(dot(v_texCoord + u_time * 0.001, vec2(12.9898, 78.233))) * 43758.5453);
        finalColor += (grain - 0.5) * 0.15; // Adjust 0.15 to change grain intensity

        // Add faint scanlines
        float scanline = sin(v_texCoord.y * u_resolution.y * 1.5) * 0.04;
        finalColor -= scanline;

        // Add a strong vignette to darken the corners
        vec2 center = v_texCoord - 0.5;
        float vignette = 1.0 - dot(center, center) * 1.2;
        vignette = smoothstep(0.0, 1.0, vignette);
        finalColor *= vignette;

        // Clamp the final result to keep it in the valid 0.0-1.0 range
        finalColor = clamp(finalColor, 0.0, 1.0);

        gl_FragColor = vec4(finalColor, color.a);
    }
"""

    private val sinCityShader = """
    #ifdef GL_ES
    precision mediump float;
    #endif

    uniform sampler2D u_texture;
    uniform float u_time;
    varying vec2 v_texCoord;

    void main() {
        vec4 originalColor = texture2D(u_texture, v_texCoord);

        // --- Step 1: Calculate luminance for the grayscale base ---
        float luminance = dot(originalColor.rgb, vec3(0.299, 0.587, 0.114));
        vec3 grayColor = vec3(luminance);

        // --- Step 2: Determine how colorful the pixel is (saturation) ---
        // A higher value means more color and less gray.
        float saturation = length(originalColor.rgb - grayColor);

        // --- Step 3: Define a threshold for what counts as "colorful" ---
        // This is the key value to tweak! Lower values let more color through.
        // A good range to experiment with is 0.2 to 0.4.
        float colorThreshold = 0.2;

        // --- Step 4: Create a blend factor based ONLY on saturation ---
        // We use smoothstep for a nice transition from gray to color. This is the main fix.
        float colorRestoreFactor = smoothstep(colorThreshold, colorThreshold + 0.25, saturation);

        // --- Step 5 (Recommended): Boost the restored color to make it "pop" ---
        // Instead of just restoring the original color, we amplify it slightly.
        vec3 boostedColor = originalColor.rgb * 1.4; // Boost brightness by 40%

        // --- Step 6: Blend between the grayscale base and the boosted color ---
        vec3 finalColor = mix(grayColor, boostedColor, colorRestoreFactor);

        // --- Step 7: Add classic film effects ---
        // Film Grain
        float grain = fract(sin(dot(v_texCoord + u_time * 0.01, vec2(12.9898, 78.233))) * 43758.5453);
        finalColor += (grain - 0.5) * 0.1;

        // Vignette
        vec2 center = v_texCoord - 0.5;
        float vignette = 1.0 - dot(center, center) * 0.8;
        finalColor *= vignette;

        gl_FragColor = vec4(clamp(finalColor, 0.0, 1.0), originalColor.a);
    }
"""

    private val grindhouseShader = """
    #ifdef GL_ES
    precision mediump float;
    #endif

    uniform sampler2D u_texture;
    uniform float u_time;
    uniform vec2 u_resolution;
    varying vec2 v_texCoord;

    // Pseudo-random function for noise generation
    float rand(vec2 co) {
        return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
    }

    // Film scratches function
    float filmScratches(vec2 uv, float time) {
        float scratch = 0.0;

        // Vertical scratches
        for(int i = 0; i < 3; i++) {
            float scratchX = 0.1 + 0.8 * rand(vec2(float(i), floor(time * 2.0)));
            float scratchWidth = 0.001 + 0.003 * rand(vec2(float(i) + 1.0, floor(time * 2.0)));

            if(abs(uv.x - scratchX) < scratchWidth) {
                scratch += 0.5 * (1.0 - abs(uv.x - scratchX) / scratchWidth);
            }
        }

        return scratch;
    }

    void main() {
        vec2 uv = v_texCoord;

        // Film gate instability - slight jitter
        vec2 jitter = vec2(
            (rand(vec2(u_time * 0.1)) - 0.5) * 0.002,
            (rand(vec2(u_time * 0.1 + 1.0)) - 0.5) * 0.001
        );
        uv += jitter;

        vec4 color = texture2D(u_texture, uv);

        // Desaturate heavily but keep some color
        float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        color.rgb = mix(color.rgb, vec3(luminance), 0.7);

        // Grindhouse color grading - crush blacks, lift mids, desaturate
        color.rgb = pow(color.rgb, vec3(1.3)); // Increase contrast
        color.rgb = color.rgb * 0.9 + 0.1; // Lift blacks slightly

        // Add sepia/brown tint typical of old film
        color.r *= 1.2;
        color.g *= 1.1;
        color.b *= 0.8;

        // Heavy film grain
        float grain = rand(uv + u_time * 0.01) * 0.25;
        color.rgb += (grain - 0.125);

        // Film scratches
        float scratches = filmScratches(uv, u_time);
        color.rgb += scratches;

        // Dust and dirt spots
        float dirt = 0.0;
        for(int i = 0; i < 5; i++) {
            vec2 dirtPos = vec2(
                rand(vec2(float(i), floor(u_time * 0.5))),
                rand(vec2(float(i) + 10.0, floor(u_time * 0.5)))
            );
            float dirtDist = distance(uv, dirtPos);
            if(dirtDist < 0.02) {
                dirt += (0.02 - dirtDist) * 2.0;
            }
        }
        color.rgb -= dirt * 0.3;

        // Film burn/exposure variations
        float exposure = 0.9 + 0.2 * sin(u_time * 0.3 + uv.y * 10.0);
        color.rgb *= exposure;

        // Vignette - heavy corners
        vec2 center = uv - 0.5;
        float vignette = 1.0 - dot(center, center) * 1.5;
        vignette = smoothstep(0.2, 0.8, vignette);
        color.rgb *= vignette;

        // Film gate - slight rounded corners
        float gate = 1.0;
        if(uv.x < 0.02 || uv.x > 0.98 || uv.y < 0.02 || uv.y > 0.98) {
            gate *= 0.3;
        }
        color.rgb *= gate;

        gl_FragColor = vec4(clamp(color.rgb, 0.0, 1.0), color.a);
    }
"""

    private val cyberpunkShader = """
    #ifdef GL_ES
    precision mediump float;
    #endif

    uniform sampler2D u_texture;
    uniform float u_time;
    uniform vec2 u_resolution;
    varying vec2 v_texCoord;

    void main() {
        vec2 uv = v_texCoord;

        // Chromatic aberration
        float aberration = 0.003;
        vec4 color;
        color.r = texture2D(u_texture, uv + vec2(aberration, 0.0)).r;
        color.g = texture2D(u_texture, uv).g;
        color.b = texture2D(u_texture, uv - vec2(aberration, 0.0)).b;
        color.a = texture2D(u_texture, uv).a;

        // Digital scan lines
        float scanline = sin(uv.y * u_resolution.y * 0.7) * 0.04;
        color.rgb -= scanline;

        // Cyberpunk color grading - enhance blues and magentas
        color.r *= 1.1;
        color.g *= 0.9;
        color.b *= 1.4;

        // Add neon glow to bright areas
        float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        if(luminance > 0.6) {
            color.rgb += vec3(0.2, 0.1, 0.4) * (luminance - 0.6) * 2.0;
        }

        // Glitch effect
        float glitch = step(0.98, sin(u_time * 15.0 + uv.y * 20.0));
        if(glitch > 0.0) {
            color.rgb = mix(color.rgb, vec3(1.0, 0.0, 1.0), 0.3);
            uv.x += (fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) * 0.1;
        }

        // Digital noise
        float noise = fract(sin(dot(uv + u_time * 0.001, vec2(12.9898, 78.233))) * 43758.5453);
        color.rgb += (noise - 0.5) * 0.08;

        gl_FragColor = color;
    }
"""

    private val vintageSepia = """
    #ifdef GL_ES
    precision mediump float;
    #endif

    uniform sampler2D u_texture;
    uniform float u_time;
    varying vec2 v_texCoord;

    void main() {
        vec4 color = texture2D(u_texture, v_texCoord);

        // Convert to luminance
        float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));

        // Sepia tone mapping
        vec3 sepia = vec3(
            luminance * 1.2,
            luminance * 1.0,
            luminance * 0.8
        );

        // Add warmth
        sepia.r *= 1.1;
        sepia.g *= 0.95;
        sepia.b *= 0.82;

        // Vintage fade effect
        sepia = pow(sepia, vec3(1.2));
        sepia *= 0.9;

        // Add subtle paper texture
        float paper = fract(sin(dot(v_texCoord * 50.0, vec2(12.9898, 78.233))) * 43758.5453);
        sepia += (paper - 0.5) * 0.05;

        // Vintage vignette
        vec2 center = v_texCoord - 0.5;
        float vignette = 1.0 - dot(center, center) * 0.8;
        sepia *= smoothstep(0.0, 1.0, vignette);

        gl_FragColor = vec4(sepia, color.a);
    }
"""

    private val thermalVisionShader = """
    #ifdef GL_ES
    precision mediump float;
    #endif

    uniform sampler2D u_texture;
    uniform float u_time;
    varying vec2 v_texCoord;

    void main() {
        vec4 color = texture2D(u_texture, v_texCoord);

        // Calculate temperature based on luminance
        float temp = dot(color.rgb, vec3(0.299, 0.587, 0.114));

        // Thermal color mapping
        vec3 thermal;
        if(temp < 0.25) {
            thermal = mix(vec3(0.0, 0.0, 0.3), vec3(0.0, 0.0, 1.0), temp * 4.0);
        } else if(temp < 0.5) {
            thermal = mix(vec3(0.0, 0.0, 1.0), vec3(0.0, 1.0, 0.0), (temp - 0.25) * 4.0);
        } else if(temp < 0.75) {
            thermal = mix(vec3(0.0, 1.0, 0.0), vec3(1.0, 1.0, 0.0), (temp - 0.5) * 4.0);
        } else {
            thermal = mix(vec3(1.0, 1.0, 0.0), vec3(1.0, 0.0, 0.0), (temp - 0.75) * 4.0);
        }

        // Add heat shimmer effect
        float shimmer = sin(v_texCoord.y * 10.0 + u_time * 5.0) * 0.01;
        vec2 shimmeredUV = v_texCoord + vec2(shimmer, 0.0);

        // Add scan lines
        float scanline = sin(v_texCoord.y * 200.0) * 0.1;
        thermal += scanline;

        gl_FragColor = vec4(thermal, color.a);
    }
"""

    private val glitchMatrixShader = """
    #ifdef GL_ES
    precision mediump float;
    #endif

    uniform sampler2D u_texture;
    uniform float u_time;
    uniform vec2 u_resolution;
    varying vec2 v_texCoord;

    float random(vec2 st) {
        return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
    }

    void main() {
        vec2 uv = v_texCoord;

        // Time-based glitch trigger
        float glitchAmount = sin(u_time * 2.0) * 0.5 + 0.5;

        // Horizontal glitch bars
        float barHeight = 0.05;
        float barPos = mod(u_time * 0.5, 1.0);

        if(abs(uv.y - barPos) < barHeight && random(vec2(floor(u_time * 10.0))) > 0.7) {
            uv.x += (random(vec2(floor(u_time * 20.0))) - 0.5) * 0.1;
        }

        vec4 color = texture2D(u_texture, uv);

        // Digital corruption
        if(random(uv + u_time * 0.1) > 0.95) {
            color.rgb = vec3(0.0, 1.0, 0.0); // Matrix green
        }

        // RGB shift
        if(random(vec2(u_time * 0.1)) > 0.8) {
            color.r = texture2D(u_texture, uv + vec2(0.01, 0.0)).r;
            color.b = texture2D(u_texture, uv - vec2(0.01, 0.0)).b;
        }

        // Block corruption
        vec2 blockUV = floor(uv * 20.0) / 20.0;
        if(random(blockUV + floor(u_time * 5.0)) > 0.9) {
            color.rgb = mix(color.rgb, vec3(random(blockUV), random(blockUV + 1.0), random(blockUV + 2.0)), 0.8);
        }

        // Matrix rain effect
        float rain = step(0.95, random(vec2(uv.x * 10.0, uv.y * 10.0 + u_time * 2.0)));
        color.rgb += rain * vec3(0.0, 0.5, 0.0);

        gl_FragColor = color;
    }
"""

    private val comicBookShader = """
    #ifdef GL_ES
    precision mediump float;
    #endif

    uniform sampler2D u_texture;
    uniform vec2 u_resolution;
    varying vec2 v_texCoord;

    void main() {
        vec2 offset = 1.0 / u_resolution;
        vec4 color = texture2D(u_texture, v_texCoord);

        // Edge detection using Sobel operator
        vec4 edgeX =
            texture2D(u_texture, v_texCoord + vec2(-offset.x, -offset.y)) * -1.0 +
            texture2D(u_texture, v_texCoord + vec2(-offset.x, 0.0)) * -2.0 +
            texture2D(u_texture, v_texCoord + vec2(-offset.x, offset.y)) * -1.0 +
            texture2D(u_texture, v_texCoord + vec2(offset.x, -offset.y)) * 1.0 +
            texture2D(u_texture, v_texCoord + vec2(offset.x, 0.0)) * 2.0 +
            texture2D(u_texture, v_texCoord + vec2(offset.x, offset.y)) * 1.0;

        vec4 edgeY =
            texture2D(u_texture, v_texCoord + vec2(-offset.x, -offset.y)) * -1.0 +
            texture2D(u_texture, v_texCoord + vec2(0.0, -offset.y)) * -2.0 +
            texture2D(u_texture, v_texCoord + vec2(offset.x, -offset.y)) * -1.0 +
            texture2D(u_texture, v_texCoord + vec2(-offset.x, offset.y)) * 1.0 +
            texture2D(u_texture, v_texCoord + vec2(0.0, offset.y)) * 2.0 +
            texture2D(u_texture, v_texCoord + vec2(offset.x, offset.y)) * 1.0;

        float edge = length(edgeX.rgb) + length(edgeY.rgb);
        edge = smoothstep(0.1, 0.3, edge);

        // Posterize colors
        color.r = floor(color.r * 6.0) / 6.0;
        color.g = floor(color.g * 6.0) / 6.0;
        color.b = floor(color.b * 6.0) / 6.0;

        // Increase saturation
        float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        color.rgb = mix(vec3(luminance), color.rgb, 1.5);

        // Add black edges
        color.rgb = mix(color.rgb, vec3(0.0), edge);

        gl_FragColor = color;
    }
"""

    private val underwaterShader = """
    #ifdef GL_ES
    precision mediump float;
    #endif

    uniform sampler2D u_texture;
    uniform float u_time;
    uniform vec2 u_resolution;
    varying vec2 v_texCoord;

    void main() {
        vec2 uv = v_texCoord;

        // Water distortion
        float wave1 = sin(uv.x * 10.0 + u_time * 2.0) * 0.01;
        float wave2 = sin(uv.y * 8.0 + u_time * 1.5) * 0.01;
        uv += vec2(wave1, wave2);

        vec4 color = texture2D(u_texture, uv);

        // Underwater color grading - blue tint and reduced visibility
        color.r *= 0.6;
        color.g *= 0.8;
        color.b *= 1.2;

        // Depth-based darkening
        float depth = 1.0 - v_texCoord.y;
        color.rgb *= mix(0.3, 1.0, depth);

        // Caustics effect
        float caustic1 = sin(uv.x * 20.0 + u_time * 3.0) * sin(uv.y * 15.0 + u_time * 2.0);
        float caustic2 = sin(uv.x * 15.0 - u_time * 2.5) * sin(uv.y * 20.0 - u_time * 1.8);
        float caustics = (caustic1 + caustic2) * 0.1;
        color.rgb += caustics * vec3(0.2, 0.5, 0.8);

        // Floating particles
        float particle = step(0.98, sin(uv.x * 100.0 + u_time) * sin(uv.y * 80.0 + u_time * 0.8));
        color.rgb += particle * vec3(0.8, 0.9, 1.0) * 0.3;

        gl_FragColor = color;
    }
"""

    private val nightVisionShader = """
    #ifdef GL_ES
    precision mediump float;
    #endif

    uniform sampler2D u_texture;
    uniform float u_time;
    uniform vec2 u_resolution;
    varying vec2 v_texCoord;

    void main() {
        vec4 color = texture2D(u_texture, v_texCoord);

        // Convert to luminance and amplify
        float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        luminance = pow(luminance, 0.4); // Brighten dark areas

        // Night vision green tint
        vec3 nightVision = vec3(luminance * 0.2, luminance * 1.0, luminance * 0.3);

        // Add scan lines
        float scanline = sin(v_texCoord.y * u_resolution.y * 0.5) * 0.1;
        nightVision -= scanline;

        // Add noise
        float noise = fract(sin(dot(v_texCoord + u_time * 0.001, vec2(12.9898, 78.233))) * 43758.5453);
        nightVision += (noise - 0.5) * 0.2;

        // Circular vignette (night vision goggles effect)
        vec2 center = v_texCoord - 0.5;
        float dist = length(center);
        float vignette = 1.0 - smoothstep(0.3, 0.5, dist);
        nightVision *= vignette;

        // Add green glow around bright areas
        if(luminance > 0.7) {
            nightVision += vec3(0.0, 0.3, 0.0) * (luminance - 0.7);
        }

        gl_FragColor = vec4(nightVision, color.a);
    }
"""

    fun initialize() {
        // Create frame buffer for post-processing
        frameBuffer = FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.width, Gdx.graphics.height, true)

        // Create sprite batch for post-processing
        postProcessBatch = SpriteBatch()
        postProcessCamera = OrthographicCamera(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        postProcessCamera.setToOrtho(false)

        // Create full-screen quad
        createFullScreenQuad()

        // Compile shaders
        compileShaders()

        println("ShaderEffectManager initialized with ${shaders.size} effects")
    }

    private fun createFullScreenQuad() {
        val vertices = floatArrayOf(
            -1f, -1f, 0f, 0f,  // Bottom-left
            1f, -1f, 1f, 0f,  // Bottom-right
            1f,  1f, 1f, 1f,  // Top-right
            -1f,  1f, 0f, 1f   // Top-left
        )

        val indices = shortArrayOf(0, 1, 2, 2, 3, 0)

        fullScreenQuad = Mesh(true, 4, 6,
            VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
            VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0")
        )

        fullScreenQuad.setVertices(vertices)
        fullScreenQuad.setIndices(indices)
    }

    private fun compileShaders() {
        // Default passthrough shader
        shaders[ShaderEffect.NONE] = createShader(vertexShader, passthroughShader)

        // Effect shaders
        shaders[ShaderEffect.BRIGHT_VIBRANT] = createShader(vertexShader, brightVibrantShader)
        shaders[ShaderEffect.RETRO_PIXEL] = createShader(vertexShader, retroPixelShader)
        shaders[ShaderEffect.DREAMY_SOFT] = createShader(vertexShader, dreamySoftShader)
        shaders[ShaderEffect.NEON_GLOW] = createShader(vertexShader, neonGlowShader)
        shaders[ShaderEffect.BLACK_WHITE] = createShader(vertexShader, blackWhiteShader)
        shaders[ShaderEffect.OLD_MOVIE] = createShader(vertexShader, oldmovieShader)
        shaders[ShaderEffect.FILM_NOIR] = createShader(vertexShader, filmNoirShader)
        shaders[ShaderEffect.SIN_CITY] = createShader(vertexShader, sinCityShader)
        shaders[ShaderEffect.GRINDHOUSE] = createShader(vertexShader, grindhouseShader)
        shaders[ShaderEffect.CYBERPUNK] = createShader(vertexShader, cyberpunkShader)
        shaders[ShaderEffect.VINTAGE_SEPIA] = createShader(vertexShader, vintageSepia)
        shaders[ShaderEffect.THERMAL_VISION] = createShader(vertexShader, thermalVisionShader)
        shaders[ShaderEffect.GLITCH_MATRIX] = createShader(vertexShader, glitchMatrixShader)
        shaders[ShaderEffect.COMIC_BOOK] = createShader(vertexShader, comicBookShader)
        shaders[ShaderEffect.UNDERWATER] = createShader(vertexShader, underwaterShader)
        shaders[ShaderEffect.NIGHT_VISION] = createShader(vertexShader, nightVisionShader)
    }

    private fun createShader(vertex: String, fragment: String): ShaderProgram {
        ShaderProgram.pedantic = false
        val shader = ShaderProgram(vertex, fragment)
        if (!shader.isCompiled) {
            println("Shader compilation error: ${shader.log}")
        } else {
            println("Shader compiled successfully")
        }
        return shader
    }

    fun beginCapture() {
        frameBuffer.begin()
    }

    fun endCaptureAndRender() {
        frameBuffer.end()

        // Apply post-processing effect
        applyPostProcessing()
    }

    private fun applyPostProcessing() {
        val activeEffect = if (isEffectsEnabled) currentEffect else ShaderEffect.NONE
        val shader = shaders[activeEffect] ?: return

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)

        shader.bind()
        shader.setUniformi("u_texture", 0)
        shader.setUniformf("u_time", System.currentTimeMillis() / 1000f)
        shader.setUniformf("u_resolution", Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

        frameBuffer.colorBufferTexture.bind(0)

        fullScreenQuad.render(shader, GL20.GL_TRIANGLES)

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    fun toggleEffectsEnabled() {
        isEffectsEnabled = !isEffectsEnabled
        println("Shader effects are now ${if (isEffectsEnabled) "ENABLED" else "DISABLED"}")
    }

    private fun setEffect(effect: ShaderEffect) {
        currentEffect = effect
        println("Shader effect changed to: ${effect.displayName}")
    }

    fun getCurrentEffect(): ShaderEffect = currentEffect

    fun nextEffect() {
        val effects = ShaderEffect.entries.toTypedArray()
        val currentIndex = effects.indexOf(currentEffect)
        val nextIndex = (currentIndex + 1) % effects.size
        setEffect(effects[nextIndex])
    }

    fun previousEffect() {
        val effects = ShaderEffect.entries.toTypedArray()
        val currentIndex = effects.indexOf(currentEffect)
        val prevIndex = if (currentIndex == 0) effects.size - 1 else currentIndex - 1
        setEffect(effects[prevIndex])
    }

    fun resize(width: Int, height: Int) {
        frameBuffer.dispose()
        frameBuffer = FrameBuffer(Pixmap.Format.RGBA8888, width, height, true)

        postProcessCamera.setToOrtho(false, width.toFloat(), height.toFloat())
        postProcessCamera.update()
    }

    fun dispose() {
        frameBuffer.dispose()
        postProcessBatch.dispose()
        fullScreenQuad.dispose()

        for (shader in shaders.values) {
            shader.dispose()
        }
        shaders.clear()
    }
}
