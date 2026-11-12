package net.citizensnpcs.packet.core;

import org.bukkit.Bukkit;

import net.citizensnpcs.api.util.Messaging;
import net.citizensnpcs.packet.sink.NoopPacketSink;
import net.citizensnpcs.packet.sink.PacketSink;

/** Loads the version/library adapter via reflection so the main jar never links PacketEvents directly. */
public final class PacketSinkLoader {
    private static final String ADAPTER_CLASS = "net.citizensnpcs.packet.adapter.PacketEventsPacketSink";

    private PacketSinkLoader() {
    }

    public static PacketSink load() {
        if (Bukkit.getPluginManager().getPlugin("packetevents") == null) {
            Messaging.severe("PacketEvents is not installed; packet NPCs will not render.");
            return new NoopPacketSink();
        }
        try {
            Class<?> clazz = Class.forName(ADAPTER_CLASS);
            return (PacketSink) clazz.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            Messaging.severe("PacketEvents adapter could not be loaded; packet NPCs will not render.");
            if (Messaging.isDebugging()) {
                t.printStackTrace();
            }
            return new NoopPacketSink();
        }
    }
}
