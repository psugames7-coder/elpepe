package com.kephale.psuattributes.mixin;

import com.kephale.psuattributes.Abilities;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerEntity.class)
public abstract class PlayerSweepMixin {

    @WrapMethod(method = "doSweepingAttack(Lnet/minecraft/entity/Entity;FLnet/minecraft/entity/damage/DamageSource;F)V")
    private void psuattributes$sweep(Entity target, float damage, DamageSource source, float cooldown, Operation<Void> original) {
        Abilities.sweepDepth++;
        try {
            original.call(target, damage, source, cooldown);
        } finally {
            Abilities.sweepDepth--;
        }
    }
}
