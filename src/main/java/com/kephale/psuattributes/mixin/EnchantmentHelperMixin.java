package com.kephale.psuattributes.mixin;

import com.kephale.psuattributes.MaceAbility;
import com.kephale.psuattributes.SpearAbility;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    @WrapMethod(method = "onTargetDamaged(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/damage/DamageSource;Lnet/minecraft/item/ItemStack;)V")
    private static void psuattributes$targetDamaged(ServerWorld world, Entity target, DamageSource source,
                                                    ItemStack weapon, Operation<Void> original) {
        original.call(world, target, source, MaceAbility.overrideWeapon(source, weapon));
    }

    @WrapMethod(method = "onTargetDamaged(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/damage/DamageSource;Lnet/minecraft/item/ItemStack;Ljava/util/function/Consumer;)V")
    private static void psuattributes$targetDamagedCallback(ServerWorld world, Entity target, DamageSource source,
                                                            ItemStack weapon, Consumer<Item> breakCallback,
                                                            Operation<Void> original) {
        original.call(world, target, source, MaceAbility.overrideWeapon(source, weapon), breakCallback);
    }

    @Inject(method = "onAttack(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private static void psuattributes$onAttack(ServerWorld world, Entity attacker, CallbackInfo ci) {
        if (!SpearAbility.allowLunge(attacker)) ci.cancel();
    }
}
