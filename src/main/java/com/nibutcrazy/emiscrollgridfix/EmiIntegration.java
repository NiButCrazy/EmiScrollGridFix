package com.nibutcrazy.emiscrollgridfix;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

/**
 * EMI 侧的清理：隐藏 JEI 自带的「标签页」类别。
 *
 * <p>JEI 15.20 起内部插件会为每个注册表建一个标签信息类别，uid 形如
 * {@code minecraft:tag_recipes/item}（见 {@code JeiInternalPlugin}：
 * {@code RecipeType.create(registryLocation.getNamespace(), "tag_recipes/" + registryLocation.getPath(), ...)}）。
 * 而 EMI 自己就有物品标签信息，两套并存时 EMI 的配方界面会多出一串重复的标签页。
 *
 * <p>做法是按 EMI 官方姿势移除配方：EMI 配方界面的标签页是用「有配方的类别」推出来的
 * （{@code EmiApi.mapRecipes}），一个配方都不剩的类别不会出现；{@code removeRecipes} 的谓词对
 * 「当前与将来」的配方都生效，所以不依赖插件加载顺序。
 *
 * <p>两条兼容链路创建的类别 id 都是 JEI 的 {@code RecipeType} uid
 * （JEMI 的 {@code JemiCategory} 与 TMRV 的 {@code TMRVCategory} 都是
 * {@code super(jeiCategory.getRecipeType().getUid(), ...)}），因此这一个谓词同时覆盖它们。
 *
 * <p>注：Forge 上 EMI 靠扫描 {@code @EmiEntrypoint} 注解发现插件（见 {@code EmiAgnosForge#getPluginsAgnos}），
 * 这个类的无参构造会被反射调用，别加带参构造。
 */
@EmiEntrypoint
public class EmiIntegration implements EmiPlugin {
    /**
     * JEI 给标签页类别取的 uid 路径前缀。
     */
    private static final String JEI_TAG_CATEGORY_PATH_PREFIX = "tag_recipes/";

    @Override
    public void register(EmiRegistry registry) {
        registry.removeRecipes(recipe -> {
            var category = recipe.getCategory();
            return category != null && category.getId().getPath().startsWith(JEI_TAG_CATEGORY_PATH_PREFIX);
        });
        EmiScrollGridFix.LOGGER.info("Hiding JEI's built-in tag categories from EMI (category uid path '{}')", JEI_TAG_CATEGORY_PATH_PREFIX);
    }
}
