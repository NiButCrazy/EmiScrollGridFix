package com.nibutcrazy.emiscrollgridfix;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.common.Internal;

import java.lang.reflect.Method;

/**
 * 取 JEI 的槽位贴图（18×18）。
 *
 * <p>来源是 JEI 的 {@code Internal.getTextures()}：JEI 15.20 自己的滚动格控件就是这么取的
 * （{@code ScrollGridRecipeWidget} → {@code Internal.getTextures().getSlot()}），
 * TooManyRecipeViewers 也把同一份 JEI 内部类原样打包了，所以 JEI 与 TMRV 两条链路都能用这一个入口。
 *
 * <p>方法名两种都试：JEI 15.20 起是 {@code getSlot()}，更早的版本是 {@code getSlotDrawable()}；
 * 拿不到（JEI 运行时还没就绪、版本对不上等）就返回 {@code null}，调用方只摆坐标、不画槽框。
 */
public final class RuntimeSlotBackground {
    private static final String[] METHOD_NAMES = {"getSlotDrawable", "getSlot"};

    private static Method slotDrawableMethod;
    private static boolean resolved;

    private RuntimeSlotBackground() {
    }

    public static IDrawable get() {
        try {
            var textures = Internal.getTextures();
            if (textures == null) {
                return null;
            }
            var method = resolveSlotDrawableMethod(textures.getClass());
            return method == null ? null : (IDrawable) method.invoke(textures);
        } catch (Throwable t) {
            return null;
        }
    }

    private static synchronized Method resolveSlotDrawableMethod(Class<?> texturesClass) {
        if (!resolved) {
            for (var name : METHOD_NAMES) {
                try {
                    slotDrawableMethod = texturesClass.getMethod(name);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // 试下一个名字
                }
            }
            resolved = true;
        }
        return slotDrawableMethod;
    }
}
