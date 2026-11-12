# Contributing

本仓库只维护 Minecraft 1.8.8 的 Citizens 发包分支。

## 环境

- JDK 17（编译），运行目标 Java 8 / CraftBukkit 1.8.8。
- Maven 3.9+。
- 集成测试需要 CraftBukkit 1.8.8、Java 8、PacketEvents 2.13.0。

## 构建与测试

```bash
./build.sh package
./build.sh test
```

Windows 使用 `build.ps1`。

## 代码约定

- 发包核心位于 `main/src/main/java/net/citizensnpcs/packet`
  （`core`、`interaction`、`sink`、`action`、`hologram`、`api` 子包）。
- PacketEvents 适配位于独立 `packet-adapter` 模块；不要把 PacketEvents/Adventure 类型引入 `main`，
  因为 `main` 的 shade 会 relocate `net.kyori`。
- 新增发包行为时优先扩展 `PacketSink`，并在 `packet-adapter` 中实现；同时补 `RecordingPacketSink` 或协议测试。
- 保留 `saves.yml` 旧键读取兼容；新增持久化数据请写迁移/回退逻辑。
