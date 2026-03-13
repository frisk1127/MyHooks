# MyHooks

一个独立的 Xposed/LSPosed Hook 容器工程，用来收纳多个应用的自定义 Hook。

## 当前已实现

- QQ/TIM: 合并转发头像 `fromFaceUrl` 提取与保存

## 结构

- `app/src/main/java/moe/frisk/myhooks/HookEntry.kt`
  Xposed 入口
- `app/src/main/java/moe/frisk/myhooks/AppHook.kt`
  单个 Hook 的统一接口
- `app/src/main/java/moe/frisk/myhooks/HookRegistry.kt`
  Hook 注册表
- `app/src/main/java/moe/frisk/myhooks/qq/`
  QQ/TIM 相关 Hook

## 添加新的应用 Hook

1. 新建一个实现 `AppHook` 的对象
2. 填好 `key` 和 `targetPackages`
3. 在 `onPackageLoaded` 里写对应应用的 Hook 逻辑
4. 把它注册到 `HookRegistry.hooks`

这样可以把不同应用的 Hook 放在同一个模块里，而不用给每个目标单独维护一个仓库。
