package com.nibutcrazy.emiscrollgridfix.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.jemi.JemiRecipeHandler;
import dev.emi.emi.jemi.JemiUtil;
import dev.emi.emi.jemi.impl.JemiRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 修正「缺失材料」红色高亮全部叠在配方左上角的问题。
 *
 * <p>这个问题只在精致存储（Refined Storage）的合成终端里能看到，原因是：
 *
 * <ol>
 *   <li>RS 把 {@code GridRecipeTransferHandler} 注册成 JEI 的 <b>universal</b> 配方转移处理器，
 *       于是它的合成终端对<b>任何</b>配方类别都算「可填充」；</li>
 *   <li>TMRV 通过 {@code EmiRecipeFiller.extraHandlers} 把 JEI 的转移处理器包成 EMI 的
 *       {@link JemiRecipeHandler}（见 TMRV 的 {@code EMIPlugin#registerRecipeTransferHandlers}）；</li>
 *   <li>悬停「填充配方」按钮时，{@code JemiRecipeHandler#render} 会用 {@code createSlotsView}
 *       重建一份 JEI 槽位视图，靠 <b>对象同一性</b>（{@code w.getStack() == ing}）从 EMI 的
 *       {@link SlotWidget} 反推每个槽位的坐标；</li>
 *   <li>而 TMRV 的 {@code TMRVRecipe#getInputs()} 每次都重新 {@code new RecipeLayoutBuilder}
 *       提取一遍原料，得到的 {@code EmiIngredient} 与槽位控件上的实例永远不是同一个对象，
 *       于是所有槽位都被定位到 (0, 0)；</li>
 *   <li>接着 RS 把缺料标红（{@code Color(1, 0, 0, 0.4f)}），EMI 按 {@code JemiRecipeSlot.x/y}
 *       涂色，结果所有红色高亮叠在配方左上角，颜色还因为反复叠加而接近纯红。</li>
 * </ol>
 *
 * <p>普通工作台没有注册 universal 转移处理器，{@code getFirstValidHandler} 返回 null，
 * 走的是 EMI 自己的 {@code StandardRecipeHandler#renderMissing}（直接用控件 bounds），
 * 所以那边一直是对的。
 *
 * <p>修复方式：同一性匹配失败时，再按内容（{@link EmiIngredient#areEqual}）匹配，
 * 并把匹配到的控件从候选里移除，保证重复材料（同一种物品占多个格子）也能按顺序一一对应。
 * 同一性匹配优先，因此 JEMI 原本能正常工作的路径行为不变。
 */
@Mixin(value = JemiRecipeHandler.class, remap = false)
public abstract class JemiRecipeHandlerMixin {
    @Inject(
            method = "addIngredients(Ldev/emi/emi/jemi/impl/JemiRecipeLayoutBuilder;Ljava/util/List;Ljava/util/List;Lmezz/jei/api/recipe/RecipeIngredientRole;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void emiScrollGridFix$matchByValue(JemiRecipeLayoutBuilder builder,
                                                      List<SlotWidget> widgets,
                                                      List<? extends EmiIngredient> stacks,
                                                      RecipeIngredientRole role,
                                                      CallbackInfo ci) {
        ci.cancel();
        // 每个控件的原料只算一次：TMRV 的 getStack() 每次都会重新拼一遍 EmiIngredient，
        // 而这个方法在悬停「填充配方」按钮时每帧都会跑
        var available = new ArrayList<>(widgets);
        var availableStacks = new ArrayList<EmiIngredient>(widgets.size());
        for (var widget : widgets) {
            availableStacks.add(widget.getStack());
        }
        for (var ingredient : stacks) {
            // 先按 EMI 原本的逻辑找同一实例（JEMI 正常路径走这里，行为保持不变）
            int matchIndex = -1;
            for (int i = 0; i < available.size(); i++) {
                if (availableStacks.get(i) == ingredient) {
                    matchIndex = i;
                }
            }
            if (matchIndex < 0) {
                // TMRV 会重建原料对象，同一性永远不成立：退化为按内容匹配，
                // 匹配到的控件从候选里移除，重复材料（同一物品占多个格子）也能逐个对应
                for (int i = 0; i < available.size(); i++) {
                    var candidate = availableStacks.get(i);
                    if (candidate != null && EmiIngredient.areEqual(candidate, ingredient)) {
                        matchIndex = i;
                        break;
                    }
                }
            }
            int x = 0;
            int y = 0;
            if (matchIndex >= 0) {
                var match = available.remove(matchIndex);
                availableStacks.remove(matchIndex);
                var bounds = match.getBounds();
                x = bounds.x();
                y = bounds.y();
            }
            var acceptor = builder.addSlot(role, x, y);
            for (var stack : ingredient.getEmiStacks()) {
                Optional<ITypedIngredient<?>> typed = JemiUtil.getTyped(stack);
                if (typed.isPresent()) {
                    emiScrollGridFix$addIngredient(acceptor, typed.get());
                }
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void emiScrollGridFix$addIngredient(IIngredientAcceptor acceptor, ITypedIngredient<?> typed) {
        acceptor.addIngredient((IIngredientType) typed.getType(), typed.getIngredient());
    }
}
