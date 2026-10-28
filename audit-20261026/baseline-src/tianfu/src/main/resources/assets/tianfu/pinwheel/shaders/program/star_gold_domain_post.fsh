// star_gold_domain 屏幕后处理
// 生成：几页特效工坊（jiye-fx）。规则：早退 discard / 深度守卫 0.0008 / 准星安全区 / 主题色只走 uniform。

uniform float StarGoldDomainStrength;
uniform float StarGoldDomainPhase;
uniform float StarGoldDomainProgress;
uniform float StarGoldDomainFog;
uniform float StarGoldDomainDisturbance;
uniform float StarGoldDomainFlash;
uniform float StarGoldDomainRecovery;
uniform vec3 StarGoldDomainTint;
uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform float VeilRenderTime;

in vec2 texCoord;
out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.55;
    mat2 r = mat2(0.80, -0.60, 0.60, 0.80);
    for (int i = 0; i < 5; i++) {
        v += vnoise(p) * a;
        p = r * p * 2.03 + vec2(7.7, 13.1);
        a *= 0.49;
    }
    return v;
}

void main() {
    vec4 base = texture(DiffuseSampler0, texCoord);
    float progress = clamp(StarGoldDomainProgress, 0.0, 1.0);
    float strength = clamp(StarGoldDomainStrength, 0.0, 1.0) * mix(0.25, 1.0, smoothstep(0.0, 0.30, progress));
    if (strength <= 0.001) {
        fragColor = base;
        return;
    }
    float phase = StarGoldDomainPhase;

    float depth = texture(DiffuseDepthSampler, texCoord).r;
    vec2 centered = texCoord - vec2(0.5);
    float radius = length(centered);

    // 1) 去饱和：只去一点，保留可读性
    float lum = dot(base.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 scene = mix(base.rgb, vec3(lum), strength * 0.16);

    // 2) 远景分级：近处不染色
    float far = smoothstep(0.72, 0.995, depth);
    vec3 graded = scene * mix(vec3(1.0), StarGoldDomainTint, 0.72) + StarGoldDomainTint * 0.02;
    scene = mix(scene, graded, far * StarGoldDomainFog * 0.85);

    // 3) 低频湍流：只在远景，避免糊脸
    float turb = fbm(centered * vec2(3.2, 5.8)
            + vec2((VeilRenderTime + phase) * 0.07, -(VeilRenderTime + phase) * 0.05));
    scene += StarGoldDomainTint * (turb - 0.5) * far * StarGoldDomainDisturbance * 0.06;

    // 4) 中心压力 + 暗角
    scene *= 1.0 - strength * 0.10 * exp(-radius * 3.0);
    scene *= 1.0 - smoothstep(0.32, 0.86, radius) * strength * 0.22;

    // 5) 闪白
    scene = mix(scene, vec3(0.95, 0.98, 1.0), clamp(StarGoldDomainFlash * 0.72, 0.0, 0.85));

    // 6) 恢复
    scene = mix(scene, base.rgb, clamp(StarGoldDomainRecovery, 0.0, 1.0));

    fragColor = vec4(max(scene, vec3(0.0)), base.a);
}
