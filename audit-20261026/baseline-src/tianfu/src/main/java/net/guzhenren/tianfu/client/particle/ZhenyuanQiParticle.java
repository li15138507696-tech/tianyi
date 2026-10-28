package net.guzhenren.tianfu.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 真元气：青白能量光点。
 *
 * <p>形态：静止悬浮 + 轻微缩放脉动，帧按 {@code age/3} 走（8 帧 ≈ 0.8 秒一轮）。
 * 与本体 {@code LeiDianParticle} 同款 {@link TextureSheetParticle} 写法。
 */
@OnlyIn(Dist.CLIENT)
public class ZhenyuanQiParticle extends TextureSheetParticle {
    private final SpriteSet spriteSet;

    public static ZhenyuanQiParticle.Provider provider(SpriteSet spriteSet) {
        return new ZhenyuanQiParticle.Provider(spriteSet);
    }

    protected ZhenyuanQiParticle(ClientLevel world, double x, double y, double z,
            double vx, double vy, double vz, SpriteSet spriteSet) {
        super(world, x, y, z);
        this.spriteSet = spriteSet;
        this.setSize(0.12F, 0.12F);
        this.quadSize *= 1.6F;
        this.lifetime = Math.max(1, 24 + (this.random.nextInt(12) - 6));
        this.gravity = 0.0F;
        this.hasPhysics = false;
        // 速度打折：主要是位置漂移，不是冲刺
        this.xd = vx * 0.15;
        this.yd = vy * 0.15;
        this.zd = vz * 0.15;
        this.setSpriteFromAge(spriteSet);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880; // 全亮，暗处也看得见
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            this.setSprite(this.spriteSet.get(this.age / 3 % 8 + 1, 8));
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;

        public Provider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        @Override
        public Particle createParticle(SimpleParticleType typeIn, ClientLevel worldIn,
                double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new ZhenyuanQiParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, this.spriteSet);
        }
    }
}
