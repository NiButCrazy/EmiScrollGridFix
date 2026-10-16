package com.nibutcrazy.emiscrollgridfix.mixin;

import com.nibutcrazy.emiscrollgridfix.JemiPagination;
import com.nibutcrazy.emiscrollgridfix.PaginatedSlot;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.jemi.widget.JemiSlotWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 JEMI 的槽位能按页位移：改 {@code SlotWidget#getBounds()} 的返回值。
 *
 * <p>为什么是这里：EMI 的 {@code SlotWidget} 把坐标存成 {@code final int x/y}（构造后动不了），
 * 但它的 {@code drawStack} / {@code drawBackground} 都是<b>基于 {@code getBounds()} 画</b>的——
 * 所以只要这里返回被位移过的矩形，物品和 EMI 自己的槽框就会跟着走。
 * JEI 那张槽框是绝对坐标，另有 {@code JemiSlotWidgetMixin} 处理。
 *
 * <p>只对 JEMI 的槽位生效（用 {@code instanceof} 挡掉其它槽位），EMI 其余界面不受影响；
 * 不在当前页的槽位返回 {@code Bounds.EMPTY}，顺带让悬停/提示/点击也失效。
 */
@Mixin(value = SlotWidget.class, remap = false)
public abstract class EmiSlotWidgetMixin {
    @Inject(method = "getBounds", at = @At("RETURN"), cancellable = true, remap = false)
    private void emiScrollGridFix$pageShiftedBounds(CallbackInfoReturnable<Bounds> cir) {
        if (!((Object) this instanceof JemiSlotWidget jemiSlotWidget)) {
            return;
        }
        if (!(jemiSlotWidget.slot instanceof PaginatedSlot paginatedSlot)) {
            return;
        }
        var ref = paginatedSlot.emiScrollGridFix$slotRef();
        if (ref == null) {
            return;
        }
        if (JemiPagination.hidden(ref)) {
            cir.setReturnValue(Bounds.EMPTY);
            return;
        }
        var shift = JemiPagination.shift(ref);
        if (shift == 0) {
            return;
        }
        var bounds = cir.getReturnValue();
        cir.setReturnValue(new Bounds(bounds.left(), bounds.top() - shift, bounds.width(), bounds.height()));
    }
}
