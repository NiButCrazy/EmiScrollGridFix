package com.nibutcrazy.emiscrollgridfix;

/**
 * 让 {@link PageControlWidget} 能驱动 TMRV 的滚动条（通过 mixin 挂到 {@code ScrollBarWidget} 上）。
 *
 * <p>滚动条本体在界面上会被藏掉，只当"当前显示哪一段"的载体——TMRV 的滚动格靠它决定哪些槽位可见、
 * 摆在哪，复用它比自己维护一套槽位定位与裁剪省事得多。
 *
 * <p>注意它的单位是<b>行</b>、而且是连续滚动模型（内部会把值 clamp 到 {@code [0, 总行数 - 可见行数]}），
 * 所以页号不能从它反推，由 {@link PageControlWidget} 自己记；这里只负责"把滚动位置挪到第 N 页该在的地方"。
 */
public interface PageScroller {
    /**
     * 把滚动位置挪到第 {@code page} 页（从 0 开始）对应的行偏移。越界由滚动条自己 clamp。
     */
    void emiScrollGridFixGoToPage(int page, int rowsPerPage);
}
