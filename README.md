# Citizens67

面向 Minecraft 1.8.8 的 [Citizens](https://github.com/CitizensDev/Citizens2) 发包 NPC 分支。

- 发包 NPC 基于 [PacketEvents](https://github.com/retrooper/packetevents) 2.13.x；服务器需要安装 `packetevents` 插件。
- 仓库只保留一套 1.8.8 源码：`main/`、`v1_8_R3/`、`packet-adapter/`、`dist/`。
- ProtocolLib 已移除；发包、皮肤、交互、动作链和轻量全息图都由 PacketEvents 路径实现。

## 构建

```bash
./build.sh package
```

Windows：

```powershell
.\build.ps1
```

产物：`dist/target/Citizens-2.0.35-bMC1.8.8-packet.jar`。

## 文档

- 架构、命令、配置、测试与已知限制：[docs/architecture.md](docs/architecture.md)
- 集成测试：[integration/protocol_test.py](integration/protocol_test.py)
- Citizens API：https://wiki.citizensnpcs.co/API

## 许可

Citizens 原始代码遵循 Open Software License 3.0；本分支为自用修改。
