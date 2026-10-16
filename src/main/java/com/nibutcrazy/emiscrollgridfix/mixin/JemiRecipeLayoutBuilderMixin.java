package com.nibutcrazy.emiscrollgridfix.mixin;

import com.nibutcrazy.emiscrollgridfix.EmiScrollGridFix;
import com.nibutcrazy.emiscrollgridfix.JemiPagination;
import com.nibutcrazy.emiscrollgridfix.RuntimeSlotBackground;
import com.nibutcrazy.emiscrollgridfix.ScrollGridLayout;
import dev.emi.emi.jemi.impl.JemiRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.widgets.ISlottedWidgetFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * EMI 的 JEMI 里 {@code addSlotToWidget} 目前是 TODO 桩：
 *
 * <pre>{@code
 * public IRecipeSlotBuilder addSlotToWidget(RecipeIngredientRole role, ISlottedWidgetFactory<?> widgetFactory) {
 *     // TODO actually implement this factory method of adding slots
 *     return addSlot(role, 0, 0);
 * }
 * }</pre>
 *
 * <p>结果是所有交给控件工厂的槽位都落在 (0, 0)，用滚动格排布产出的配方（例如 CrockPot 的猪灵以物易物）
 * 在 EMI 里会把所有物品叠在同一个格子上。这里把该方法补成按控件工厂给出的区域摆放槽位。
 *
 * <p><b>绝不能在 mixin 应用前加载目标类（例如在 MixinConfigPlugin 里用 {@code Class.forName} 去探测它）。</b>
 * Mixin 只能转换还没被加载的类：一旦目标类在 mixin 应用前就被加载，本模组的注入和 EMI 自己的
 * GlobalMixin 都会静默失效。注解里的 {@code value = JemiRecipeLayoutBuilder.class} 只是常量池里的
 * 注解值，不会触类加载；EMI 缺失时 Mixin 也只会记一条 "Error loading class" 警告并跳过。
 *
 * <p>注意：本注入只影响 EMI 的 JEMI 路径，JEI 自身的渲染不受影响。
 */
@SuppressWarnings("removal") // JEI 15.20 起 ISlottedWidgetFactory 标记为 forRemoval，这里必须继续对旧 API 生效
@Mixin(value = JemiRecipeLayoutBuilder.class, remap = false)
public abstract class JemiRecipeLayoutBuilderMixin {
    /**
     * JEMI 自己实现的加槽方法，坐标语义与 JEI 一致：物品在 (x, y)，槽框在 (x - 1, y - 1)。
     */
    @Shadow
    public abstract IRecipeSlotBuilder addSlot(RecipeIngredientRole role, int x, int y);

    /**
     * 每个控件工厂已经分出去多少个格子。用 {@link IdentityHashMap} 是因为同一个类别通常只持有一个
     * 工厂实例，而不同工厂（一个配方里可能有多个滚动格）要各算各的。
     */
    @Unique
    private Map<ISlottedWidgetFactory<?>, Integer> emiScrollGridFix$assignedCounts;

    @Inject(
            method = "addSlotToWidget(Lmezz/jei/api/recipe/RecipeIngredientRole;Lmezz/jei/api/gui/widgets/ISlottedWidgetFactory;)Lmezz/jei/api/gui/builder/IRecipeSlotBuilder;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void emiScrollGridFix$layoutSlotInGrid(RecipeIngredientRole role, ISlottedWidgetFactory<?> widgetFactory, CallbackInfoReturnable<IRecipeSlotBuilder> cir) {
        var layout = ScrollGridLayout.of(widgetFactory);
        if (layout == null) {
            // 不是滚动格控件，或者拿不到它的区域：保持 JEMI 原本的 (0, 0) 行为
            return;
        }
        if (this.emiScrollGridFix$assignedCounts == null) {
            this.emiScrollGridFix$assignedCounts = new IdentityHashMap<>();
        }
        var index = this.emiScrollGridFix$assignedCounts.merge(widgetFactory, 1, Integer::sum) - 1;
        if (EmiScrollGridFix.LOG_LAYOUT_APPLIED.compareAndSet(false, true)) {
            EmiScrollGridFix.LOGGER.info("Widget slots from a JEI scroll grid are now laid out in a fixed grid on EMI: origin ({}, {}), {} columns, {} visible rows", layout.x(), layout.y(), layout.columns(), layout.rows());
        }
        // 超出一页能容纳的格子时顺序往下排，EMI 里没有滚动条，至少保证物品不丢失、不重叠
        var x = layout.x() + index % layout.columns() * ScrollGridLayout.SLOT_SIZE + ScrollGridLayout.SLOT_ITEM_OFFSET + 1;
        var y = layout.y() + index / layout.columns() * ScrollGridLayout.SLOT_SIZE + ScrollGridLayout.SLOT_ITEM_OFFSET;
        var slot = this.addSlot(role, x, y);
        // 记账：翻页时要知道这条工厂占了多少格、每个槽位是第几个（index 与坐标用的是同一个按工厂计的序号）
        var entry = JemiPagination.entryFor((Object) (Object) this, widgetFactory, layout);
        entry.countSlot();
        JemiPagination.recordSlot(slot, entry, index);
        // 滚动格在 JEI 里由控件自己画槽框，JEMI 没有对应控件，所以这里给槽位补上标准槽框。
        // 用 setBackground(…) 而不是 JEI 15.20 才有的 setStandardSlotBackground()，
        // 好处是兼容更早的 JEI，且 mod 之后自己设背景时会直接覆盖掉这个，不会两层叠画。
        var slotBackground = RuntimeSlotBackground.get();
        if (slotBackground != null) {
            slot.setBackground(slotBackground, -1, -1);
        }
        cir.setReturnValue(slot);
    }
}
