package net.bagaja.mafiagame

/**
 * Contains all shader source code definitions for the ShaderEffectManager
 * This separation keeps the main manager class cleaner and more maintainable
 */
object ShaderDefinitions {

    const val vertexShader = """
        attribute vec4 a_position;
        attribute vec2 a_texCoord0;
        varying vec2 v_texCoord;

        void main() {
            v_texCoord = a_texCoord0;
            gl_Position = a_position;
        }
    """

    const val passthroughShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        varying vec2 v_texCoord;

        void main() {
            gl_FragColor = texture2D(u_texture, v_texCoord);
        }
    """

    const val brightVibrantShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform float u_time;
        varying vec2 v_texCoord;

        void main() {
            vec4 color = texture2D(u_texture, v_texCoord);

            // Increase brightness and saturation
            color.rgb = pow(color.rgb, vec3(0.8));
            // ... rest of bright vibrant shader code
        }
    """

    const val retroPixelShader = """
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
            // ... rest of retro pixel shader code
        }
    """

    const val dreamySoftShader = """
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
            // ... rest of dreamy soft shader code
        }
    """

    const val neonGlowShader = """
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
            // ... rest of neon glow shader code
        }
    """

    const val blackWhiteShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform float u_time;
        varying vec2 v_texCoord;

        void main() {
            vec4 color = texture2D(u_texture, v_texCoord);
            // Convert to grayscale using luminance formula
            // ... rest of black & white shader code
        }
    """

    const val oldMovieShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        varying vec2 v_texCoord;

        void main() {
            vec4 color = texture2D(u_texture, v_texCoord);
            // Simple grayscale conversion
            // ... rest of old movie shader code
        }
    """

    const val filmNoirShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform float u_time;
        uniform vec2 u_resolution;
        varying vec2 v_texCoord;

        void main() {
            vec4 color = texture2D(u_texture, v_texCoord);
            // Calculate base luminance and colored lighting
            // ... rest of film noir shader code
        }
    """

    const val sinCityShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform float u_time;
        varying vec2 v_texCoord;

        void main() {
            vec4 originalColor = texture2D(u_texture, v_texCoord);
            // Sin City selective color effect
            // ... rest of sin city shader code
        }
    """

    const val grindhouseShader = """
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
        // ... rest of grindhouse shader code
    """

    const val cyberpunkShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform float u_time;
        uniform vec2 u_resolution;
        varying vec2 v_texCoord;

        void main() {
            vec2 uv = v_texCoord;
            // Chromatic aberration and cyberpunk effects
            // ... rest of cyberpunk shader code
        }
    """

    const val vintageSepia = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform float u_time;
        varying vec2 v_texCoord;

        void main() {
            vec4 color = texture2D(u_texture, v_texCoord);
            // Sepia tone mapping
            // ... rest of vintage sepia shader code
        }
    """

    const val thermalVisionShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform float u_time;
        varying vec2 v_texCoord;

        void main() {
            vec4 color = texture2D(u_texture, v_texCoord);
            // Calculate temperature based on luminance
            // ... rest of thermal vision shader code
        }
    """

    const val glitchMatrixShader = """
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
        // ... rest of glitch matrix shader code
    """

    const val comicBookShader = """
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
            // ... rest of comic book shader code
        }
    """

    const val underwaterShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform float u_time;
        uniform vec2 u_resolution;
        varying vec2 v_texCoord;

        void main() {
            vec2 uv = v_texCoord;
            // Water distortion effects
            // ... rest of underwater shader code
        }
    """

    const val nightVisionShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform float u_time;
        uniform vec2 u_resolution;
        varying vec2 v_texCoord;

        void main() {
            vec4 color = texture2D(u_texture, v_texCoord);
            // Night vision green tint and amplification
            // ... rest of night vision shader code
        }
    """
}
