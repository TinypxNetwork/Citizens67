# Citizens 1.8.8 发包版

本目录是当前仓库唯一的源码树：一个面向 Minecraft 1.8.8 的 Citizens 分支。发包 NPC 使用
[PacketEvents 2.13.x](https://github.com/retrooper/packetevents) 直接向客户端发送实体包，不把 NPC 注册到
世界实体列表，也不参与原版实体 tick、物理或寻路。

## 运行要求

- 服务端：CraftBukkit / Spigot / PaperSpigot 1.8.8（`v1_8_R3`）。
- Java 8 运行服务端。
- **必须安装 `packetevents` 插件 2.13.0 或兼容版本**；Citizens 在 `plugin.yml` 中 `depend` 它。
- 可选：Vault、PlaceholderAPI、WorldGuard。
- 不再需要 ProtocolLib；`ProtocolLibListener` 和构建期 ProtocolLib 下载已移除。

## 构建

使用 JDK 17 编译，生成 Java 8 字节码：

```powershell
.\build.ps1
```

```bash
./build.sh package
```

产物：`dist/target/Citizens-2.0.35-bMC1.8.8-packet.jar`。

构建不再下载 ProtocolLib；Maven 会从 CodeMC 解析 PacketEvents 的 `provided` 依赖。最终 jar 不内嵌
PacketEvents，运行时由 `packetevents` 插件提供。

## 目录结构

```
main/                      Citizens 主体（Java 8 字节码）
  src/main/java/net/citizensnpcs/
    packet/core/           PacketNpcService / state / viewer session / registry
    packet/interaction/    INTERACT_ENTITY 解析与点击分发
    packet/sink/           PacketSink 抽象与 Noop 实现
    packet/action/         动作链与持久化
    packet/hologram/       轻量 packet 全息图
    packet/skin/           逐玩家皮肤解析（Mirror）
    packet/api/            对外 PacketNpcApi
  src/test/java/           JUnit 5 单元测试
v1_8_R3/                   CraftBukkit 1.8.8 适配
packet-adapter/            PacketEvents 2.13 实现（不参与 main 的 relocate）
dist/                      最终 jar 组装
integration/               真实服务端 + 原始协议客户端测试
```

## 架构

```
Citizens 命令 / trait / saves.yml
        |
PacketNpcService（注册表、静态/动态 tick、viewer 扫描、统计）
        |
PacketViewerSession（每个 NPC × 玩家：位置/朝向/装备/皮肤/Tab/动作冷却）
        |
PacketSink（抽象接口）
        |
packet-adapter 模块（PacketEvents 2.13 wrapper，不做 shade/relocate）
```

要点：

- 观察者状态只有 `PacketViewerSession` 一份事实来源；`PacketSessionRegistry` 按玩家维护集合，退出/换世界清理是
  O(可见数量)。
- 静态 NPC 默认每 20 tick 更新，动态 NPC（LookClose、全息图、临时 NPC）每 tick 更新；viewer 扫描默认每 5 tick。
- 位置/朝向/装备/metadata 只在变化时发送；装备按非空槽逐槽发送。
- 主线程每 tick 只把包放入 adapter 队列，由单发送线程按批次有序写出 PacketEvents，避免编码/写包阻塞 tick。
- `NPCSeenByPlayerEvent` 可取消；link/unlink 仍触发 Citizens 的 `NPCLinkToPlayerEvent` /
  `NPCUnlinkFromPlayerEvent`。
- 交互通过 PacketEvents 的 `INTERACT_ENTITY` 监听，支持 INTERACT / INTERACT_AT / ATTACK，按玩家+实体去重，
  可配置 reach。
- 皮肤支持逐玩家镜像（`MirrorTrait`）；URL/文件皮肤沿用 `/npc skin --url/--file` 的 MineSkin 流程。
  `PacketSkinResolver` 通过 `SkinDescriptor` 选择 static/name/mirror，`PacketSkinCache` 持久化已解析纹理。
- 轻量全息图由 `PacketHologramTrait` 用 armor stand / item packet entity 实现，每行不再创建临时 Citizens NPC。
  旧 `HologramTrait` 仍保留用于兼容/真实实体路径。
- 插件 API：`net.citizensnpcs.packet.api.PacketNpcApi` 提供 `isPacketNpc`、`getViewers`、`show/hide`、`refresh`、`getStats`。

## 命令

- `/npc packet [info|stats|scan] [--enabled true|false]`
- `/npc action add [left|right|any] [type] [value] (--cooldown ms) (--delay ticks) (--permission node)`
- `/npc action remove [index]`、`/npc action list`、`/npc action clear`
- `/npc holo add [text]`、`additem [material[:amount[:data]]]`、`insert [index] [text]`、`remove [index]`、
  `list`、`clear`、`offset [value]`、`spacing [value]`、`refresh`
- `/npc mirror [--name true|false]`
- `/npc skin [name]`、`/npc skin --url ...`、`/npc skin --file ...`

动作类型：`console`、`command`、`player-command`、`message`、`sound`、`server`、`wait`、`permission`、
`item`、`random`。

## 配置

`plugins/Citizens/config.yml` 中：

```yaml
npc:
  packets:
    default-enabled: true
    static-update-interval: 20
    dynamic-update-interval: 1
    viewer-scan-interval: 5
    interaction-reach: 6.0
    click-dedupe-millis: 50
    skin-cache-ttl-seconds: 86400
```

`packet` trait 不再无条件作为默认 trait；新 NPC 是否默认发包由 `npc.packets.default-enabled` 决定。已有
`saves.yml` 中带 `packet` trait 的 NPC 保持发包。

## 测试

单元测试：

```bash
./build.sh test
```

真实协议集成测试需要 CraftBukkit 1.8.8、Java 8、JDK 17 和 PacketEvents jar：

```bash
python integration/protocol_test.py \
  --java <java8> --javac <jdk17> \
  --server <craftbukkit-1.8.8.jar> \
  --citizens <built jar> \
  --packetevents <packetevents-2.13.0.jar> \
  --library-cache <libby-cache> \
  --run-dir <fresh dir>
```

测试覆盖 spawn/Tab/皮肤/装备/换肤/范围/换向/重连/重载，以及新增的 PacketEvents 交互、动作链、镜像皮肤和轻量全息图。

可选基准测试（创建 100 个发包 NPC 并打印 packet tick 耗时）：

```bash
CITIZENS_BENCHMARK=1 python integration/protocol_test.py ...
```

当前实测约 104 个 packet NPC / 104 个 viewer 时，packet tick 约 0.16ms（瞬时）/ 0.61ms（平滑平均）。

## 已知限制

- 发包 NPC 不参与原版物理、碰撞、寻路、AI、伤害/死亡事件；需要这些能力的 NPC 请关闭 packet 模式。
- 真实实体 NPC 的逐玩家 Rotation/Mirror 全息覆写不再由 ProtocolLib 提供；发包 NPC 的对应能力由 session 实现。
- 仅支持 1.8.8；26.x 源码与构建已移除。
