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
 * 剑气：霜白弧形刃光。帧走得快（{@code age/2}，6 帧 ≈ 0.6 秒）做出"唰"的一下。
 */
@OnlyIn(Dist.CLIENT)
public class JianQiParticle extends TextureSheetParticle {
    private final SpriteSet spriteSet;

    public static JianQiParticle.Provider provider(SpriteSet spriteSet) {
        return new JianQiParticle.Provider(spriteSet);
    }

    protected JianQiParticle(ClientLevel world, double x, double y, double z,
            double vx, double vy, double vz, SpriteSet spriteSet) {
        super(world, x, y, z);
        this.spriteSet = spriteSet;
        this.setSize(0.30F, 0.30F);     // 比其它粒子大，刃光要看得见
        this.quadSize *= 2.4F;
        this.lifetime = Math.max(1, 12 + (this.random.nextInt(6) - 3));
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = vx * 0.0;
        this.yd = vy * 0.0;
        this.zd = vz * 0.0;
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
            this.setSprite(this.spriteSet.get(this.age / 2 % 6 + 1, 6));
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
            return new JianQiParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, this.spriteSet);
        }
    }
}
