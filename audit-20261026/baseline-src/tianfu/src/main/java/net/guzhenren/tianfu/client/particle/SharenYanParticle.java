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
 * 杀焰：血红火苗。速度全收（{@code vx*0.0}），位置由服务端给，本地只做形变与帧动画。
 */
@OnlyIn(Dist.CLIENT)
public class SharenYanParticle extends TextureSheetParticle {
    private final SpriteSet spriteSet;

    public static SharenYanParticle.Provider provider(SpriteSet spriteSet) {
        return new SharenYanParticle.Provider(spriteSet);
    }

    protected SharenYanParticle(ClientLevel world, double x, double y, double z,
            double vx, double vy, double vz, SpriteSet spriteSet) {
        super(world, x, y, z);
        this.spriteSet = spriteSet;
        this.setSize(0.14F, 0.14F);
        this.quadSize *= 2.0F;
        this.lifetime = Math.max(1, 20 + (this.random.nextInt(10) - 5));
        this.gravity = -0.02F;       // 火苗上窜
        this.hasPhysics = false;
        this.xd = vx * 0.05;
        this.yd = vy * 0.05;
        this.zd = vz * 0.05;
        this.setSpriteFromAge(spriteSet);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            this.setSprite(this.spriteSet.get(this.age / 2 % 8 + 1, 8));
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
            return new SharenYanParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, this.spriteSet);
        }
    }
}
