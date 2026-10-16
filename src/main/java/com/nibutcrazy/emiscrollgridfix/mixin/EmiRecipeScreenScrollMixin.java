package com.nibutcrazy.emiscrollgridfix.mixin;

import com.nibutcrazy.emiscrollgridfix.PageControlWidget;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.WidgetGroup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 把鼠标滚轮交给翻页控件。
 *
 * <p>EMI 1.1.24 的控件 API（{@code dev.emi.emi.api.widget.Widget}）只有 {@code mouseClicked}/{@code keyPressed}，
 * 没有滚轮出口，所以只能从配方界面接：{@code RecipeScreen.mouseScrolled} 里第一件事就是把滚轮转给
 * {@code EmiScreenManager}（用于滚动物品列表 / 翻配方），我们在它之前先问一遍我们的控件。
 *
 * <p>命中就 {@code setReturnValue(true)} 吞掉事件，避免同一格滚轮既翻页又翻配方。
 * 坐标要用 EMI 的组内相对坐标（和它自己的 {@code mouseClicked} 一致）。
 *
 * <p>注意：{@code mouseScrolled} 是 MC 的方法（生产环境叫 {@code m_6050_}），这里按开发名写、
 * 由 refmap 负责映射，所以这个注入**不能**加 {@code remap = false}（类本身是 mod 类，仍是 {@code remap = false}）。
 */
@Mixin(value = RecipeScreen.class, remap = false)
public abstract class EmiRecipeScreenScrollMixin {
    /**
     * 当前显示的配方控件组（EMI 自己的分组，坐标是屏幕绝对坐标）。
     */
    @Shadow
    @Final
    private List<WidgetGroup> currentPage;

    // 关键：类上写了 remap = false（因为 RecipeScreen 是 mod 类），但这个方法名是 MC 的，
    // 必须在这一处显式 remap = true，否则注解处理器不会把映射写进 refmap，运行期就找不到 m_6050_。
    @Inject(method = "mouseScrolled(DDD)Z", at = @At("HEAD"), cancellable = true, remap = true)
    private void emiScrollGridFix$wheelFlipsPages(double mouseX, double mouseY, double scrollDelta, CallbackInfoReturnable<Boolean> cir) {
        var mx = (int) mouseX;
        var my = (int) mouseY;
        for (var group : this.currentPage) {
            var ox = mx - group.x();
            var oy = my - group.y();
            for (var widget : group.widgets) {
                if (widget instanceof PageControlWidget control && control.mouseScrolled(ox, oy, scrollDelta)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
}
