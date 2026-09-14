#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    vec4 center = texture(DiffuseSampler, texCoord);

    // 1. Detect CRYSTAL RED ENEMY pixels robustly
    // The crystal red entity texture is pure red R=1.0, G=0.0, B=0.0.
    // Under ambient lighting, directional shadows, or caves, R ranges between 0.18 and 1.00,
    // while green and blue remain low (ambient blue/green tints up to 0.15).
    float maxGB = max(center.g, center.b);
    float sumGB = center.g + center.b;

    bool isCrystalEnemy = (center.r > 0.18) && (center.r > maxGB * 2.0) && (center.r > sumGB * 1.1) && (maxGB < 0.22);

    if (isCrystalEnemy) {
        // PURE VIBRANT SUPERHOT CRYSTALLINE RED
        // Subtle specular facet shine based on directional normal brightness
        float brightness = center.r;
        vec3 enemyRed = mix(vec3(0.90, 0.02, 0.02), vec3(1.0, 0.28, 0.28), clamp((brightness - 0.4) * 1.8, 0.0, 1.0));
        fragColor = vec4(enemyRed, 1.0);
        return;
    }

    // 2. STARK ARCHITECTURAL SUPERHOT ENVIRONMENT
    // Sobel edge detection for crisp architectural block contours
    vec4 left  = texture(DiffuseSampler, texCoord - vec2(oneTexel.x, 0.0));
    vec4 right = texture(DiffuseSampler, texCoord + vec2(oneTexel.x, 0.0));
    vec4 up    = texture(DiffuseSampler, texCoord - vec2(0.0, oneTexel.y));
    vec4 down  = texture(DiffuseSampler, texCoord + vec2(0.0, oneTexel.y));

    float edge = length(center.rgb - left.rgb) + length(center.rgb - right.rgb) +
                 length(center.rgb - up.rgb)   + length(center.rgb - down.rgb);

    // Filter out micro-texture grain while keeping crisp architectural contours
    float edgeFactor = smoothstep(0.20, 0.55, edge) * 0.70;

    // Calibrated off-white concrete base
    float lum = dot(center.rgb, vec3(0.299, 0.587, 0.114));
    float baseShade = 0.76 + clamp(lum, 0.0, 1.0) * 0.12;

    // Geometric edge darkening (the iconic block outlines of Superhot)
    float finalShade = clamp(baseShade - edgeFactor, 0.18, 0.90);

    // Clean neutral cool-concrete tone
    vec3 envColor = vec3(finalShade * 0.97, finalShade * 0.98, finalShade);

    fragColor = vec4(envColor, 1.0);
}
