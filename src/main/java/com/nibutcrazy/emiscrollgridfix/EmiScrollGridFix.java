package com.nibutcrazy.emiscrollgridfix;

import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 把 JEI 的 {@code IRecipeLayoutBuilder#addSlotToWidget}（滚动格控件）补成 EMI 也能正确渲染的版本。
 *
 * <p>EMI 内置的 JEMI（JEI 兼容层）里这个方法是 TODO 桩，会把交给控件工厂的槽位全部放到 (0, 0)，
 * 于是像 CrockPot 的猪灵以物易物这类用滚动格排布产出的类别，在 EMI 里所有物品都会挤在一个格子上。
 * 本模组在 JEMI 的 {@code JemiRecipeLayoutBuilder} 上注入该方法，改为按滚动格的实际位置摆放槽位。
 */
@Mod(EmiScrollGridFix.MOD_ID)
public class EmiScrollGridFix {
    public static final String MOD_ID = "emi_scroll_grid_fix";
    public static final Logger LOGGER = LoggerFactory.getLogger("EMI Scroll Grid Fix");

    /**
     * 只打一次"注入确实生效"的日志，用来排查补丁是否真的应用上了。
     * 两个桥各一个标志：JEMI（EMI 内置、需要 JEI）与 TMRV（不需要 JEI），同一时间只有一条链路在工作。
     */
    public static final AtomicBoolean LOG_LAYOUT_APPLIED = new AtomicBoolean();

    public static final AtomicBoolean LOG_LAYOUT_APPLIED_TMRV = new AtomicBoolean();

    public EmiScrollGridFix() {
        LOGGER.info("EMI Scroll Grid Fix loaded, JEI widget slots will be laid out in a fixed grid on EMI");
    }
}
