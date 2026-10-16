package com.nibutcrazy.emiscrollgridfix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JEMI 侧的翻页记账。
 *
 * <p>JEMI 没有 TMRV 那种"滚动条驱动槽位"的机制，槽位坐标在构造时就定死了
 * （{@code JemiRecipeSlot.x/y} 与 EMI {@code SlotWidget} 的 x/y 都是 final），所以这里记下
 * 「这条滚动格工厂在哪个区域、有多少格、当前第几页」，再让槽位控件在渲染/取边界时按页做位移或隐藏。
 *
 * <p>键都是实例同一性（布局器 / 槽位构建器 / 配方都没重写 equals），且用弱引用，不会把 EMI 的对象留成活引用。
 */
public final class JemiPagination {
    /**
     * 布局器 → （控件工厂 → 这条工厂的记账）。布局器是 {@code JemiRecipeLayoutBuilder}。
     */
    private static final Map<Object, Map<Object, Entry>> ENTRIES = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * 槽位构建器 → 它属于哪条工厂、是第几个。槽位构建器是 {@code JemiRecipeSlotBuilder}。
     */
    private static final Map<Object, SlotRef> SLOTS = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * 配方 → 该配方上所有需要翻页的工厂。键是 {@code JemiRecipe}。
     */
    private static final Map<Object, List<Entry>> BY_RECIPE = Collections.synchronizedMap(new WeakHashMap<>());

    private JemiPagination() {
    }

    /**
     * 拿到（或创建）某个布局器上某条控件工厂的记账。
     */
    public static Entry entryFor(Object layoutBuilder, Object widgetFactory, ScrollGridLayout layout) {
        synchronized (ENTRIES) {
            return ENTRIES
                    .computeIfAbsent(layoutBuilder, ignored -> new IdentityHashMap<>())
                    .computeIfAbsent(widgetFactory, ignored -> new Entry(layout));
        }
    }

    public static void recordSlot(Object slotBuilder, Entry entry, int index) {
        SLOTS.put(slotBuilder, new SlotRef(entry, index));
    }

    /**
     * @return 该槽位构建器对应的记账；不属于任何滚动格工厂时为 {@code null}
     */
    public static SlotRef slotRef(Object slotBuilder) {
        return SLOTS.get(slotBuilder);
    }

    public static void registerForRecipe(Object recipe, Object slot) {
        if (!(slot instanceof PaginatedSlot paginatedSlot)) {
            return;
        }
        var ref = paginatedSlot.emiScrollGridFix$slotRef();
        if (ref == null) {
            return;
        }
        synchronized (BY_RECIPE) {
            var entries = BY_RECIPE.computeIfAbsent(recipe, ignored -> new ArrayList<>());
            if (!entries.contains(ref.entry())) {
                entries.add(ref.entry());
            }
        }
    }

    public static List<Entry> entriesForRecipe(Object recipe) {
        synchronized (BY_RECIPE) {
            var entries = BY_RECIPE.get(recipe);
            return entries == null ? List.of() : List.copyOf(entries);
        }
    }

    public static void clearRecipe(Object recipe) {
        BY_RECIPE.remove(recipe);
    }

    /**
     * 某个槽位当前是否被翻页藏起来（不在当前页的行窗口内）。
     */
    public static boolean hidden(SlotRef ref) {
        return rowOffset(ref) < 0 || rowOffset(ref) >= ref.entry().layout().rows();
    }

    /**
     * 某个槽位相对原位需要向上挪动的像素数（同一页内所有槽位相同）。
     */
    public static int shift(SlotRef ref) {
        return ref.entry().page() * ref.entry().layout().rows() * ScrollGridLayout.SLOT_SIZE;
    }

    /**
     * 该槽位在网格里的行号减去当前页的起始行：落在 [0, 可见行数) 才该显示。
     */
    private static int rowOffset(SlotRef ref) {
        var layout = ref.entry().layout();
        var row = ref.index() / Math.max(1, layout.columns());
        return row - ref.entry().page() * layout.rows();
    }

    public record SlotRef(Entry entry, int index) {
    }

    /**
     * 一条滚动格工厂的翻页状态：区域、已创建的槽位数、当前页。
     *
     * <p>同时就是 {@link PageScroller}——翻页控件点/滚之后调用
     * {@link #emiScrollGridFixGoToPage(int, int)} 把页码写到这里，槽位控件再去读，
     * 所以两边不会各记一份。
     */
    public static final class Entry implements PageScroller {
        private final ScrollGridLayout layout;
        private final AtomicInteger count = new AtomicInteger();

        private volatile int page;

        private Entry(ScrollGridLayout layout) {
            this.layout = layout;
        }

        public void countSlot() {
            count.incrementAndGet();
        }

        public int count() {
            return count.get();
        }

        public ScrollGridLayout layout() {
            return layout;
        }

        public int page() {
            return page;
        }

        /**
         * 是否超过一页能容纳的格子数（没超过就完全不需要翻页控件）。
         */
        public boolean overflows() {
            return count.get() > layout.capacity();
        }

        @Override
        public void emiScrollGridFixGoToPage(int page, int rowsPerPage) {
            this.page = Math.max(0, page);
        }
    }
}
