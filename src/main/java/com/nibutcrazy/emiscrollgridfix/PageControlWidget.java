package com.nibutcrazy.emiscrollgridfix;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 翻页控件：占着原来滚动条那条竖条（宽 10、高 = 可见行数 × 18），顶部是上一页、底部是下一页。
 *
 * <p>为什么不用 EMI 的 {@code ButtonWidget} + {@code buttons.png} 里的箭头：
 * 那套按钮假定贴图有三态（正常 / 悬停 / 禁用，禁用时 v 偏移 {@code height * 2}），
 * 而数字微调用的那对上下箭头在贴图里只有正常与悬停两态（EMI 自己的 {@code IntEdit} 里它们永远可用），
 * 于是"上一页"在第 1 页被判定为禁用时，会画到空像素上——按钮透明但仍可点。这里自己画三角，三态都看得见。
 *
 * <p>页号由本类自己记（不能从滚动条反推：它的单位是行、而且是连续滚动模型、还会 clamp，
 * 最后一页的滚动量与"页号 × 行数"并不相等）；滚动量只作为输出交给 {@link PageScroller}。
 */
public class PageControlWidget extends Widget {
    private static final int WIDTH = 10;
    private static final int BUTTON_HEIGHT = 10;
    private static final int ROW_HEIGHT = 18;
    private static final int ENABLED_COLOR = 0xFF404040;
    private static final int HOVER_COLOR = 0xFF6A6A6A;
    private static final int DISABLED_COLOR = 0xFFB0B0B0;

    private final PageScroller scroller;
    private final int x;
    private final int y;
    private final int rowsPerPage;
    private final int pageCount;

    private int page;

    public PageControlWidget(PageScroller scroller, int x, int y, int rowsPerPage, int pageCount) {
        this.scroller = scroller;
        this.x = x;
        this.y = y;
        this.rowsPerPage = Math.max(1, rowsPerPage);
        this.pageCount = Math.max(1, pageCount);
    }

    @Override
    public Bounds getBounds() {
        return new Bounds(x, y, WIDTH, rowsPerPage * ROW_HEIGHT);
    }

    @Override
    public void render(GuiGraphics draw, int mouseX, int mouseY, float delta) {
        drawArrow(draw, y, true, page > 0, hovered(mouseX, mouseY, y));
        var nextY = y + getBounds().height() - BUTTON_HEIGHT;
        drawArrow(draw, nextY, false, page < pageCount - 1, hovered(mouseX, mouseY, nextY));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!getBounds().contains(mouseX, mouseY)) {
            return false;
        }
        if (hovered(mouseX, mouseY, y)) {
            return goTo(page - 1);
        }
        if (hovered(mouseX, mouseY, y + getBounds().height() - BUTTON_HEIGHT)) {
            return goTo(page + 1);
        }
        return false;
    }

    private boolean goTo(int target) {
        if (target < 0 || target > pageCount - 1) {
            return false;
        }
        this.page = target;
        scroller.emiScrollGridFixGoToPage(target, rowsPerPage);
        return true;
    }

    private boolean hovered(int mouseX, int mouseY, int buttonY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT;
    }

    /**
     * 画一个 4 行的实心三角（宽 1、3、5、7），上下用行序镜像。
     */
    private void drawArrow(GuiGraphics draw, int buttonY, boolean up, boolean enabled, boolean hovered) {
        var color = !enabled ? DISABLED_COLOR : hovered ? HOVER_COLOR : ENABLED_COLOR;
        for (var row = 0; row < 4; row++) {
            var width = 1 + row * 2;
            var rowY = up ? buttonY + 3 + row : buttonY + 3 + (3 - row);
            var rowX = x + (WIDTH - width) / 2;
            draw.fill(rowX, rowY, rowX + width, rowY + 1, color);
        }
    }
}
