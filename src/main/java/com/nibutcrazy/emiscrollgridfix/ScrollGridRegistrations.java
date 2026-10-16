package com.nibutcrazy.emiscrollgridfix;

import dev.nolij.toomanyrecipeviewers.impl.jei.api.gui.builder.RecipeLayoutBuilder;
import dev.nolij.toomanyrecipeviewers.impl.jei.api.gui.builder.RecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.widgets.ISlottedWidgetFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 记账：某个布局器上、某个滚动格控件工厂一共创建了哪些槽位。
 *
 * <p>用在 TMRV 侧：布局器填槽阶段（{@code addSlotToWidget}）记录「工厂 → 布局 + 槽位下标」，
 * 等到构建卡片控件时（{@code TMRVRecipeWidget.addSlotWidgets} 末尾）才知道总数，若超过一页容量，
 * 就把这些槽位交给 TMRV 自带的滚动格控件（带滚动条、会裁掉不可见行）。
 *
 * <p>键是布局器实例、弱引用；构建完控件就取走（{@link #take}）。只记录下标、不持有槽位对象，
 * 免得把 TMRV 的内部对象留成活引用。
 */
@SuppressWarnings("removal") // JEI 15.20 起 ISlottedWidgetFactory 标记为 forRemoval，这里必须继续对旧 API 生效
public final class ScrollGridRegistrations {
    private static final Map<RecipeLayoutBuilder, Map<ISlottedWidgetFactory<?>, FactorySlots>> REGISTRATIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ScrollGridRegistrations() {
    }

    public static void record(RecipeLayoutBuilder layoutBuilder, ISlottedWidgetFactory<?> widgetFactory, ScrollGridLayout layout, int slotIndex) {
        synchronized (REGISTRATIONS) {
            REGISTRATIONS
                    .computeIfAbsent(layoutBuilder, ignored -> new IdentityHashMap<>())
                    .computeIfAbsent(widgetFactory, ignored -> new FactorySlots(layout))
                    .slotIndices()
                    .add(slotIndex);
        }
    }

    /**
     * 取出并移除该布局器的全部记账（构建控件时调用一次即可）。
     */
    public static Map<ISlottedWidgetFactory<?>, FactorySlots> take(RecipeLayoutBuilder layoutBuilder) {
        synchronized (REGISTRATIONS) {
            return REGISTRATIONS.remove(layoutBuilder);
        }
    }

    /**
     * 把「槽位下标」映射成 TMRV 已经构建好的槽位控件。
     *
     * <p>TMRV 只把可见槽位建成控件、且顺序与布局器里的先后一致（见 {@code TMRVRecipeWidget.addSlotWidgets}），
     * 所以这里数一遍可见槽位就能对上号。
     */
    public static List<IRecipeSlotDrawable> drawablesAt(List<RecipeSlotBuilder> allSlots, List<IRecipeSlotDrawable> drawables, List<Integer> slotIndices) {
        var wanted = new HashSet<>(slotIndices);
        var result = new ArrayList<IRecipeSlotDrawable>(slotIndices.size());
        var visibleIndex = 0;
        for (var index = 0; index < allSlots.size(); index++) {
            if (!allSlots.get(index).isVisible()) {
                continue;
            }
            if (wanted.contains(index) && visibleIndex < drawables.size()) {
                result.add(drawables.get(visibleIndex));
            }
            visibleIndex++;
        }
        return result;
    }

    /**
     * @param layout      该工厂的滚动格布局
     * @param slotIndices 该工厂创建的槽位在布局器槽位列表中的下标（按创建顺序）
     */
    public record FactorySlots(ScrollGridLayout layout, List<Integer> slotIndices) {
        public FactorySlots(ScrollGridLayout layout) {
            this(layout, new ArrayList<>());
        }

        public boolean overflows() {
            return slotIndices.size() > layout.capacity();
        }
    }
}
