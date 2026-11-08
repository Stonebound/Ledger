package com.github.quiltservertools.ledger.neoforge.mixin;

import com.github.quiltservertools.ledger.callbacks.EntityModifyCallback;
import com.github.quiltservertools.ledger.utility.NbtUtils;
import com.github.quiltservertools.ledger.utility.Sources;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.IShearable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge disables the shear code in each mob's mobInteract and shears here instead. This logs the same two mobs
 * that Ledger logs on Fabric.
 */
@Mixin(ShearsItem.class)
public abstract class ShearsItemMixin {
    @WrapOperation(
            method = "interactLivingEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/common/IShearable;onSheared(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Ljava/util/List;"
            )
    )
    private List<ItemStack> ledger$logShear(
            IShearable target,
            Player player,
            ItemStack stack,
            Level level,
            BlockPos pos,
            Operation<List<ItemStack>> original,
            @Local(argsOnly = true) LivingEntity entity
    ) {
        boolean logged = !level.isClientSide() && (entity instanceof Sheep || entity instanceof SnowGolem);
        CompoundTag oldEntityTags = logged ? NbtUtils.INSTANCE.createNbt(entity) : null;

        List<ItemStack> drops = original.call(target, player, stack, level, pos);

        if (logged) {
            EntityModifyCallback.EVENT.invoker().modify(level, entity.blockPosition(), oldEntityTags, entity, null, player, Sources.SHEAR);
        }
        return drops;
    }
}
