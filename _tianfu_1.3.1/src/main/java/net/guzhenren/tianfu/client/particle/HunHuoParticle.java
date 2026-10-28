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
 * 魂火：幽蓝火苗（与杀焰同形，换色 + 更慢更沉）。
 */
@OnlyIn(Dist.CLIENT)
public class HunHuoParticle extends TextureSheetParticle {
    private final SpriteSet spriteSet;

    public static HunHuoParticle.Provider provider(SpriteSet spriteSet) {
        return new HunHuoParticle.Provider(spriteSet);
    }

    protected HunHuoParticle(ClientLevel world, double x, double y, double z,
            double vx, double vy, double vz, SpriteSet spriteSet) {
        super(world, x, y, z);
        this.spriteSet = spriteSet;
        this.setSize(0.12F, 0.12F);
        this.quadSize *= 1.7F;
        this.lifetime = Math.max(1, 26 + (this.random.nextInt(12) - 6));
        this.gravity = -0.01F;
        this.hasPhysics = false;
        this.xd = vx * 0.06;
        this.yd = vy * 0.06;
        this.zd = vz * 0.06;
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
            return new HunHuoParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, this.spriteSet);
        }
    }
}
