package com.nibutcrazy.emiscrollgridfix;

/**
 * 由 mixin 挂到 {@code JemiRecipeSlot} 上：这个槽位属于哪条滚动格工厂、是第几个。
 *
 * <p>槽位控件（{@code JemiSlotWidget}）和 EMI 的 {@code SlotWidget#getBounds} 通过它来判断
 * 该不该显示、要不要按页位移。
 */
public interface PaginatedSlot {
    /**
     * @return 记账信息；该槽位不是滚动格工厂产出的、或记账已被回收时为 {@code null}
     */
    JemiPagination.SlotRef emiScrollGridFix$slotRef();
}
