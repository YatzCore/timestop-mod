#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    vec4 center = texture(DiffuseSampler, texCoord);

    // 1. Detect if this pixel is a Crystal Enemy
    // Enemies are textured with exact R == B and zero green (G = 0).
    // Directional lighting scales R and B equally, so abs(R - B) remains ~0 even in shadow.
    // Also include pure red flash highlights (R > 0.70 && G < 0.05) as crystal red.
    float chromaticDelta = abs(center.r - center.b);
    float shade = max(center.r, center.b);

    bool isCrystalEnemy = (chromaticDelta < 0.12 && center.g < 0.08 && shade > 0.16) ||
                          (center.r > 0.70 && center.g < 0.05);

    if (isCrystalEnemy) {
        // PURE VIBRANT CRYSTAL RED OUTPUT (100% of the mob, shadows, highlights & hit animations!)
        float facet = clamp((shade - 0.25) / 0.70, 0.0, 1.0);
        vec3 crystalRed = mix(vec3(0.86, 0.02, 0.02), vec3(1.0, 0.32, 0.32), facet);
        fragColor = vec4(crystalRed, 1.0);
        return;
    }

    // 2. STARK ARCHITECTURAL SUPERHOT ENVIRONMENT (Comfortable off-white, no eye strain)
    vec4 left  = texture(DiffuseSampler, texCoord - vec2(oneTexel.x, 0.0));
    vec4 right = texture(DiffuseSampler, texCoord + vec2(oneTexel.x, 0.0));
    vec4 up    = texture(DiffuseSampler, texCoord - vec2(0.0, oneTexel.y));
    vec4 down  = texture(DiffuseSampler, texCoord + vec2(0.0, oneTexel.y));

    float edge = length(center.rgb - left.rgb) + length(center.rgb - right.rgb) +
                 length(center.rgb - up.rgb)   + length(center.rgb - down.rgb);

    // Filter out micro-texture grain (grass/dirt noise) while keeping crisp architectural contours
    float edgeFactor = smoothstep(0.20, 0.55, edge) * 0.70;

    // Calibrated off-white concrete base (comfortable 0.76 to 0.88 range, not glaring white)
    float lum = dot(center.rgb, vec3(0.299, 0.587, 0.114));
    float baseShade = 0.76 + clamp(lum, 0.0, 1.0) * 0.12;

    // Geometric edge darkening (the iconic block outlines of Superhot)
    float finalShade = clamp(baseShade - edgeFactor, 0.18, 0.90);

    // Clean neutral cool-concrete tone
    vec3 envColor = vec3(finalShade * 0.97, finalShade * 0.98, finalShade);

    fragColor = vec4(envColor, 1.0);
}
