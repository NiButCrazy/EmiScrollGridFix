package com.nibutcrazy.emiscrollgridfix.mixin;

import com.nibutcrazy.emiscrollgridfix.PageControlWidget;
import com.nibutcrazy.emiscrollgridfix.PageScroller;
import com.nibutcrazy.emiscrollgridfix.ScrollGridLayout;
import com.nibutcrazy.emiscrollgridfix.ScrollGridRegistrations;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.nolij.toomanyrecipeviewers.impl.jei.api.gui.builder.RecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 溢出时改用 TMRV 自带的滚动格控件，而不是把多余槽位画到卡片外面。
 *
 * <p>TMRV 已经实现了完整的 {@code ScrollGridWidget}（自带滚动条、并把不可见行 {@code setVisible(false)} 裁掉），
 * 只是它的 {@code addSlotToWidget} 没把槽位接过去。这里在构建卡片控件时（{@code addSlotWidgets} 末尾，
 * 即槽位控件都已建好、且早于类别自己的 {@code createRecipeExtras}）补上这一步：
 *
 * <ol>
 *   <li>从 {@link ScrollGridRegistrations} 取出这个布局器上各滚动格工厂的记账；</li>
 *   <li>数量没超过一页容量的，保持固定坐标（外观与 JEI 一致，不需要滚动条）；</li>
 *   <li>超过的，把这些槽位控件交给 {@code addScrollGridWidget(slots, columns, visibleRows)}
 *       并 {@code addSlottedWidget} 挂上，再 {@code setPosition} 到该工厂原本的区域。</li>
 * </ol>
 *
 * <p>{@code TMRVRecipeWidget} 是包私有类，所以只能用 {@code targets} 字符串指定；
 * 注入方法内部只通过 JEI 的 {@code IRecipeExtrasBuilder} 接口访问它。
 */
@SuppressWarnings("removal") // JEI 15.20 起相关接口标记为 forRemoval，这里必须继续对旧 API 生效
@Mixin(targets = "dev.nolij.toomanyrecipeviewers.impl.recipe.TMRVRecipeWidget", remap = false)
public abstract class TmrvRecipeWidgetMixin {
    /**
     * TMRV 内部真正装载 EMI 控件（以及滚动条）的容器。翻页控件要挂到这里才能被 EMI 渲染与派发点击。
     */
    @Shadow
    @Final
    private WidgetHolder widgets;

    @Inject(
            method = "addSlotWidgets(Ldev/nolij/toomanyrecipeviewers/impl/jei/api/gui/builder/RecipeLayoutBuilder;Ldev/emi/emi/api/recipe/EmiRecipe;)V",
            at = @At("TAIL"),
            remap = false
    )
    private void emiScrollGridFix$attachScrollGrids(RecipeLayoutBuilder builder, EmiRecipe recipe, CallbackInfo ci) {
        var registrations = ScrollGridRegistrations.take(builder);
        if (registrations == null || registrations.isEmpty()) {
            return;
        }
        var extras = (IRecipeExtrasBuilder) (Object) this;
        var allSlots = builder.getSlots();
        var drawables = extras.getRecipeSlots().getSlots();
        for (var factorySlots : registrations.values()) {
            if (!factorySlots.overflows()) {
                continue;
            }
            var layout = factorySlots.layout();
            var subset = ScrollGridRegistrations.drawablesAt(allSlots, drawables, factorySlots.slotIndices());
            if (subset.size() < 2) {
                continue;
            }
            var grid = extras.addScrollGridWidget(subset, layout.columns(), layout.rows());
            extras.addSlottedWidget(grid, subset);
            grid.setPosition(layout.x(), layout.y());
            // 用翻页按钮替代滚动条：滚动条本体已被 TmrvScrollBarWidgetMixin 藏掉（也不响应点击），
            // 这里在它原本占的那条竖条上挂翻页控件，点击后驱动同一个滚动条来跳页。
            var scrollBar = ((TmrvScrollGridWidgetAccessor) grid).emiScrollGridFixGetScrollBar();
            if (scrollBar != null) {
                // PageScroller 是 TmrvScrollBarWidgetMixin 在运行期加到 ScrollBarWidget 上的接口，编译器看不到，需显式转换
                var scroller = (PageScroller) (Object) scrollBar;
                var rows = layout.rows();
                // 页数按“一页能放多少格”算（列 × 行），不是按可见行数——否则页数会虚高，
                // 多出来的点击只会让页号继续涨、内容却早被滚动条 clamp 住了
                var capacity = layout.capacity();
                var pageCount = (subset.size() + capacity - 1) / capacity;
                // 与 TMRV 的滚动条同一条竖条：滚动格 setPosition 会把 rect 右移 1，宽度是 列数×18 + 滚动条宽(10)
                var stripX = layout.x() + 1 + layout.columns() * ScrollGridLayout.SLOT_SIZE;
                // 滚轮范围：整个槽位区域（含 TMRV 那 1px 右移），指针在格子任意处都能翻页
                var slotArea = new Bounds(layout.x(), layout.y(), 1 + layout.columns() * ScrollGridLayout.SLOT_SIZE, rows * ScrollGridLayout.SLOT_SIZE);
                this.widgets.add(new PageControlWidget(scroller, stripX, layout.y(), rows, pageCount, slotArea));
            }
        }
    }
}
