package com.kephale.psuattributes.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerEntity.class)
public interface PlayerEntityAccessor {

    @Accessor("currentExplosionImpactPos")
    void psuattributes$setCurrentExplosionImpactPos(Vec3d pos);

    @Accessor("ignoreFallDamageFromCurrentExplosion")
    void psuattributes$setIgnoreFallDamageFromCurrentExplosion(boolean value);
}
