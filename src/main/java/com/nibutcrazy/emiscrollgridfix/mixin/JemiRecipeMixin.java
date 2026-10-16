package com.nibutcrazy.emiscrollgridfix.mixin;

import com.nibutcrazy.emiscrollgridfix.JemiPagination;
import com.nibutcrazy.emiscrollgridfix.PageControlWidget;
import com.nibutcrazy.emiscrollgridfix.ScrollGridLayout;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.jemi.JemiRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * JEMI 侧翻页的「挂控件」部分：在配方控件都建好之后（{@code addWidgets} 末尾），
 * 给超过一页容量的滚动格工厂挂上翻页控件。
 *
 * <p>记账是槽位控件构造时登记的（键就是配方本身，即这里的 {@code this}），
 * 记账里带着面积/列数/行数/槽位总数，正好够算页数和控件位置。
 */
@Mixin(value = JemiRecipe.class, remap = false)
public abstract class JemiRecipeMixin {
    @Inject(method = "addWidgets", at = @At("TAIL"), remap = false)
    private void emiScrollGridFix$addPageControls(WidgetHolder widgets, CallbackInfo ci) {
        var recipe = (Object) (Object) this;
        for (var entry : JemiPagination.entriesForRecipe(recipe)) {
            if (!entry.overflows()) {
                continue;
            }
            var layout = entry.layout();
            var rows = layout.rows();
            var pageCount = (entry.count() + layout.capacity() - 1) / layout.capacity();
            // 网格右侧是 JEI 给滚动条预留的 16px 竖条，控件宽 10，放在它中间
            var stripX = layout.x() + layout.columns() * ScrollGridLayout.SLOT_SIZE + 1;
            // 滚轮范围：整个槽位区域
            var slotArea = new Bounds(layout.x(), layout.y(), layout.columns() * ScrollGridLayout.SLOT_SIZE, rows * ScrollGridLayout.SLOT_SIZE);
            // entry 本身就是 PageScroller：翻页只改它的 page，槽位控件渲染时再去读
            widgets.add(new PageControlWidget(entry, stripX, layout.y(), rows, pageCount, slotArea));
        }
        JemiPagination.clearRecipe(recipe);
    }
}
