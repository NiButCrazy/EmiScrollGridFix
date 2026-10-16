package com.nibutcrazy.emiscrollgridfix.mixin;

import com.nibutcrazy.emiscrollgridfix.JemiPagination;
import com.nibutcrazy.emiscrollgridfix.PaginatedSlot;
import dev.emi.emi.jemi.impl.JemiRecipeSlot;
import dev.emi.emi.jemi.impl.JemiRecipeSlotBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给 JEMI 的槽位对象贴上「属于哪条滚动格工厂、第几个」的标记。
 *
 * <p>槽位的 x/y 是在构造时从 {@code JemiRecipeSlotBuilder} 复制过来的常量，之后动不了，
 * 所以翻页只能在渲染/取边界时按这个标记算位移（见 {@code JemiSlotWidgetMixin} 与 {@code EmiSlotWidgetMixin}）。
 */
@Mixin(value = JemiRecipeSlot.class, remap = false)
public abstract class JemiRecipeSlotMixin implements PaginatedSlot {
    @Unique
    private JemiPagination.SlotRef emiScrollGridFix$slotRef;

    @Inject(method = "<init>(Ldev/emi/emi/jemi/impl/JemiRecipeSlotBuilder;)V", at = @At("TAIL"), remap = false)
    private void emiScrollGridFix$stamp(JemiRecipeSlotBuilder builder, CallbackInfo ci) {
        this.emiScrollGridFix$slotRef = JemiPagination.slotRef(builder);
    }

    @Override
    public JemiPagination.SlotRef emiScrollGridFix$slotRef() {
        return this.emiScrollGridFix$slotRef;
    }
}
