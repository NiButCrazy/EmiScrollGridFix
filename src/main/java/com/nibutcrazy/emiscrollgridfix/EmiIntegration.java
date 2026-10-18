package com.nibutcrazy.emiscrollgridfix;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EMI 侧的清理：隐藏 JEI 自带的「标签页」类别，并把这件事的日志讲清楚。
 *
 * <p>JEI 15.20 起内部插件会为每个注册表建一个标签信息类别，uid 形如
 * {@code minecraft:tag_recipes/item}（见 {@code JeiInternalPlugin}：
 * {@code RecipeType.create(registryLocation.getNamespace(), "tag_recipes/" + registryLocation.getPath(), ...)}）。
 * 而 EMI 自己就有物品标签信息，两套并存时 EMI 的配方界面会多出一串重复的标签页。
 *
 * <p>做法是按 EMI 官方姿势移除配方：EMI 配方界面的标签页是用「有配方的类别」推出来的
 * （{@code EmiApi.mapRecipes}），一个配方都不剩的类别不会出现；{@code removeRecipes} 的谓词对
 * 「当前与将来」的配方都生效，所以不依赖插件加载顺序。两条兼容链路创建的类别 id 都是 JEI 的
 * {@code RecipeType} uid（JEMI 的 {@code JemiCategory} 与 TMRV 的 {@code TMRVCategory} 都是
 * {@code super(jeiCategory.getRecipeType().getUid(), ...)}），因此一个谓词同时覆盖它们。
 *
 * <p>日志刻意分成三类，避免在 TMRV 局里把「计划」误读成「已经隐藏了 JEI 的东西」：
 * <ul>
 *   <li><b>armed</b>：每次 EMI 重载注册插件时一行，带重载序号与各链路版本（JEI/JEMI、TMRV 是否在场）；</li>
 *   <li><b>matched</b>：本次重载里第一次匹配到某个标签类别时一行，说明实际命中了哪些类别；</li>
 *   <li><b>summary</b>：上一次重载隐藏了多少条配方、涉及哪些类别——谓词要到 {@code EmiRecipes.bake()}
 *       才执行，本次注册时还拿不到本次结果，所以在下次注册时补报上一次的。</li>
 * </ul>
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
    private static final String LOG_PREFIX = "[tag-hide]";

    private static final AtomicInteger RELOAD_COUNTER = new AtomicInteger();

    // 本次重载的进度（谓词在 EMI 的 bake 阶段跑，可能在其它线程上）
    private static volatile int currentRemoved;
    private static final Set<ResourceLocation> CURRENT_CATEGORIES = ConcurrentHashMap.newKeySet();

    // 上一次重载的结果（供下一次注册时补报）
    private static volatile int lastRemoved;
    private static volatile Set<ResourceLocation> lastCategories = Set.of();

    @Override
    public void register(EmiRegistry registry) {
        var reload = RELOAD_COUNTER.incrementAndGet();

        // 上一次重载的结果总是先补报（同一 JVM 里链路不会变，不会报错对象）
        if (reload > 1 && !lastCategories.isEmpty()) {
            EmiScrollGridFix.LOGGER.info("{} reload #{}: previous reload hid {} recipes across {} JEI tag categories {}",
                    LOG_PREFIX, reload, lastRemoved, lastCategories.size(), lastCategories);
        }

        // 先看 TMRV：它与 JEI 互斥，且会注册一个“假 jei”来满足插件依赖（日志里看到的 jei 版本是它自带的仿冒品）。
        // TMRV 自己就会把这些标签页藏掉，不需要我们再动手。
        var tmrv = versionOf("toomanyrecipeviewers");
        var jei = versionOf("jei");
        if (!"absent".equals(tmrv)) {
            EmiScrollGridFix.LOGGER.info("{} reload #{}: TMRV is the active bridge ({}), and the jei={} it provides is TMRV's own imitation — TMRV already hides JEI's tag categories, skipping",
                    LOG_PREFIX, reload, tmrv, jei);
            return;
        }
        if ("absent".equals(jei)) {
            // 既没有 TMRV 也没有 JEI：不会有任何东西把 JEI 的标签类别带进 EMI
            EmiScrollGridFix.LOGGER.info("{} reload #{}: no JEI/TMRV bridge present — skipping; nothing feeds JEI's tag categories into EMI (bridges: {})",
                    LOG_PREFIX, reload, describeBridges());
            return;
        }

        currentRemoved = 0;
        CURRENT_CATEGORIES.clear();

        EmiScrollGridFix.LOGGER.info("{} reload #{}: armed — JEMI path (JEI={}) will hide JEI's built-in tag categories from EMI (uid path '{}'); bridges: {}",
                LOG_PREFIX, reload, jei, JEI_TAG_CATEGORY_PATH_PREFIX, describeBridges());

        registry.removeRecipes(recipe -> {
            var category = recipe.getCategory();
            if (category == null || !category.getId().getPath().startsWith(JEI_TAG_CATEGORY_PATH_PREFIX)) {
                return false;
            }
            currentRemoved++;
            if (CURRENT_CATEGORIES.add(category.getId())) {
                EmiScrollGridFix.LOGGER.info("{} reload #{}: matched JEI tag category {} — its recipes are hidden from EMI",
                        LOG_PREFIX, reload, category.getId());
            }
            // 让下一次注册能补报本次结果
            lastRemoved = currentRemoved;
            lastCategories = Set.copyOf(CURRENT_CATEGORIES);
            return true;
        });
    }

    /**
     * 把当场加载的链路与版本写进日志，"为什么 TMRV 局里也有这条"一眼就能看出来。
     */
    private static String describeBridges() {
        var bridges = new ArrayList<String>(3);
        bridges.add("EMI=" + versionOf("emi"));
        bridges.add("JEI=" + versionOf("jei"));
        bridges.add("TMRV=" + versionOf("toomanyrecipeviewers"));
        return String.join(", ", bridges);
    }

    private static String versionOf(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("absent");
    }
}
