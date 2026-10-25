# Minecraft 1.8.8 发包版

本目录基于 Citizens 2.0.35（上游提交 `2c0c7bf8a0dc11c25b6dfc63966769c074990b18`），对应工作区原有的 `Citizens-2.0.35-MC1.8.8.jar`。工作区顶层的 2.0.44 / 26.x 源码不能用于构建 1.8.8，因此适配源码单独放在这里。

## 使用

关闭服务器，将原 Citizens JAR 移出 `plugins`，放入工作区根目录的 `Citizens-2.0.35-MC1.8.8-Packet.jar`，然后启动服务器。保留原来的 `plugins/Citizens` 配置和存档目录；已有 NPC 会在载入时默认采用发包模式，不需要重新创建。不要同时加载两个 Citizens JAR。

NPC 的可见名字由 `name` 改为 `&8[NPC] name`，`&8` 显示为深灰色。已含 `[NPC] ` 前缀的名字不会重复添加。原始名称仍用于存档、选择 NPC 和默认皮肤查询；重命名后显示名会自动更新。超过玩家名称长度限制的名字使用发包全息图，不截断名字。

Tab 列表单独显示 `&8[NPC] ` 加 10 位随机小写字母或数字，例如 `&8[NPC] a3m9k2x7bc`。每个 NPC 固定一个随机名，保存到 NPC 元数据；刷新 Tab、重命名、重连、重载和重启都沿用该名字，复制出来的 NPC 会生成自己的随机名。随机名通过 Tab 显示名字段发送，完整保留颜色与 10 位字符；头顶名字和默认皮肤查询不受影响。

## 行为和范围

- NPC 保留一个供 Citizens API 使用的内存对象，但不会注册到服务器世界实体列表或世界实体追踪器。名字全息图同样使用发包 NPC。
- 每 5 tick 更新附近玩家的可见关系；位置、朝向、元数据、装备仅在变化时发送。玩家离开范围、切换世界、重生或退出时解除追踪，返回后重新显示。
- 直接处理 1.8.8 的实体交互包，并在服务器主线程触发 Citizens 的点击事件、NPC 选择和右键商店/命令逻辑，无需安装 ProtocolLib。
- `/npc sel` 从指定 NPC 注册表查找附近已生成的 NPC，支持发包 NPC；`/npc sel ID`、按原始名字选择和持选择物品右键选择同样可用。Tab 随机显示名不替换用于指令的原始名字。
- 移除生成、皮肤/追踪刷新时自动触发的挥手动画，不再出现周期性集体挥手。显式调用的动画命令仍可使用。
- 换肤、自动皮肤刷新、离开可见范围后返回、重连和重载时沿用保存的皮肤贴图及签名。旧实体的延迟注销、皮肤回调和 Tab 移除任务不会覆盖新实体；同一个实体重复注册不会被删除。发包 NPC 的临时 Tab 条目至少保留 20 tick，给 1.8 客户端加载皮肤的时间。
- 启用 `/npc lookclose` 后，可用 `/npc lookclose --range 3` 设置转向距离；带 `--range` 的命令仅修改距离，不切换开关。范围内每位玩家各自看到 NPC 面向自己，范围外的观察者看到 NPC 朝向当前选中的附近玩家。无人处于范围内时，NPC 恢复启用转向前的朝向和俯仰角，刷新及重载后仍保留这个归位方向。该功能无需 ProtocolLib。
- 适用于静态展示、传送点、菜单、商店等 NPC。发包 NPC 不参与实体物理、碰撞、原版 AI 或寻路行走；传送、转头和装备显示仍可使用。依赖真实世界实体的第三方插件需要单独适配。

## 构建

使用 JDK 17 编译，生成 Java 8 字节码；运行目标为 CraftBukkit / Spigot 1.8.8 (`v1_8_R3`)。

```powershell
.\mc1.8.8\build.ps1 -Maven 'C:\Users\lenovo\Downloads\citizens-build\tools\apache-maven-3.9.9\bin\mvn.cmd' -Offline
```

本机 Maven 缓存已具备构建依赖，包括同一时期 CitizensAPI 2.0.35（提交 `e1959813`）、CraftBukkit 1.8.8 和编译用 Spigot 1.21.1。换用其他环境时需要先准备这些依赖，删除 `-Offline` 可允许 Maven 下载仓库中仍然可用的依赖。`main` 使用较新的 Spigot 编译依赖沿用上游的跨版本写法，最终成品只打包 `main` 与 `v1_8_R3`，不包含现代服务端适配模块。

## 验证

`integration/PacketIntegration.java` 与 `integration/protocol_test.py` 在私有的 1.8.8 服务端上检查实际协议包，而不是模拟 Bukkit。

测试覆盖连续换肤、重复注册、延迟注销、自动刷新、皮肤包保留时间、重连和重载后的贴图与签名，并使用三个客户端验证各自转向、范围外观察、无人时归位以及刷新和重载后的方向。

```powershell
python .\mc1.8.8\integration\protocol_test.py `
  --java 'D:\Program Files\Zulu\zulu-8\bin\java.exe' `
  --javac 'D:\Program Files\Zulu\zulu-17\bin\javac.exe' `
  --server 'C:\Users\lenovo\Downloads\citizens-build\tools\cb188.jar' `
  --citizens .\Citizens-2.0.35-MC1.8.8-Packet.jar `
  --library-cache 'C:\Users\lenovo\Downloads\citizens-build\testserver18\plugins\Citizens\lib' `
  --run-dir .\.build-tmp\packet-test-new
```

运行目录必须不存在。测试只绑定 `127.0.0.1`，结束时自动关闭测试服务器，检查结果和完整日志保存在指定运行目录。测试插件仅供集成测试使用，不需要安装到正式服务器。
