#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    vec4 center = texture(DiffuseSampler, texCoord);

    // 1. Detect if this pixel is RED (Enemy / Hazard / Threat)
    float maxGB = max(center.g, center.b);
    float redDiff = center.r - maxGB;

    if (redDiff > 0.16 && center.r > 0.28) {
        // PURE VIBRANT SUPERHOT CRYSTALLINE RED
        float brightness = dot(center.rgb, vec3(0.299, 0.587, 0.114));
        vec3 enemyRed = mix(vec3(0.96, 0.04, 0.04), vec3(1.0, 0.38, 0.38), clamp((brightness - 0.35) * 1.6, 0.0, 1.0));
        fragColor = vec4(enemyRed, 1.0);
        return;
    }

    // 2. ENVIRONMENT / BLOCKS / WORLD
    // Sobel edge detection for crisp architectural block contours
    vec4 left  = texture(DiffuseSampler, texCoord - vec2(oneTexel.x, 0.0));
    vec4 right = texture(DiffuseSampler, texCoord + vec2(oneTexel.x, 0.0));
    vec4 up    = texture(DiffuseSampler, texCoord - vec2(0.0, oneTexel.y));
    vec4 down  = texture(DiffuseSampler, texCoord + vec2(0.0, oneTexel.y));

    float edge = length(center.rgb - left.rgb) + length(center.rgb - right.rgb) +
                 length(center.rgb - up.rgb)   + length(center.rgb - down.rgb);

    // Convert world luminance to minimalist stark white / architectural light gray
    float luma = dot(center.rgb, vec3(0.299, 0.587, 0.114));
    float whiteBase = 0.82 + luma * 0.16;

    // Darken geometric contours (subtle architectural block outlines)
    float edgeFactor = clamp(edge * 1.6, 0.0, 0.45);
    float finalWhite = clamp(whiteBase - edgeFactor, 0.35, 1.0);

    // Clean neutral cool-white palette
    vec3 envColor = vec3(finalWhite * 0.97, finalWhite * 0.98, finalWhite);

    fragColor = vec4(envColor, 1.0);
}
