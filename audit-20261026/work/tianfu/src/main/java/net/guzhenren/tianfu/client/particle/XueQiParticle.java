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
 * 血气：暗红雾团。慢速弥散 + 轻微缩放，帧走慢一档（{@code age/5}）显得更粘稠。
 */
@OnlyIn(Dist.CLIENT)
public class XueQiParticle extends TextureSheetParticle {
    private final SpriteSet spriteSet;

    public static XueQiParticle.Provider provider(SpriteSet spriteSet) {
        return new XueQiParticle.Provider(spriteSet);
    }

    protected XueQiParticle(ClientLevel world, double x, double y, double z,
            double vx, double vy, double vz, SpriteSet spriteSet) {
        super(world, x, y, z);
        this.spriteSet = spriteSet;
        this.setSize(0.16F, 0.16F);
        this.quadSize *= 1.4F;
        this.lifetime = Math.max(1, 40 + (this.random.nextInt(20) - 10));
        this.gravity = -0.006F;
        this.hasPhysics = false;
        this.xd = vx * 0.08;
        this.yd = vy * 0.08;
        this.zd = vz * 0.08;
        this.setSpriteFromAge(spriteSet);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            this.setSprite(this.spriteSet.get(this.age / 5 % 8 + 1, 8));
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
            return new XueQiParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, this.spriteSet);
        }
    }
}
