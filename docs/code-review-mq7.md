# mq.7 代码审查报告

审查日期：2026-09-27。基线为 `feature/jei-cache-modes` 的 mq.6（`c7b72c9`）。
范围包含整个源码树的结构、静态缓存、线程和等待、异常处理、Mixin 配置及兼容开关扫描，
并深入检查启动与发布、取消与断线、GUI、两种缓存模式、拼音索引、配方索引及兼容模块的关键路径。
主源码共 157 个 Java 文件；这不是逐个整合包、各版本的完整运行验证。

确认并修复四组问题。P1 表示可能中断启动或导致崩溃；P2 表示缓存生命周期或兼容性缺陷。
这里的严重程度描述可触发的代码路径，并不表示已在玩家客户端逐一复现。

## 1. P1：正常的跨服取消可能进入崩溃处理

位置：`runtime/JeiOptExecutors.java` 的 `isJeiStartCancellation`、`awaitJeiStartTask`
和 `runOnMainThreadAndWait`。

旧代码只识别内部的 `JeiStartCancelled`。索引收尾发现世界代次改变时会抛出普通
`CancellationException`，异步 Future 还可能把它包装为 `CompletionException` 或
`ExecutionException`。它可能穿过取消判断，被启动错误处理转发到客户端主线程。

修复：解开 Future 异常包装，统一识别并传播取消；真正的空指针等错误仍保留错误处理。

证据：新增测试覆盖普通取消、嵌套包装、实际异步任务失败和真正错误的区分。
将同一测试放到 mq.6 JAR 前置的类路径运行，会在
`world-change cancellation is not a crash` 断言失败；mq.7 通过。

## 2. P1（仅实验选项）：配方预热的等待可能无法退出

位置：`recipe/VanillaRecipeWarmup.java`。

旧代码对工作线程 Future 使用不可中断的 `join()`，且未将任务纳入世界代次的取消管理。
若断线时线程池丢弃了尚未执行的排队任务，相应 Future 可能永不完成，启动线程持续等待，
后续 JEI 启动也可能被阻塞。该路径需要启用 `parallelVanillaRecipes`，默认关闭。

修复：只允许专用启动线程进入预热；记录并追踪每个任务；使用可中断的启动等待；
在解析途中检查世界代次和中断，退出时取消未完成 Future。

证据：回归测试占满单线程池，再将任务排队；取消启动、失效代次并关闭线程池后，
检查等待者退出、排队任务被取消，以及下一次启动能够执行。
测试覆盖执行器和生命周期机制，没有启动真实 Minecraft 配方插件。

限制：第三方 `Ingredient.getItems()` 的线程安全性没有因此得到保证，
不响应中断的第三方调用也无法被 Java 安全地强行终止。该实验仍默认关闭。

## 3. P2：退出世界时遗漏两处缓存清理

位置：`integration/JeiOptStartupDriver.java`、`recipe/BrewingRecipeIndex.java`、
`recipe/GenerationAwareRecipeIndex.java`、`integration/ProductiveTreesStripperToolCache.java`。

旧的酿造索引会持有配方集合和索引，Productive Trees 缓存会持有工具 Ingredient，
即使已退出世界也要等后续访问或进程结束才有机会替换，延迟旧对象释放。
工具缓存还把代次和 Ingredient 分别写入两个 volatile 字段，读者可能观察到不成对的值。

修复：在运行时清理路径清空两处缓存；索引清理同时释放源集合并重置回退状态；
工具缓存改为一个不可变条目，让代次与 Ingredient 一起发布，并拒绝旧代次写入。

证据：新增索引生命周期测试检查清理后旧条目不可查、源集合引用已释放、
重新加载及进入回退后的恢复。工具缓存的发布与清理路径进行了代码审查和编译检查。

这说明存在旧对象滞留，不代表已经证明无上限的内存泄漏；本轮未测量实际保留堆大小。

## 4. P2：FULL 缓存的版本检查漏掉必要依赖

位置：`JeiOptMixinPlugin.java` 的 `hasSessionCacheContract`。

旧检查要求一个已不再使用的配方指纹接口，却未要求缓存实际依赖的
`Internal.jeiRuntime` 字段。在该字段改变的 JEI 版本上，运行时访问器可能不加载，
缓存 Mixin 却仍加载，后续调用可能失败。这是版本适配缺陷，当前 JEI 19.57 字段本身正常。

修复：缓存检查直接复用运行时访问器的字段检查，去掉旧指纹接口依赖。

证据：读取实际 JEI 19.57 类结构，改变字段类型模拟不兼容版本，要求缓存被拒绝。
mq.6 会在 `cache rejected without its runtime accessor ABI` 断言失败；mq.7 通过。

## 验证与交付范围

- NeoForge 1.21.1 完整构建成功，14 项回归运行器通过（包含多项断言和差分测试）。
- 打包产物与实际 JEI 19.57.0.449、JECharacters 4.5.29 的接口检查通过。
- 拼音搜索继续与实际 PinIn 1.6.0 对照；占位 GUI 拒绝缓存与恢复测试通过。
- 新增测试确认 mq.6 的取消分类和缓存依赖检查确有缺陷，而非只验证新实现。
- 本轮未完成 mq.7 的真实客户端进服、连续跨服、加载中断线、资源重载验收。
- 本轮没有重新构建和游戏验证 Forge 1.20.1，也没有验证所有第三方模组版本组合。

FULL 保留同一连接内的完整运行时，子服配方有差异时可能显示旧配方，这是既定模式取舍；
默认 FULL 保持不变。ACCURATE 保持按当前子服重建配方。拼音共享字典有容量上限，
只共享字符串及搜索结构；未发现它保存旧世界物品对象的路径。

本轮未在其余已检查路径确认新的同等级缺陷，但静态审查和接口测试不能证明所有插件
回调均线程安全，也不能保证未来 JEI 版本自动兼容。建议用 mq.7 在实际整合包中完成上述验收。
