package com.kephale.psuattributes.mixin;

import net.minecraft.entity.player.HungerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HungerManager.class)
public interface HungerManagerAccessor {

    @Accessor("exhaustion")
    float psuattributes$getExhaustion();

    @Accessor("exhaustion")
    void psuattributes$setExhaustion(float value);
}
