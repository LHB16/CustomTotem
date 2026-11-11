#version 330

uniform sampler2D CaptureSampler;
uniform sampler2D HaloSampler;

layout(std140) uniform HandOutlineConfig {
    vec4 Tint;
    vec4 Sheen;
    vec4 Shape;
    vec4 ModeParams;
};

in vec2 texCoord;

out vec4 fragColor;

const vec2 RAYS[8] = vec2[](
    vec2(1.0, 0.0), vec2(0.7071, 0.7071), vec2(0.0, 1.0), vec2(-0.7071, 0.7071),
    vec2(-1.0, 0.0), vec2(-0.7071, -0.7071), vec2(0.0, -1.0), vec2(0.7071, -0.7071)
);

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec4 hand = texture(CaptureSampler, texCoord);
    vec4 halo = texture(HaloSampler, texCoord);
    if (hand.a <= 0.0 && halo.a <= 0.0) {
        discard;
    }

    float mode = ModeParams.x;
    float time = Shape.z;
    float sat = ModeParams.z > 0.0 ? ModeParams.z : 0.9;
    float val = ModeParams.w > 0.0 ? ModeParams.w : 1.0;

    vec3 baseColor = Tint.rgb;

    if (mode == 1.0) {
        // Mode 1: Rainbow Unified (toàn bộ outline cùng 1 màu đổi dần theo thời gian)
        float hue = fract(time);
        baseColor = hsv2rgb(vec3(hue, sat, val));
    } else if (mode == 2.0) {
        // Mode 2: Rainbow Spiral (xoắn cầu vồng quanh tâm tay)
        vec2 center = vec2(0.68, 0.75);
        vec2 delta = texCoord - center;
        float angle = atan(delta.y, delta.x);
        float dist = length(delta);
        float hue = fract((angle / 6.2831853) * 2.0 + dist * 3.5 - time);
        baseColor = hsv2rgb(vec3(hue, sat, val));
    } else if (mode == 3.0) {
        // Mode 3: Rainbow Wave (sóng cầu vồng chuyển động chéo)
        float hue = fract((texCoord.x + texCoord.y) * 2.0 - time);
        baseColor = hsv2rgb(vec3(hue, sat, val));
    }

    // Sheen glint
    float phase = fract(dot(texCoord, vec2(0.45, -0.55)) - time * 1.5);
    float glint = (1.0 - smoothstep(0.0, 0.06, abs(phase - 0.5))) * Sheen.a;
    vec3 tint = mix(baseColor, Sheen.rgb, glint);

    // Aura halo (outer soft glow)
    float auraAlpha = Tint.a * (1.0 - exp(-3.4 * halo.a));
    vec4 aura = vec4(tint * auraAlpha, auraAlpha * 0.6);

    // Rim outline (edge detection)
    vec3 body = hand.rgb;
    if (hand.a > 0.0 && Shape.x > 0.0) {
        vec2 reach = Shape.y / vec2(textureSize(CaptureSampler, 0));
        float open = 0.0;
        for (int i = 0; i < 8; i++) {
            open += 1.0 - texture(CaptureSampler, texCoord + RAYS[i] * reach).a;
        }
        float rim = smoothstep(0.0, 0.45, open * 0.125) * Shape.x;
        body = mix(body, tint * hand.a, rim * 0.75) + tint * hand.a * rim * 0.25;
    }

    fragColor = vec4(body, hand.a) + aura * (1.0 - hand.a);
}
