package com.nibutcrazy.emiscrollgridfix;

import mezz.jei.api.gui.widgets.IScrollGridWidgetFactory;
import mezz.jei.api.gui.widgets.ISlottedWidgetFactory;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/**
 * 从 JEI 的滚动格控件工厂里读出它打算把格子摆在哪。
 *
 * <p>JEI 的 {@code IScrollGridWidgetFactory} 只公开了 {@link IScrollGridWidgetFactory#getArea()}：
 * 区域宽度 = 列数 × 18 + 滚动条宽度，高度 = 可见行数 × 18。列数本身是 JEI 实现里的私有字段，
 * 所以先反射读 {@code columns}，读不到时再按区域宽度反推。
 */
@SuppressWarnings("removal") // 同 JemiRecipeLayoutBuilderMixin：JEI 15.20 起该 API 标记为 forRemoval
public record ScrollGridLayout(int x, int y, int columns, int rows) {
    /**
     * JEI 槽位贴图是 18×18（16×16 物品 + 边框），物品坐标比格子原点大 1。
     */
    public static final int SLOT_SIZE = 18;

    /**
     * 物品相对格点（槽框左上角）的偏移：JEI 的约定是 <b>1</b>——槽框画在格点上、物品在格点 +1。
     * 两个桥的非溢出路径都用它摆位，所以想整体微调槽位（比如贴图与这个约定差一像素）改这里一处即可：
     * 改成 0 就是物品与格点对齐（整体上移/左移 1px）。
     */
    public static final int SLOT_ITEM_OFFSET = 0;

    /**
     * JEI 的 {@code AbstractScrollWidget.getScrollBoxScrollbarExtraWidth()}：
     * SCROLLBAR_WIDTH(14) + SCROLLBAR_PADDING(2)。
     */
    private static final int SCROLLBAR_EXTRA_WIDTH = 16;

    private static final String COLUMNS_FIELD = "columns";

    /**
     * 一页能容纳的槽位数量。
     */
    public int capacity() {
        return columns * rows;
    }

    /**
     * @return 布局信息；如果不是滚动格控件、或者拿不到有效区域，返回 {@code null}（调用方保持 JEMI 原本的行为）
     */
    public static ScrollGridLayout of(ISlottedWidgetFactory<?> widgetFactory) {
        if (!(widgetFactory instanceof IScrollGridWidgetFactory<?> scrollGridFactory)) {
            return null;
        }
        final ScreenRectangle area;
        try {
            area = scrollGridFactory.getArea();
        } catch (Throwable t) {
            return null;
        }
        if (area == null || area.width() < SLOT_SIZE || area.height() < SLOT_SIZE) {
            return null;
        }
        var rows = Math.max(1, area.height() / SLOT_SIZE);
        var columns = readIntField(widgetFactory, COLUMNS_FIELD);
        if (columns <= 0) {
            columns = Math.max(1, (area.width() - SCROLLBAR_EXTRA_WIDTH) / SLOT_SIZE);
        }
        return new ScrollGridLayout(area.left(), area.top(), columns, rows);
    }

    private static int readIntField(Object owner, String fieldName) {
        for (var type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getInt(owner);
            } catch (NoSuchFieldException ignored) {
                // 继续往父类找
            } catch (Throwable t) {
                return -1;
            }
        }
        return -1;
    }
}
