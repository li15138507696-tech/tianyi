package net.guzhenren.tianfu.client.vfx;

// 由几页特效工坊（jiye-fx）生成。
// API 依据 Veil 1.21 wiki + 源码（VeilRenderType.get / ShaderManager.getShader /
// ShaderUniformAccess / VeilEventPlatform.preVeilPostProcessing）。
// 如果你的 Veil 版本不同，以 jar 内方法签名为准；所有 getUniform 都必须判空。

import net.minecraft.resources.ResourceLocation;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.uniform.ShaderUniformAccess;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.platform.VeilEventPlatform;

/**
 * star_gold_domain（screen_post）—— 屏幕后处理。
 * JSON 管线必须在客户端初始化时 add() 一次（见下），之后在 after_level 阶段执行；这里只负责推 uniform。
 */
public final class StarGoldDomainFx {
    public static final String MODID = "tianfu";
    public static final ResourceLocation PIPELINE = ResourceLocation.fromNamespaceAndPath(MODID, "star_gold_domain");

    private static float StarGoldDomainStrength = 0.8000f;
    private static float StarGoldDomainPhase = 0.0000f;
    private static float StarGoldDomainProgress = 0.0000f;
    private static float StarGoldDomainFog = 0.1000f;
    private static float StarGoldDomainDisturbance = 0.5000f;
    private static float StarGoldDomainFlash = 0.0000f;
    private static float StarGoldDomainRecovery = 0.0000f;
    private static float StarGoldDomainTintX = 0.4196f, StarGoldDomainTintY = 0.2902f, StarGoldDomainTintZ = 0.0627f;

    private StarGoldDomainFx() {}

    /** 在客户端初始化时调用一次。 */
    public static void register() {
        // ★ 必须 add()：Veil 每帧只遍历 activePipelines，JSON 里写 renderStage **不会**自动激活，
        //   它只是「在哪个阶段跑」的过滤条件。漏了这一步 = 日志显示已加载、画面永远不动。
        VeilRenderSystem.renderer().getPostProcessingManager().add(1210, PIPELINE);

        VeilEventPlatform.INSTANCE.preVeilPostProcessing((pipelineName, pipeline, context) -> {
            if (!PIPELINE.equals(pipelineName)) return;
            setFloat(pipeline, "StarGoldDomainStrength", StarGoldDomainStrength);
            setFloat(pipeline, "StarGoldDomainPhase", StarGoldDomainPhase);
            setFloat(pipeline, "StarGoldDomainProgress", StarGoldDomainProgress);
            setFloat(pipeline, "StarGoldDomainFog", StarGoldDomainFog);
            setFloat(pipeline, "StarGoldDomainDisturbance", StarGoldDomainDisturbance);
            setFloat(pipeline, "StarGoldDomainFlash", StarGoldDomainFlash);
            setFloat(pipeline, "StarGoldDomainRecovery", StarGoldDomainRecovery);
            setVec3(pipeline, "StarGoldDomainTint", StarGoldDomainTintX, StarGoldDomainTintY, StarGoldDomainTintZ);
        });
    }

    public static void setStarGoldDomainStrength(float v) { StarGoldDomainStrength = v; }

    public static void setStarGoldDomainPhase(float v) { StarGoldDomainPhase = v; }

    public static void setStarGoldDomainProgress(float v) { StarGoldDomainProgress = v; }

    public static void setStarGoldDomainFog(float v) { StarGoldDomainFog = v; }

    public static void setStarGoldDomainDisturbance(float v) { StarGoldDomainDisturbance = v; }

    public static void setStarGoldDomainFlash(float v) { StarGoldDomainFlash = v; }

    public static void setStarGoldDomainRecovery(float v) { StarGoldDomainRecovery = v; }

    public static void setStarGoldDomainTint(float x, float y, float z) {
        StarGoldDomainTintX = x; StarGoldDomainTintY = y; StarGoldDomainTintZ = z;
    }

    private static void setFloat(foundry.veil.api.client.render.post.PostPipeline p, String n, float v) {
        ShaderUniformAccess u = p.getUniform(n);
        if (u != null && u.isValid()) u.setFloat(v);
    }

    private static void setInt(foundry.veil.api.client.render.post.PostPipeline p, String n, int v) {
        ShaderUniformAccess u = p.getUniform(n);
        if (u != null && u.isValid()) u.setInt(v);
    }

    private static void setVec3(foundry.veil.api.client.render.post.PostPipeline p, String n, float x, float y, float z) {
        ShaderUniformAccess u = p.getUniform(n);
        if (u != null && u.isValid()) u.setVector(x, y, z);
    }
}
