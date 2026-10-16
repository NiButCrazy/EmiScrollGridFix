# EMI Scroll Grid Fix

给 EMI 侧的两条「JEI 插件兼容链路」补上缺失的一环：`IRecipeLayoutBuilder#addSlotToWidget`（JEI 15.10+ 的滚动格控件）。

## 这几个东西是什么关系

| 名称 | 是什么 | 与本模组的关系 |
|---|---|---|
| **JEI** | 配方查看器本体，也是插件 API 的提供方（mod 们注册配方类别的地方） | 不碰它的界面；本模组修的是"JEI 插件配方 → EMI"这条转译链 |
| **EMI** | 另一个配方查看器，玩家实际看到的界面是它 | bug 就出现在它的界面上（物品全挤一格） |
| **JEMI** | EMI **内置**的 JEI 兼容层，**需要装 JEI**：把 JEI 的配方类别转给 EMI | 本模组修它的 `JemiRecipeLayoutBuilder#addSlotToWidget` |
| **TMRV** | TooManyRecipeViewers，第三方兼容层，**自带一份 JEI API、不需要装 JEI** | 本模组修它的 `RecipeLayoutBuilder#addSlotToWidget`，并把溢出换成翻页按钮 |

也就是说：**JEMI 和 TMRV 是两条互斥的路线**（装了 JEI 走 JEMI，装了 TMRV 走 TMRV），两条路线的同一个方法都把控件工厂丢掉、槽位落在 `(0, 0)`，本模组在各自的布局器上各注入一处，按滚动格的实际区域摆放槽位；两条链路各自独立生效、无需配置，缺哪个桥就只记一条警告并跳过。

> 名字的由来：`EMI`（症状出现在 EMI 界面）+ `ScrollGrid`（被修的是 JEI 的滚动格控件）+ `Fix`。
> 更严格地说，mixin 目标是两个桥的布局器（`dev.emi.emi.jemi.impl.JemiRecipeLayoutBuilder` 与
> `dev.nolij.toomanyrecipeviewers.impl.jei.api.gui.builder.RecipeLayoutBuilder`），修的是「JEI 插件配方 → EMI」
> 这条转译链，叫 `JemiScrollGridFix` 也算贴切；如果要改名，注意 mod id 变了就等同于一个新模组（旧 jar 必须删掉）。

## 症状

用 JEI 15.10+ 新增的 `IGuiHelper#createScrollGridFactory`（滚动格）排布产出的配方，在 JEI 里显示正常，在 EMI 里却会把全部物品叠在同一个格子上——例如 CrockPot 的 `crockpot:piglin_bartering`（15 个产出全挤在左上角）。

两个桥的源码里都能看到同一个错误：

```java
// 一、EMI 的 JEMI 是 TODO 桩（dev/emi/emi/jemi/impl/JemiRecipeLayoutBuilder.java）
@Override
public IRecipeSlotBuilder addSlotToWidget(RecipeIngredientRole role, ISlottedWidgetFactory<?> widgetFactory) {
    // TODO actually implement this factory method of adding slots
    return addSlot(role, 0, 0);
}

// 二、TMRV 把工厂参数直接丢掉（dev.nolij.toomanyrecipeviewers.impl.jei.api.gui.builder.RecipeLayoutBuilder.java）
@Override
public IRecipeSlotBuilder addSlotToWidget(RecipeIngredientRole role, ISlottedWidgetFactory<?> widgetFactory) {
    return addSlot(role);   // widgetFactory 没被使用，槽位 rect 默认 (0, 0)
}
```

## 这个模组做了什么

两个 mixin，各自注入对应桥的 `addSlotToWidget`（HEAD、可取消），把槽位按控件工厂给出的区域摆放：

- 控件工厂是 `IScrollGridWidgetFactory` 时，读 `getArea()` 拿到起点与可见行数、反射读 JEI `ScrollGridWidgetFactory` 的私有字段 `columns` 得到列数（读不到就按 `(区域宽 - 16) / 18` 反推），然后按 18px 一格摆放，物品坐标 `+1`。
- 槽框：两条链路都主动 `setBackground` 补上 JEI 的槽位贴图（JEI 里槽框本来是滚动格控件自己画的，这两个桥都没有那个控件）。贴图统一从 `mezz.jei.common.Internal.getTextures()` 取——JEI 15.20 自己的滚动格就是这么取的（`getSlot()`），TMRV 也把同一份 JEI 内部类原样打包了；方法名 `getSlotDrawable()`（旧版）/`getSlot()`（15.20+）都会试。
- 不是滚动格控件、或拿不到有效区域时，保持对应桥原本的行为（不干预）。
- **TMRV 侧多了一步（1.1.2 起）**：某个滚动格工厂的槽位数量**超过一页能容纳的格子**时，不再顺序往下排，而是把这些槽位交给 TMRV 自带的 `ScrollGridWidget`（会把不可见行 `setVisible(false)` 裁掉），位置仍用该工厂原本的区域；没超出的保持固定坐标（外观与 JEI 一致）。
- **翻页按钮而非滚动条（1.1.3 起）**：交给滚动格之后，TMRV 那个滚动条会被藏掉（渲染与点击都取消），并在它原本占的竖条里放翻页控件：顶部上一页、底部下一页。滚动条这时只当"当前第几页"的状态载体——滚动格的定位与裁剪仍然由它驱动，所以不用自己维护页码状态，也不会和它的裁剪逻辑打架。
- **JEMI 那条也翻页了（1.2.0 起）**：JEMI 没有可复用的滚动控件，槽位坐标又是在构造时定死的（`JemiRecipeSlot.x/y` 与 EMI `SlotWidget` 的 `x/y` 都是 `final`），所以这套是自己实现的：记账（哪条工厂、多少格、第几个）→ 槽位控件按页**改 `getBounds()` 的返回值**（EMI 的物品与自绘槽框都基于它画）→ **绝对坐标画的 JEI 槽框/overlay 用 `@Redirect` 一起上移** → 不在当前页的槽位直接不画、边界返回 `Bounds.EMPTY`（悬停/提示/点击一并失效）→ 配方控件建好后挂上与 TMRV 同一个翻页控件。按钮与滚轮的交互和 TMRV 那条完全一致。
- **顺手隐藏 JEI 自带的「标签页」（1.2.0 起）**：JEI 15.20 起内部插件会为每个注册表建一个标签信息类别（uid 形如 `minecraft:tag_recipes/item`），而 EMI 自己就有物品标签信息，两套并存时 EMI 里会多出一串重复的标签页。这里用 EMI 的公开 API 把这类配方从 EMI 里移掉——EMI 配方界面的标签页是按「**有配方的**类别」推出来的（`EmiApi.mapRecipes`），配方没了标签页自然消失；`removeRecipes` 的谓词对「当前与将来」的配方都生效，所以也不依赖插件加载顺序。JEI（经 JEMI）与 TMRV 创建的类别 id 都是 JEI 的 `RecipeType` uid，因此一个谓词覆盖两条链路。
- **槽位微调**：`ScrollGridLayout.SLOT_ITEM_OFFSET` 是物品相对格点的偏移（JEI 约定是 1，当前为 0 即与格点对齐），两条链路的非溢出路径共用它，想整体挪槽位改这一处即可。
- 页号由翻页控件自己记（1.1.6 起）：TMRV 的滚动条是**连续滚动**模型（单位是行、`maxScroll = 总行数 − 可见行数`、内部还会 clamp），所以"页号 × 行数"不一定等于它的滚动量、也不能反推页号；滚动量只当输出。副作用是**最后一页会贴到内容末尾**，行数除不尽时最后一页会与上一页有几行重叠（连续滚动模型的固有行为）。
- 翻页箭头是**自绘**的（10px 竖条内一个 4 行实心三角，三态：正常 / 悬停 / 禁用）。**试过复用 EMI 的 `ButtonWidget` + `buttons.png` 里那对上下箭头（`u=232/244, v=48`，EMI 源码里 `IntEdit` 数字微调用的同一对），但有坑**：EMI 的按钮假定贴图是三态，禁用时 v 偏移 `height * 2`，而这对箭头在贴图里只有正常与悬停两态（`IntEdit` 里它们永远可用）——于是"上一页"在第 1 页被判为禁用时会画到空像素上：按钮透明、却仍然可点。自绘就没这个问题。
- 只影响 EMI 上的渲染路径，**JEI 自身的显示完全不受影响**。

两条链路互斥、各自独立生效，**不需要任何配置**：装了 JEI 走 JEMI 那条，装了 TMRV 走 TMRV 那条。

## 限制

- 两条链路在溢出时都换成翻页按钮（TMRV 1.1.3 起、JEMI 1.2.0 起），滚动条不出现；指针在**整个槽位区域**或翻页控件上滚轮都能翻页。
- JEMI 那条是"按页位移 + 隐藏"实现的，最后一页与上一页可能有几行重叠（跟 TMRV 一样，是连续/分页模型差异的必然结果）；行数正好整除时不会重叠。
- 翻页控件被放在原来滚动条占的那条竖条里（宽 10px），所以页码是竖排的（一个字符一行）；如果某个类别的背景没给滚动条留位置，它就会画在那块区域上。
- 溢出交给 TMRV 滚动格的那批槽位，会同时有我们补的 JEI 槽框和滚动格给槽位开的 EMI 槽框（同一位置两层叠画，边框会略重一点）。要让不溢出的那批保持 JEI 观感，这个代价就省不掉。
- 至少要有一个桥在跑：EMI 单独存在时，其它 mod 的 JEI 插件根本不会被加载，谁都救不了。
- 版本跟随：JEMI 那条只在“EMI + JEI”下有意义；TMRV 那条跟着它的内部类走（TMRV 更新较快，类名/结构变了注入会退化成“不生效 + 记日志”）。
- 这是一个“跟随上游”的补丁：EMI/TMRV 哪天自己实现了 `addSlotToWidget`，本模组就可以卸掉。

## 安装

把构建出来的 `EMIScrollGridFix-<MC 版本>-<模组版本>.jar`（例如 `EMIScrollGridFix-1.20.1-1.2.0.jar`）丢进 `mods` 即可（仅客户端）。缺哪个桥，就只会看到一条对应的 `Error loading class: ...` 警告并跳过，不会报错、不会崩。

验证是否生效：`latest.log` 里先出现 `EMI Scroll Grid Fix loaded, ...`；真正生效时还会再打一条（看是哪个桥在跑）：

```
Widget slots from a JEI scroll grid are now laid out in a fixed grid on EMI (TMRV bridge): origin (70, 0), 5 columns, 6 visible rows
```

JEMI 那条的措辞是 `... on EMI: origin (...)`（不带 bridge 后缀），因为 1.0.x 时只有它。只看到第一条而看不到第二条，就说明注入没跑起来（见下面的“实现上的坑”）。

另外，隐藏 JEI 标签页那条也会各打一条（出现在 EMI 加载插件时）：

```
Hiding JEI's built-in tag categories from EMI (category uid path 'tag_recipes/')
```

**没有这条**就说明 EMI 没发现这个插件（Forge 上 EMI 靠扫描 `@EmiEntrypoint` 注解发现插件），此时标签页不会消失——那就是改 mixin 方案了。

## 实现上的坑（踩过）

**绝不能在 Mixin 应用前加载目标类。** 最初这个模组用一个 `MixinConfigPlugin` + `Class.forName("dev.emi.emi.jemi.impl.JemiRecipeLayoutBuilder", false, ...)` 来探测 EMI 是否存在——这行代码在 mixin 配置准备阶段（游戏刚启动、mod 还没构造时）就把目标类加载掉了。Mixin 只能转换**尚未加载**的类，于是：

- 本模组的注入不会报错，但**静默失效**（`debug.log` 里只有本模组的 config 被 "Preparing"，永远没有 `Mixing ... into ...JemiRecipeLayoutBuilder`）；
- 连 EMI 自己的 `GlobalMixin` 也一起失效了（同一个类，同样转换不进去）；
- 游戏运行完全正常，所以从日志上看不出任何异常。

现在改成：目标类只以 `targets` 字符串 / 注解值出现（mixin 类本身不引用目标类的成员，缺桥时只是警告），依赖 JEI 运行时的槽位贴图查找抽到单独的 `RuntimeSlotBackground` 里，靠 `required = false` + `defaultRequire = 0` 保证任何异常都退化成“不生效 + 记日志”。

## 构建

```bash
./gradlew.bat build
```

产物在 `build/libs/`。编译期依赖：Forge 1.20.1 47.1.0、EMI 1.1.24+1.20.1（[Sleeping Town maven](https://repo.sleeping.town/)）、JEI 15.20.0.134（[BlameJared maven](https://maven.blamejared.com/)）。**TMRV 不需要作为依赖**：它的目标类只出现在注解字符串里，`setPosition` 走的是 JEI API 的 `IPlaceable`（TMRV 自带一份同继承链的 API 副本）。

## 备注

根因在两个上游，最彻底的修法是：

- 给 [EMI](https://github.com/emilyploszaj/emi) 提 PR 实现 JEMI 的 `addSlotToWidget` 与滚动控件；
- 给 [TooManyRecipeViewers](https://github.com/Nolij/TooManyRecipeViewers) 提 PR：它已经有完整的 `ScrollGridWidget`（会 `slot.setPosition(rect.x() + column * 18, ...)`），只差把 `addSlotToWidget` 收到的槽位交给工厂（`createWidgetForSlots`）。

本模组是把这段行为外挂实现的过渡方案。
