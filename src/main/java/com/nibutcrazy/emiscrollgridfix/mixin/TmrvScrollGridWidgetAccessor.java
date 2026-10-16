package com.nibutcrazy.emiscrollgridfix.mixin;

import dev.nolij.toomanyrecipeviewers.impl.jei.api.gui.widgets.ScrollGridWidget;
import dev.nolij.toomanyrecipeviewers.impl.widget.ScrollBarWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 取出 TMRV 滚动格内部的滚动条实例（{@code private final @Nullable ScrollBarWidget scrollBar}）。
 *
 * <p>翻页控件需要它：滚动条被 {@code TmrvScrollBarWidgetMixin} 藏掉后不再显示，
 * 但它仍然握着"当前显示第几行"这个状态，也正是滚动格定位/裁剪槽位的依据。
 */
@Mixin(value = ScrollGridWidget.class, remap = false)
public interface TmrvScrollGridWidgetAccessor {
    @Accessor("scrollBar")
    ScrollBarWidget emiScrollGridFixGetScrollBar();
}
