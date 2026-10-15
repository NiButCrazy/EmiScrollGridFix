# EMI Scroll Grid Fix

给 EMI 侧的两条 JEI 兼容链路补上缺失的一环：`IRecipeLayoutBuilder#addSlotToWidget`（JEI 15.10+ 的滚动格控件）。

- **JEMI**：EMI 内置的 JEI 兼容层，需要装 JEI。
- **TMRV**（TooManyRecipeViewers）：独立兼容层，不需要装 JEI。

两者都把控件工厂丢掉、槽位落在 `(0, 0)`，本模组在各自布局器上各注入一处，按滚动格的实际区域摆放槽位。

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
- 只影响 EMI 上的渲染路径，**JEI 自身的显示完全不受影响**。

两条链路互斥、各自独立生效，**不需要任何配置**：装了 JEI 走 JEMI 那条，装了 TMRV 走 TMRV 那条。

## 限制

- **没有滚动条**：超出一页能容纳的格子数量时，多出来的槽位会顺序排到下方，可能画到配方背景之外。JEI 里这些槽位是可以滚动的，EMI 里做不到（两个桥都没有把滚动交互接到 EMI 的槽位控件上）。
- 至少要有一个桥在跑：EMI 单独存在时，其它 mod 的 JEI 插件根本不会被加载，谁都救不了。
- 版本跟随：JEMI 那条只在“EMI + JEI”下有意义；TMRV 那条跟着它的内部类走（TMRV 更新较快，类名/结构变了注入会退化成“不生效 + 记日志”）。
- 这是一个“跟随上游”的补丁：EMI/TMRV 哪天自己实现了 `addSlotToWidget`，本模组就可以卸掉。

## 安装

把构建出来的 `EMIScrollGridFix-1.20.1-1.1.1.jar` 丢进 `mods` 即可（仅客户端）。缺哪个桥，就只会看到一条对应的 `Error loading class: ...` 警告并跳过，不会报错、不会崩。

验证是否生效：`latest.log` 里先出现 `EMI Scroll Grid Fix loaded, ...`；真正生效时还会再打一条（看是哪个桥在跑）：

```
Widget slots from a JEI scroll grid are now laid out in a fixed grid on EMI (TMRV bridge): origin (70, 0), 5 columns, 6 visible rows
```

JEMI 那条的措辞是 `... on EMI: origin (...)`（不带 bridge 后缀），因为 1.0.x 时只有它。只看到第一条而看不到第二条，就说明注入没跑起来（见下面的“实现上的坑”）。

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
