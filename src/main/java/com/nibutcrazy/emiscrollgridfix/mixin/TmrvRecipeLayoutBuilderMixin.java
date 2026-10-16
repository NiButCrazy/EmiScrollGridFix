package com.nibutcrazy.emiscrollgridfix.mixin;

import com.nibutcrazy.emiscrollgridfix.EmiScrollGridFix;
import com.nibutcrazy.emiscrollgridfix.RuntimeSlotBackground;
import com.nibutcrazy.emiscrollgridfix.ScrollGridLayout;
import com.nibutcrazy.emiscrollgridfix.ScrollGridRegistrations;
import dev.nolij.toomanyrecipeviewers.impl.jei.api.gui.builder.RecipeLayoutBuilder;
import dev.nolij.toomanyrecipeviewers.impl.jei.api.gui.builder.RecipeSlotBuilder;
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
 * TooManyRecipeViewers（TMRV）那条链路的对应修复。
 *
 * <p>TMRV 用自己的一套 JEI API 实现在没有 JEI 的情况下跑 JEI 插件，它的 {@code addSlotToWidget}
 * 与 JEMI 犯了同一个错：把控件工厂丢掉，直接返回一个没设过坐标的槽位
 * （{@code RecipeSlotBuilder} 的 rect 默认是 (0, 0)，见 TMRV 源码），于是滚动格的槽位同样会全堆在左上角。
 *
 * <pre>{@code
 * // dev.nolij.toomanyrecipeviewers.impl.jei.api.gui.builder.RecipeLayoutBuilder
 * public IRecipeSlotBuilder addSlotToWidget(RecipeIngredientRole role, ISlottedWidgetFactory<?> widgetFactory) {
 *     return addSlot(role);   // widgetFactory 被丢弃
 * }
 * }</pre>
 *
 * <p>TMRV 已经把 JEI 的 {@code ScrollGridWidgetFactory} 原样打包进来了，所以 {@link ScrollGridLayout}
 * 那套「读区域 + 反射读 columns」的逻辑在 TMRV 下无需改动。
 *
 * <p>要注意槽框：TMRV 的 {@code RecipeSlotBuilder} 里 {@code drawBack} 默认是 {@code false}
 * （它显式覆盖了 EMI {@code SlotWidget} 默认为 true 的行为，符合 JEI 语义——滚动格的槽框本该由控件自己画），
 * 所以这条链路上必须像 JEMI 那条一样主动 {@code setBackground} 补框，否则只会看到一个悬停高亮的空格子。
 *
 * <p>与 JEMI 版 mixin 互斥：不装 TMRV 时目标类不存在，Mixin 只记一条警告并跳过。
 * 目标类通过 {@code compileOnly} 的 TMRV 依赖（Modrinth Maven）参与编译，这样 Mixin 注解处理器能校验
 * 注入点；注解里的类引用不会触类加载，不存在 JEMI 版踩过的“目标类被提前加载”问题。
 */
@SuppressWarnings("removal") // JEI 15.20 起 ISlottedWidgetFactory 标记为 forRemoval，这里必须继续对旧 API 生效
@Mixin(value = RecipeLayoutBuilder.class, remap = false)
public abstract class TmrvRecipeLayoutBuilderMixin {
    /**
     * TMRV 的加槽方法（对应 JEI 15.20+ 的 {@code addSlot(RecipeIngredientRole)}，
     * 坐标由 {@code IPlaceable#setPosition} 设置）。
     */
    @Shadow
    public abstract IRecipeSlotBuilder addSlot(RecipeIngredientRole role);

    /**
     * 布局器里已创建的所有槽位（按创建顺序），用来记下我们这条工厂的槽位下标。
     */
    @Shadow
    public abstract java.util.List<RecipeSlotBuilder> getSlots();

    /**
     * 每个控件工厂已经分出去多少个格子，语义与 JemiRecipeLayoutBuilderMixin 中的一致。
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
            // 不是滚动格控件，或者拿不到它的区域：保持 TMRV 原本的行为
            return;
        }
        if (this.emiScrollGridFix$assignedCounts == null) {
            this.emiScrollGridFix$assignedCounts = new IdentityHashMap<>();
        }
        var index = this.emiScrollGridFix$assignedCounts.merge(widgetFactory, 1, Integer::sum) - 1;
        if (EmiScrollGridFix.LOG_LAYOUT_APPLIED_TMRV.compareAndSet(false, true)) {
            EmiScrollGridFix.LOGGER.info("Widget slots from a JEI scroll grid are now laid out in a fixed grid on EMI (TMRV bridge): origin ({}, {}), {} columns, {} visible rows", layout.x(), layout.y(), layout.columns(), layout.rows());
        }
        // 坐标语义与 JEI 一致：物品在 (x, y)，槽框画在 (x - 1, y - 1)
        var x = layout.x() + index % layout.columns() * ScrollGridLayout.SLOT_SIZE + 1;
        var y = layout.y() + index / layout.columns() * ScrollGridLayout.SLOT_SIZE + 1;
        var slot = this.addSlot(role).setPosition(x, y);
        // 记账：这条工厂一共占用了哪些槽位。等构建卡片控件时若发现超过一页容量，
        // 就交给 TMRV 自带的滚动格控件（带滚动条），否则保持现在这套固定坐标。
        ScrollGridRegistrations.record((RecipeLayoutBuilder) (Object) this, widgetFactory, layout, this.getSlots().size() - 1);
        // TMRV 的槽位默认 drawBack = false（符合 JEI 语义：滚动格的槽框本来由控件自己画），
        // 而这条链路上没有那个控件，所以这里主动补上槽框。mod 之后若自己调用 setBackground，
        // 会直接覆盖掉这个，不会两层叠画。
        var slotBackground = RuntimeSlotBackground.get();
        if (slotBackground != null) {
            slot.setBackground(slotBackground, -1, -1);
        }
        cir.setReturnValue(slot);
    }
}
