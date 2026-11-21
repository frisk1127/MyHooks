# MyHooks

一个独立的 Xposed/LSPosed Hook 容器工程，用来收纳多个应用的自定义 Hook。

## 当前已实现

- QQ/TIM: 合并转发头像 `fromFaceUrl` 提取与保存
- 哔哩哔哩：图片弹窗自动点击“查看原图”
- 应用高刷与画质增强：在模块首页点此项可搜索、勾选应用。系统框架为所选应用的前台主窗口请求高刷，并为已适配的双芯视频增强场景请求 120 Hz。电量和性能（`com.miui.powerkeeper`）按同一列表过滤应用级 60 Hz 限制。首次安装需勾选这两个 LSPosed 作用域并重启，之后编辑列表只需重新进入应用。
- HyperOS SystemUI：隐藏手势提示线并保留旋转建议和全屏手势
- HyperOS 手机管家：快充通知触发时自动启用快充加速
- Microsoft RDP：隐藏会话悬浮栏，增加三指呼出和键盘快捷入口

## 结构

- `app/src/main/java/moe/frisk/myhooks/HookEntry.java`
  Xposed 入口
- `app/src/main/java/moe/frisk/myhooks/AppHook.java`
  单个 Hook 的统一接口
- `app/src/main/java/moe/frisk/myhooks/HookRegistry.java`
  Hook 注册表
- `app/src/main/java/moe/frisk/myhooks/qq/`
  QQ/TIM 相关 Hook

## 添加新的应用 Hook

1. 新建一个实现 `AppHook` 的对象
2. 填好 `key` 和 `targetPackages`
3. 在 `onPackageLoaded` 里写对应应用的 Hook 逻辑
4. 把它注册到 `HookRegistry.HOOKS`

这样可以把不同应用的 Hook 放在同一个模块里，而不用给每个目标单独维护一个仓库。
