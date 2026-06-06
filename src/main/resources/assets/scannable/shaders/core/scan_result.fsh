#version 330

// Animated shimmer drawn over each scan-result box (additive): horizontal scanlines that scroll
// over time, a gentle global brightness pulse, and an edge glow that brightens face borders.
// Ported from the 1.21.1 ShaderInstance shader; the per-second "time" uniform is recovered from the
// Globals UBO's GameTime (a 0..1 day fraction, so *1200 gives seconds).

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float time = GameTime * 1200.0;

    float scanlines = sqrt(sin(gl_FragCoord.y + time * 10.0) * 0.5 + 0.5);

    float timeScale = (sin(time * 2.5) + 1.0) * 0.5;
    timeScale = timeScale * 0.15 + 0.85;

    vec2 edgeDist = abs(texCoord0.xy - 0.5) * 2.0;
    edgeDist = edgeDist * 0.25 + 0.75;
    float edgeMul = pow(max(edgeDist.x, edgeDist.y), 8.0) * 0.8 + 0.2;

    vec4 c = vertexColor * scanlines;
    c *= timeScale;
    c *= edgeMul;
    fragColor = c;
}
