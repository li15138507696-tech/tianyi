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
 * 念头光：金色光点（比真元气略小、更慢、更"飘"）。
 */
@OnlyIn(Dist.CLIENT)
public class NiantouGuangParticle extends TextureSheetParticle {
    private final SpriteSet spriteSet;

    public static NiantouGuangParticle.Provider provider(SpriteSet spriteSet) {
        return new NiantouGuangParticle.Provider(spriteSet);
    }

    protected NiantouGuangParticle(ClientLevel world, double x, double y, double z,
            double vx, double vy, double vz, SpriteSet spriteSet) {
        super(world, x, y, z);
        this.spriteSet = spriteSet;
        this.setSize(0.10F, 0.10F);
        this.quadSize *= 1.3F;
        this.lifetime = Math.max(1, 30 + (this.random.nextInt(16) - 8));
        this.gravity = -0.004F;      // 极轻微上浮
        this.hasPhysics = false;
        this.xd = vx * 0.10;
        this.yd = vy * 0.10;
        this.zd = vz * 0.10;
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
            this.setSprite(this.spriteSet.get(this.age / 4 % 8 + 1, 8));
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
            return new NiantouGuangParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, this.spriteSet);
        }
    }
}
