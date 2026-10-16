package com.nibutcrazy.emiscrollgridfix.mixin;

import com.nibutcrazy.emiscrollgridfix.JemiPagination;
import com.nibutcrazy.emiscrollgridfix.PaginatedSlot;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.jemi.impl.JemiRecipeSlot;
import dev.emi.emi.jemi.widget.JemiSlotWidget;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * JEMI 侧翻页的「渲染」部分：
 *
 * <ul>
 *   <li>不在当前页的槽位直接不画（{@code render} 里包含 JEI 槽框、物品、overlay 的整条链路）；</li>
 *   <li>JEI 槽框与 overlay 是按绝对坐标画的（{@code x + 1 + xOff}），翻页时要一起上移，
 *       所以把这两处 {@code IDrawable#draw(draw, x, y)} 调用重定向成带位移的版本
 *       （物品本身走 {@code getBounds()}，由 {@code EmiSlotWidgetMixin} 负责）；</li>
 *   <li>构造时把「该槽位属于哪条工厂」登记到配方上，供 {@code JemiRecipe.addWidgets} 末尾挂翻页控件。</li>
 * </ul>
 *
 * <p>{@code render} 是覆盖 MC {@code Renderable} 的方法，生产环境叫 {@code m_88315_}，
 * 所以这一处（以及它里面调用的重定向）用 {@code remap = true} 让 refmap 去映射；
 * EMI 自己的方法名（{@code drawOverlay} 等）保持 {@code remap = false}。
 */
@Mixin(value = JemiSlotWidget.class, remap = false)
public abstract class JemiSlotWidgetMixin {
    @Shadow
    @Final
    public JemiRecipeSlot slot;

    @Inject(method = "<init>(Ldev/emi/emi/jemi/impl/JemiRecipeSlot;Ldev/emi/emi/api/recipe/EmiRecipe;)V", at = @At("TAIL"), remap = false)
    private void emiScrollGridFix$registerForRecipe(JemiRecipeSlot slot, EmiRecipe recipe, CallbackInfo ci) {
        JemiPagination.registerForRecipe(recipe, slot);
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true, remap = true)
    private void emiScrollGridFix$hideOffPageSlots(GuiGraphics draw, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        var ref = emiScrollGridFix$slotRef();
        if (ref != null && JemiPagination.hidden(ref)) {
            ci.cancel();
        }
    }

    // 外层方法 render 是 MC 的（生产环境 m_88315_）所以要 remap；被重定向的调用是 JEI/EMI 的，不需要映射，
    // 于是在 @At 上单独关掉 remap，免得注解处理器去找一个根本不存在的映射（会出 "Unable to locate method mapping" 警告）
    @Redirect(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "INVOKE", target = "Lmezz/jei/api/gui/drawable/IDrawable;draw(Lnet/minecraft/client/gui/GuiGraphics;II)V", remap = false),
            remap = true
    )
    private void emiScrollGridFix$shiftBackground(IDrawable drawable, GuiGraphics draw, int x, int y) {
        drawable.draw(draw, x, y - emiScrollGridFix$shift());
    }

    @Redirect(
            method = "drawOverlay",
            at = @At(value = "INVOKE", target = "Lmezz/jei/api/gui/drawable/IDrawable;draw(Lnet/minecraft/client/gui/GuiGraphics;II)V"),
            remap = false
    )
    private void emiScrollGridFix$shiftOverlay(IDrawable drawable, GuiGraphics draw, int x, int y) {
        drawable.draw(draw, x, y - emiScrollGridFix$shift());
    }

    @Unique
    private JemiPagination.SlotRef emiScrollGridFix$slotRef() {
        return this.slot instanceof PaginatedSlot paginatedSlot ? paginatedSlot.emiScrollGridFix$slotRef() : null;
    }

    @Unique
    private int emiScrollGridFix$shift() {
        var ref = emiScrollGridFix$slotRef();
        return ref == null ? 0 : JemiPagination.shift(ref);
    }
}
