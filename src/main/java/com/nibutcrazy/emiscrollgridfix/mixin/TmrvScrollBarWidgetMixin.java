package com.nibutcrazy.emiscrollgridfix.mixin;

import com.nibutcrazy.emiscrollgridfix.PageScroller;
import dev.nolij.toomanyrecipeviewers.impl.widget.ScrollBarWidget;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把 TMRV 的滚动条降级成"翻页状态载体"：
 *
 * <ul>
 *   <li>取消 {@code render}：界面上不再画滚动条（翻页改由 {@code PageControlWidget} 的按钮完成）；</li>
 *   <li>取消 {@code mouseClicked}：藏起来的滚动条不该再响应点击；</li>
 *   <li>实现 {@link PageScroller}：把私有的 {@code scroll(int)}（语义是<b>绝对值</b>，内部会 clamp 到
 *       {@code [0, maxScroll]}）暴露成"跳到第 N 页"。</li>
 * </ul>
 *
 * 这样 TMRV 的滚动格依旧按滚动条的位置做定位与可见性裁剪，只是由我们的按钮来驱动它。
 */
@Mixin(value = ScrollBarWidget.class, remap = false)
public abstract class TmrvScrollBarWidgetMixin implements PageScroller {
    /**
     * 注意：参数是<b>绝对位置</b>（行偏移）而非增量，且内部会 clamp 到 {@code [0, maxScroll]}
     * （字节码里是 {@code Mth.clamp(参数, 0, maxScroll)} 后赋值）——越界会被自动收住，
     * 最后一页因此会贴到内容末尾，而不是留出空白。
     */
    @Shadow
    private boolean scroll(int position) {
        throw new AssertionError();
    }

    @Override
    public void emiScrollGridFixGoToPage(int page, int rowsPerPage) {
        this.scroll(Math.max(0, page) * Math.max(1, rowsPerPage));
    }

    @Inject(method = "m_88315_", at = @At("HEAD"), cancellable = true, remap = false)
    private void emiScrollGridFix$hideScrollBar(GuiGraphics draw, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void emiScrollGridFix$ignoreClicks(int mouseX, int mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
