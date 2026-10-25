"""Run a private CraftBukkit 1.8.8 server and assert actual client packet behavior.

No Minecraft client library or ProtocolLib is required. The run directory must be new.
"""
import argparse
import base64
from collections import Counter
import io
import json
import os
from pathlib import Path
import re
import shutil
import socket
import struct
import subprocess
import sys
import threading
import time
import uuid
import zipfile
import zlib


def vint(value):
    data = bytearray()
    value &= 0xFFFFFFFF
    while True:
        part = value & 127
        value >>= 7
        data.append(part | (128 if value else 0))
        if not value:
            return bytes(data)


def read_vint(stream):
    result = 0
    for index in range(5):
        part = stream.read(1)
        if not part:
            raise EOFError()
        result |= (part[0] & 127) << (index * 7)
        if not part[0] & 128:
            return result
    raise ValueError("invalid VarInt")


def string(value):
    encoded = value.encode("utf-8")
    return vint(len(encoded)) + encoded


def read_string(stream):
    return stream.read(read_vint(stream)).decode("utf-8")


def component_text(component):
    if isinstance(component, str):
        return component
    if isinstance(component, list):
        return "".join(component_text(part) for part in component)
    return component.get("text", "") + component_text(component.get("extra", []))


def has_dark_gray(component):
    if isinstance(component, list):
        return any(has_dark_gray(part) for part in component)
    return isinstance(component, dict) and (component.get("color") == "dark_gray"
                                           or has_dark_gray(component.get("extra", [])))


class Client:
    def __init__(self, port, name="PacketTest"):
        self.sock = socket.create_connection(("127.0.0.1", port), timeout=10)
        self.sock.settimeout(None)
        self.stream = self.sock.makefile("rb")
        self.compression = False
        self.play = False
        self.closed = False
        self.error = None
        self.packets = []
        self.timed_packets = []
        self.send(0, vint(47) + string("localhost") + struct.pack(">H", port) + vint(2))
        self.send(0, string(name))
        self.thread = threading.Thread(target=self.receive, daemon=True)
        self.thread.start()

    def send(self, packet_id, data=b""):
        payload = vint(packet_id) + data
        if self.compression:
            payload = vint(0) + payload
        self.sock.sendall(vint(len(payload)) + payload)

    def receive(self):
        try:
            while not self.closed:
                length = read_vint(self.stream)
                payload = self.stream.read(length)
                if self.compression:
                    packed = io.BytesIO(payload)
                    original_size = read_vint(packed)
                    payload = zlib.decompress(packed.read()) if original_size else packed.read()
                data = io.BytesIO(payload)
                packet_id = read_vint(data)
                body = data.read()
                if not self.play:
                    if packet_id == 3:
                        self.compression = True
                    elif packet_id == 2:
                        self.play = True
                    elif packet_id == 0:
                        raise AssertionError("login disconnect: " + repr(body))
                    continue
                self.timed_packets.append((time.monotonic(), packet_id, body))
                self.packets.append((packet_id, body))
                if packet_id == 1:
                    self.send(0x15, string("en_US") + b"\x04\x00\x01\x7f")
                elif packet_id == 0:
                    self.send(0, body)
                elif packet_id == 8:
                    x, y, z, yaw, pitch = struct.unpack(">dddff", body[:32])
                    self.send(6, struct.pack(">dddff?", x, y, z, yaw, pitch, True))
                elif packet_id == 0x32 and body[-1:] == b"\x00":
                    self.send(0x0F, body[:3] + b"\x01")
                elif packet_id == 0x40:
                    raise AssertionError("play disconnect: " + repr(body))
        except Exception as error:
            if not self.closed:
                self.error = error

    def close(self):
        self.closed = True
        try:
            self.sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self.sock.close()
        self.stream.close()

    def spawned(self, packets=None):
        return {read_vint(io.BytesIO(body)) for kind, body in (packets if packets is not None else self.packets)
                if kind in (0x0C, 0x0E, 0x0F, 0x10)}

    def destroyed(self, packets):
        result = set()
        for kind, body in packets:
            if kind == 0x13:
                stream = io.BytesIO(body)
                for _ in range(read_vint(stream)):
                    result.add(read_vint(stream))
        return result

    def contains(self, text, packets=None):
        return any(text.encode("utf-8") in body for _, body in (packets if packets is not None else self.packets))

    def check_equipment(self, entity_id, expected, packets=None):
        equipment = {}
        for kind, body in packets if packets is not None else self.packets:
            if kind != 4:
                continue
            stream = io.BytesIO(body)
            if read_vint(stream) != entity_id:
                continue
            slot, item_id = struct.unpack(">hh", stream.read(4))
            amount = struct.unpack(">b", stream.read(1))[0] if item_id != -1 else 0
            assert 0 <= slot <= 4, "1.8 equipment uses the hand and four armor slots"
            equipment[slot] = (item_id, amount)
        for slot, item in expected.items():
            assert equipment.get(slot) == item, \
                f"NPC {entity_id} equipment slot {slot}: received {equipment.get(slot)}, expected {item}"

    def click_window(self, window_id, slot, action, clicked_item=b"\xff\xff", button=0, mode=0):
        self.send(0x0E, struct.pack(">BhBhB", window_id, slot, button, action, mode) + clicked_item)

    def player_info(self, packets=None):
        result = []
        for kind, body in packets if packets is not None else self.packets:
            if kind != 0x38:
                continue
            stream = io.BytesIO(body)
            action = read_vint(stream)
            for _ in range(read_vint(stream)):
                entry = {"uuid": str(uuid.UUID(bytes=stream.read(16))), "action": action}
                if action == 0:
                    entry["profile"] = read_string(stream)
                    entry["properties"] = {}
                    for _ in range(read_vint(stream)):
                        name = read_string(stream)
                        value = read_string(stream)
                        signature = read_string(stream) if stream.read(1) == b"\x01" else None
                        entry["properties"][name] = {"value": value, "signature": signature}
                    read_vint(stream)  # game mode
                    read_vint(stream)  # latency
                elif action in (1, 2):
                    read_vint(stream)
                if action in (0, 3):
                    entry["display"] = json.loads(read_string(stream)) if stream.read(1) == b"\x01" else None
                result.append(entry)
            assert stream.read() == b"", "complete player-info packet decoded"
        return result

    def check_tab_names(self, expected, packets=None, action=0):
        entries = [entry for entry in self.player_info(packets)
                   if entry["uuid"] in expected and entry["action"] == action]
        assert {entry["uuid"] for entry in entries} == set(expected), "all NPC Tab entries received"
        for entry in entries:
            display = entry.get("display")
            assert display is not None, "NPC Tab entry has an explicit display name"
            text = component_text(display)
            assert re.fullmatch(r"\[NPC\] [a-z0-9]{10}", text), "exactly 10 lowercase letters/digits in Tab"
            assert has_dark_gray(display), "Tab entry uses the &8 dark-gray color"
            assert text == expected[entry["uuid"]][2:], "NPC keeps its saved random Tab alias"

    def check_skin(self, skin_uuid, texture_hash, packets=None):
        entries = [entry for entry in self.player_info(packets)
                   if entry["uuid"] == skin_uuid and entry["action"] == 0]
        assert entries, "skin refresh sends the player-info entry"
        for entry in entries:
            skin = entry["properties"]["textures"]
            assert skin["signature"] == "packet-test-signature", "skin signature preserved"
            texture = json.loads(base64.b64decode(skin["value"]))
            assert texture["textures"]["SKIN"]["url"].endswith(texture_hash), "saved texture sent to client"

    def check_rotation(self, entity_id, yaw, pitch):
        rotation = {}
        for kind, body in self.packets:
            if kind not in (0x0C, 0x16, 0x17, 0x18, 0x19):
                continue
            stream = io.BytesIO(body)
            if read_vint(stream) != entity_id:
                continue
            if kind == 0x19:
                rotation["head"] = stream.read(1)[0] * 360 / 256
                continue
            if kind == 0x0C:
                stream.read(28)  # UUID and fixed-point XYZ
            elif kind == 0x18:
                stream.read(12)
            elif kind == 0x17:
                stream.read(3)
            rotation["body"], rotation["pitch"] = (value * 360 / 256 for value in stream.read(2))
        for name, expected in (("head", yaw), ("body", yaw), ("pitch", pitch)):
            assert name in rotation, f"client received NPC {name} rotation"
            difference = abs((rotation[name] - expected + 180) % 360 - 180)
            assert difference < 3, f"NPC {name} is {rotation[name]:.2f}, expected {expected:.2f}"

    def check_skin_delay(self, skin_uuid, packets):
        added_at = None
        removed = False
        for received_at, kind, body in packets:
            for entry in self.player_info([(kind, body)]):
                if entry["uuid"] != skin_uuid:
                    continue
                if entry["action"] == 0:
                    added_at = received_at
                elif entry["action"] == 4 and added_at is not None:
                    assert received_at - added_at >= 0.8, "skin entry kept long enough for the client to load its texture"
                    removed = True
                    added_at = None
        assert removed, "temporary skin Tab entry is eventually removed"


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument("--java", required=True)
    parser.add_argument("--javac", required=True)
    parser.add_argument("--server", type=Path, required=True)
    parser.add_argument("--citizens", type=Path, required=True)
    parser.add_argument("--library-cache", type=Path, required=True)
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25585)
    args = parser.parse_args()
    run_dir = args.run_dir.resolve()
    run_dir.mkdir(parents=True, exist_ok=False)
    plugins = run_dir / "plugins"
    plugins.mkdir()
    shutil.copy2(args.server, run_dir / "server.jar")
    shutil.copy2(args.citizens, plugins / "Citizens.jar")
    shutil.copytree(args.library_cache, plugins / "Citizens" / "lib")
    (run_dir / "eula.txt").write_text("eula=true\n", encoding="ascii")
    (run_dir / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={args.port}\nonline-mode=false\n"
        "level-type=FLAT\ngenerate-structures=false\nallow-nether=false\n"
        "spawn-monsters=false\nspawn-animals=false\nview-distance=3\n"
        "gamemode=1\nmax-players=4\nnetwork-compression-threshold=256\n",
        encoding="ascii")
    (run_dir / "bukkit.yml").write_text("settings:\n  allow-end: false\n", encoding="ascii")
    for name in ("banned-players.json", "banned-ips.json", "ops.json", "whitelist.json"):
        (run_dir / name).write_text("[]\n", encoding="ascii")
    (plugins / "Citizens" / "config.yml").write_text(
        "npc:\n  skins:\n    try-fetch-default-skin: false\n", encoding="ascii")
    source_dir = Path(__file__).resolve().parent
    classes = run_dir / "test-classes"
    classes.mkdir()
    subprocess.run([args.javac, "--release", "8", "-encoding", "UTF-8", "-classpath",
                    os.pathsep.join([str(args.server.resolve()), str(args.citizens.resolve())]),
                    "-d", str(classes), str(source_dir / "PacketIntegration.java")], check=True)
    with zipfile.ZipFile(plugins / "PacketIntegration.jar", "w") as jar:
        jar.write(source_dir / "plugin.yml", "plugin.yml")
        for file in classes.rglob("*.class"):
            jar.write(file, str(file.relative_to(classes)).replace("\\", "/"))
    log_lines = []
    log_file = (run_dir / "integration.log").open("w", encoding="utf-8")
    process = subprocess.Popen([args.java, "-Dfile.encoding=UTF-8", "-Xms128M", "-Xmx512M", "-jar", "server.jar", "nogui"],
                               cwd=run_dir, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace",
                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)

    def read_log():
        for line in process.stdout:
            log_lines.append(line)
            log_file.write(line)
            log_file.flush()
            if "[PACKET-TEST]" in line or "ERROR" in line or "Exception" in line:
                print(line.strip(), flush=True)

    threading.Thread(target=read_log, daemon=True).start()
    client = None
    extra_clients = []
    phases = []

    def until(predicate, description, timeout=40):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            for connection in ([client] if client else []) + extra_clients:
                if connection.error:
                    raise AssertionError(f"client error: {connection.error}")
            if process.poll() is not None:
                raise AssertionError("server exited: " + "".join(log_lines[-20:]))
            if predicate():
                return
            time.sleep(0.05)
        raise AssertionError("timeout: " + description + "\n" + "".join(log_lines[-12:]))

    def phase(name):
        start = len(log_lines)
        packet_start = len(client.packets)
        process.stdin.write("packettest " + name + "\n")
        process.stdin.flush()
        until(lambda: any("[PACKET-TEST]" in line and ("PASS" in line or "FAIL" in line)
                          for line in log_lines[start:]), name)
        assert not any("FAIL" in line for line in log_lines[start:]), "".join(log_lines[start:])
        assert not any("Player registered twice" in line for line in log_lines[start:]), "".join(log_lines[start:])
        state = json.loads((run_dir / "packet-test-state.json").read_text(encoding="utf-8"))
        phases.append(name)
        return state, client.packets[packet_start:]

    try:
        until(lambda: any("Done (" in line for line in log_lines), "server startup")
        client = Client(args.port)
        until(lambda: client.play and any("logged in with entity id" in line for line in log_lines), "client login")
        state, packets = phase("setup")
        assert set(state["ids"] + state["holograms"]) <= client.spawned(), "client received all virtual spawns"
        assert client.contains("\u00a78[NPC] Merchant"), "villager display name in metadata"
        assert client.contains("\u00a78[NPC] Steve"), "player profile display name"
        assert client.contains("\u00a78[NPC] LongMerchantName123"), "long name rendered in hologram metadata"
        tab_names = state["tab_names"]
        skin_uuid = next(iter(tab_names))
        assert len(set(tab_names.values())) == len(tab_names), "NPCs have independent random aliases"
        client.check_tab_names(tab_names)
        real_player = [entry for entry in client.player_info() if entry.get("profile") == "PacketTest"]
        assert real_player and all(entry.get("display") is None for entry in real_player), "real player Tab names are unchanged"
        old_ids = state["ids"]
        state, packets = phase("mutate")
        assert old_ids[1] in client.destroyed(packets), "renamed short player profile was removed"
        assert state["ids"][1] in client.spawned(packets), "renamed player profile respawned"
        assert client.contains("\u00a78[NPC] Dealer", packets), "changed villager metadata"
        assert client.contains("\u00a78[NPC] NewLongMerchantName", packets), "changed hologram name"
        assert any(kind == 4 and b"\x01\x14" in body for kind, body in packets), "diamond sword equipment sent"
        assert any(kind == 0x18 for kind, _ in packets), "virtual teleport sent"
        assert state["tab_names"] == tab_names, "renaming NPCs preserves their random Tab aliases"
        state, packets = phase("equipopen")
        windows = [body[0] for kind, body in packets if kind == 0x2D]
        assert windows, "/npc equip opens the equipment window"
        window_id = windows[-1]
        packet_start = len(client.packets)
        client.click_window(window_id, 9, 1)
        state, _ = phase("equiphand")
        client.check_equipment(state["ids"][1], {0: (276, 1)}, client.packets[packet_start:])
        packet_start = len(client.packets)
        client.click_window(window_id, 9, 2, struct.pack(">hbh", 276, 1, 0) + b"\x00")
        state, _ = phase("equipremove")
        client.check_equipment(state["ids"][1], {0: (-1, 0)}, client.packets[packet_start:])
        phase("equipappleprepare")
        packet_start = len(client.packets)
        client.click_window(window_id, 9, 3)
        state, _ = phase("equipapple")
        client.check_equipment(state["ids"][1], {0: (260, 3)}, client.packets[packet_start:])
        phase("equipprotectedprepare")
        client.click_window(window_id, 0, 4, struct.pack(">hbh", 276, 1, 0) + b"\x00")
        phase("equipprotected")
        client.click_window(window_id, 14, 5)
        phase("equipinvalidarmor")
        phase("equipshiftprepare")
        packet_start = len(client.packets)
        client.click_window(window_id, 9, 6, struct.pack(">hbh", 260, 3, 0) + b"\x00", mode=1)
        state, _ = phase("equipshiftout")
        client.check_equipment(state["ids"][1], {0: (-1, 0)}, client.packets[packet_start:])
        packet_start = len(client.packets)
        client.click_window(window_id, 45, 7, struct.pack(">hbh", 260, 64, 0) + b"\x00", mode=1)
        state, _ = phase("equipshiftin")
        client.check_equipment(state["ids"][1], {0: (260, 64)}, client.packets[packet_start:])
        packet_start = len(client.packets)
        client.click_window(window_id, 9, 8, struct.pack(">hbh", 260, 64, 0) + b"\x00", button=1)
        state, _ = phase("equiphalf")
        client.check_equipment(state["ids"][1], {0: (260, 32)}, client.packets[packet_start:])
        packet_start = len(client.packets)
        client.click_window(window_id, 9, 9, struct.pack(">hbh", 260, 32, 0) + b"\x00", button=1)
        state, _ = phase("equipplaceone")
        client.check_equipment(state["ids"][1], {0: (260, 33)}, client.packets[packet_start:])
        phase("equipbootsprepare")
        packet_start = len(client.packets)
        client.click_window(window_id, 14, 10)
        state, _ = phase("equipboots")
        client.check_equipment(state["ids"][1], {1: (313, 1)}, client.packets[packet_start:])
        state, _ = phase("equiparmor")
        player_equipment = {0: (276, 1), 1: (313, 1), 2: (312, 1), 3: (311, 1), 4: (310, 1)}
        client.check_equipment(state["ids"][1], player_equipment)
        state, packets = phase("tabrefresh")
        assert state["tab_names"] == tab_names, "Tab refresh does not regenerate random aliases"
        client.check_tab_names(tab_names, packets)
        client.check_tab_names(tab_names, packets, action=3)
        for skin_phase, texture_hash in (("skin", "a" * 64), ("skinagain", "b" * 64)):
            old_id = state["ids"][1]
            state, packets = phase(skin_phase)
            assert old_id in client.destroyed(packets), "previous skin entity removed from client"
            assert state["ids"][1] != old_id and state["ids"][1] in client.spawned(packets), "new skin entity spawned"
            assert state["tab_names"] == tab_names, "skin changes preserve saved Tab aliases"
            client.check_skin(skin_uuid, texture_hash, packets)
            client.check_tab_names({skin_uuid: tab_names[skin_uuid]}, packets)
            client.check_equipment(state["ids"][1], player_equipment, packets)
        phase("reregister")
        skin_refresh_start = len(client.timed_packets)
        _, packets = phase("skinrefresh")
        client.check_skin(skin_uuid, "b" * 64, packets)
        client.check_skin_delay(skin_uuid, client.timed_packets[skin_refresh_start:])
        phase("select")
        # INTERACT_AT plus INTERACT must produce only one right click.
        client.send(2, vint(state["ids"][0]) + vint(2) + struct.pack(">fff", 0, 1, 0))
        client.send(2, vint(state["ids"][0]) + vint(0))
        time.sleep(0.15)
        client.send(2, vint(state["ids"][0]) + vint(1))
        time.sleep(0.15)
        phase("clicks")
        state, _ = phase("selectclickprepare")
        client.send(2, vint(state["ids"][0]) + vint(0))
        time.sleep(0.15)
        phase("selectclick")
        phase("chunk")
        phase("toggle")
        far_state, packets = phase("far")
        assert set(far_state["ids"] + far_state["holograms"]) <= client.destroyed(packets), "out-of-range destroys"
        state, packets = phase("near")
        assert set(state["ids"] + state["holograms"]) <= client.spawned(packets), "in-range respawns"
        client.check_tab_names(tab_names, packets)
        client.check_skin(skin_uuid, "b" * 64, packets)
        client.check_equipment(state["ids"][1], player_equipment, packets)
        state, packets = phase("world")
        assert set(state["ids"]) <= client.destroyed(packets), "world change destroys"
        state, packets = phase("near")
        assert set(state["ids"] + state["holograms"]) <= client.spawned(packets), "return to world respawns"
        client.check_equipment(state["ids"][1], player_equipment, packets)
        state, packets = phase("filter")
        assert state["ids"][0] in client.destroyed(packets), "filtered NPC removed"
        assert state["ids"][0] not in client.spawned(packets), "cancelled seen event prevents spawning"
        state, packets = phase("unfilter")
        assert state["ids"][0] in client.spawned(packets), "unfiltered NPC respawns"
        phase("cancel")
        phase("save")
        state, packets = phase("reload")
        assert set(state["ids"] + state["holograms"]) <= client.spawned(packets), "saved NPCs respawn after reload"
        assert client.contains("\u00a78[NPC] Dealer", packets), "prefix survives reload"
        assert state["tab_names"] == tab_names, "saved random aliases survive disk reload"
        client.check_tab_names(tab_names, packets)
        client.check_skin(skin_uuid, "b" * 64, packets)
        client.check_equipment(state["ids"][1], player_equipment, packets)
        client.close()
        until(lambda: any("PacketTest left" in line for line in log_lines), "disconnect")
        client = Client(args.port)
        until(lambda: client.play, "second client login")
        state, packets = phase("reconnect")
        assert set(state["ids"] + state["holograms"]) <= client.spawned(), "reconnect restores all NPCs"
        client.check_tab_names(tab_names)
        client.check_skin(skin_uuid, "b" * 64)
        client.check_equipment(state["ids"][1], player_equipment)
        extra_clients = [Client(args.port, "LookEast"), Client(args.port, "LookObserver")]
        until(lambda: all(connection.play for connection in extra_clients)
              and all(any(name + "[/" in line and "logged in with entity id" in line for line in log_lines)
                      for name in ("LookEast", "LookObserver")), "lookclose clients login")
        state, _ = phase("looksetup")
        look_id = state["ids"][1]
        client.check_rotation(look_id, 0, 0)
        extra_clients[0].check_rotation(look_id, -90, 0)
        extra_clients[1].check_rotation(look_id, 0, 0)
        phase("lookoutside")
        for connection in [client] + extra_clients:
            connection.check_rotation(look_id, -90, 0)
        state, packets = phase("lookrefresh")
        look_id = state["ids"][1]
        client.check_skin(skin_uuid, "b" * 64, packets)
        client.check_equipment(look_id, player_equipment, packets)
        for connection in [client] + extra_clients:
            connection.check_rotation(look_id, -90, 0)
        phase("save")
        state, packets = phase("reload")
        look_id = state["ids"][1]
        client.check_skin(skin_uuid, "b" * 64, packets)
        phase("lookempty")
        for connection in [client] + extra_clients:
            connection.check_rotation(look_id, 37, 13)
        phase("lookreturn")
        client.check_rotation(look_id, 0, 0)
        extra_clients[0].check_rotation(look_id, -90, 0)
        extra_clients[1].check_rotation(look_id, 0, 0)
        phase("lookdisable")
        for connection in [client] + extra_clients:
            connection.check_rotation(look_id, 37, 13)
        for connection in extra_clients:
            connection.close()
        extra_clients.clear()
        time.sleep(1)
        before = len(client.packets)
        time.sleep(1)
        idle = Counter(kind for kind, _ in client.packets[before:] if kind in (4, 0x16, 0x18, 0x19, 0x1C))
        assert not idle, f"stationary NPCs must not resend unchanged state: {idle}"
        all_ids = state["ids"] + state["holograms"]
        _, packets = phase("cleanup")
        assert set(all_ids) <= client.destroyed(packets), "cleanup removes all client entities"
        assert not any(kind == 0x0B and body[-1:] == b"\x00" for kind, body in client.packets), \
            "NPCs must not automatically swing on spawn, relink, reload or reconnect"
        result = {"result": "PASS", "phases": phases, "idle_npc_updates_per_second": dict(idle),
                  "persistent_random_tab_names": tab_names}
        (run_dir / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        print(json.dumps(result), flush=True)
    finally:
        if client:
            client.close()
        for connection in extra_clients:
            connection.close()
        if process.poll() is None:
            process.stdin.write("stop\n")
            process.stdin.flush()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.terminate()
        log_file.close()


if __name__ == "__main__":
    main()
