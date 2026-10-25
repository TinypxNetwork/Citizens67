package net.citizensnpcs.integration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.event.NPCSeenByPlayerEvent;
import net.citizensnpcs.api.event.NPCSelectEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot;
import net.citizensnpcs.npc.skin.SkinnableEntity;
import net.citizensnpcs.trait.HologramTrait;
import net.citizensnpcs.trait.PacketNPC;
import net.citizensnpcs.trait.ClickRedirectTrait;
import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.NPCNames;

/** Exercises actual Bukkit/NMS behavior; driven by protocol_test.py through the server console. */
public final class PacketIntegration extends JavaPlugin implements Listener {
    private final List<NPC> npcs = new ArrayList<>();
    private Location at;
    private World other;
    private int rightClicks, leftClicks;
    private int selectionEvents;
    private boolean cancelNextSpawn, hideMerchant;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onSpawn(NPCSpawnEvent event) {
        if (cancelNextSpawn) {
            cancelNextSpawn = false;
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSeen(NPCSeenByPlayerEvent event) {
        if (hideMerchant && !npcs.isEmpty() && event.getNPC() == npcs.get(0)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRight(NPCRightClickEvent event) {
        if (!npcs.isEmpty() && event.getNPC() == npcs.get(0)) {
            rightClicks++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeft(NPCLeftClickEvent event) {
        if (!npcs.isEmpty() && event.getNPC() == npcs.get(0)) {
            leftClicks++;
        }
    }

    @EventHandler
    public void onSelect(NPCSelectEvent event) {
        selectionEvents++;
    }

    private void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    private void later(String phase, Runnable run) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                run.run();
                checkVirtual();
                snapshot();
                getLogger().info("[PACKET-TEST] " + phase + " PASS");
            } catch (Throwable error) {
                getLogger().severe("[PACKET-TEST] " + phase + " FAIL " + error);
                error.printStackTrace();
            }
        }, 45);
    }

    private Player player() {
        Player player = Bukkit.getPlayerExact("PacketTest");
        check(player != null, "protocol client is online");
        return player;
    }

    private void checkVirtual() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                check(!entity.hasMetadata("NPC"), "world contains a real NPC entity: " + entity);
            }
        }
        for (NPC npc : npcs) {
            check(npc.isSpawned(), "spawned NPC " + npc.getId());
            check(!npc.getEntity().isValid(), "virtual backing object must not be valid in WorldServer");
            check(PacketNPC.getByEntityId(npc.getEntity().getEntityId()) != null, "interaction ID registered");
        }
        for (NPC helper : CitizensAPI.getTemporaryNPCRegistry()) {
            if (helper.isSpawned()) {
                check(helper.hasTrait(PacketNPC.class), "hologram is virtual");
                check(!helper.getEntity().isValid(), "hologram not in WorldServer");
            }
        }
    }

    private void snapshot() throws Exception {
        StringBuilder state = new StringBuilder("{\"ids\":[");
        for (int i = 0; i < npcs.size(); i++) {
            if (i > 0) state.append(',');
            state.append(npcs.get(i).getEntity().getEntityId());
        }
        state.append("],\"holograms\":[");
        boolean first = true;
        for (NPC helper : CitizensAPI.getTemporaryNPCRegistry()) {
            if (!helper.isSpawned()) continue;
            if (!first) state.append(',');
            state.append(helper.getEntity().getEntityId());
            first = false;
        }
        state.append("],\"tab_names\":{");
        first = true;
        for (NPC npc : npcs) {
            if (npc.getEntity().getType() != EntityType.PLAYER) continue;
            if (!first) state.append(',');
            state.append('"').append(npc.getMinecraftUniqueId()).append("\":\"")
                    .append(NPCNames.tabListName(npc)).append('"');
            first = false;
        }
        state.append("},\"right\":").append(rightClicks).append(",\"left\":").append(leftClicks).append('}');
        Files.write(Paths.get("packet-test-state.json"), state.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            String phase = args[0];
            switch (phase) {
                case "setup":
                    at = new Location(Bukkit.getWorlds().get(0), 0.5, 8, 0.5);
                    player().setGameMode(GameMode.CREATIVE);
                    player().teleport(at.clone().add(0, 0, 2));
                    NPC merchant = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, "Merchant");
                    NPC human = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "Steve");
                    NPC longName = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "LongMerchantName123");
                    npcs.add(merchant);
                    npcs.add(human);
                    npcs.add(longName);
                    for (int i = 0; i < npcs.size(); i++) {
                        check(npcs.get(i).spawn(at.clone().add(i - 1, 0, 0)), "spawn accepted");
                    }
                    later(phase, () -> {
                        check("\u00a78[NPC] Merchant".equals(merchant.getFullName()), "gray NPC prefix");
                        check("Merchant".equals(merchant.getRawName()), "saved raw name preserved");
                        check("steve".equals(((SkinnableEntity) human.getEntity()).getSkinName()), "skin lookup unchanged");
                        check(longName.requiresNameHologram(), "long player names use a hologram");
                        check(longName.getTraitNullable(HologramTrait.class).getNameEntity() != null, "name hologram spawned");
                        NPC prefixed = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, "&8[NPC] Ready");
                        check("\u00a78[NPC] Ready".equals(prefixed.getFullName()), "prefix not duplicated");
                        prefixed.destroy();
                    });
                    break;
                case "mutate":
                    npcs.get(0).setName("Dealer");
                    npcs.get(0).getOrAddTrait(Equipment.class).set(EquipmentSlot.HAND, new ItemStack(Material.DIAMOND_SWORD));
                    npcs.get(0).teleport(at.clone().add(-1, 0, -1), TeleportCause.PLUGIN);
                    npcs.get(1).setName("Alex");
                    npcs.get(2).setName("NewLongMerchantName");
                    later(phase, () -> check("\u00a78[NPC] Dealer".equals(npcs.get(0).getFullName()), "renamed display"));
                    break;
                case "clicks":
                    check(rightClicks == 1 && leftClicks == 1, "one left and one right click, got " + leftClicks + "/" + rightClicks);
                    later(phase, () -> {});
                    break;
                case "select":
                    player().setOp(true);
                    CitizensAPI.getDefaultNPCSelector().deselect(player());
                    Bukkit.dispatchCommand(player(), "npc sel");
                    check(CitizensAPI.getDefaultNPCSelector().getSelected(player()) == npcs.get(1), "no-argument sel finds nearest packet NPC");
                    CitizensAPI.getDefaultNPCSelector().deselect(player());
                    Bukkit.dispatchCommand(player(), "npc sel " + npcs.get(0).getId());
                    check(CitizensAPI.getDefaultNPCSelector().getSelected(player()) == npcs.get(0), "sel by ID");
                    CitizensAPI.getDefaultNPCSelector().deselect(player());
                    Bukkit.dispatchCommand(player(), "npc select Dealer");
                    check(CitizensAPI.getDefaultNPCSelector().getSelected(player()) == npcs.get(0), "select by original name");
                    Bukkit.dispatchCommand(player(), "npc sel " + npcs.get(1).getUniqueId());
                    check(CitizensAPI.getDefaultNPCSelector().getSelected(player()) == npcs.get(1), "sel by UUID");
                    CitizensAPI.getDefaultNPCSelector().deselect(player());
                    Bukkit.dispatchCommand(player(), "npc sel --range 0.1");
                    check(CitizensAPI.getDefaultNPCSelector().getSelected(player()) == null, "selection range enforced");
                    NPCRegistry custom = CitizensAPI.createNamedNPCRegistry("packet-select", new MemoryNPCDataStore());
                    NPC named = custom.createNPC(EntityType.VILLAGER, "OtherRegistry");
                    named.spawn(at.clone().add(0, 0, 2));
                    CitizensAPI.getDefaultNPCSelector().select(player(), npcs.get(0));
                    Bukkit.dispatchCommand(player(), "npc sel --registry packet-select");
                    check(CitizensAPI.getDefaultNPCSelector().getSelected(player()) == named, "named registry works with overlapping numeric IDs");
                    named.destroy();
                    CitizensAPI.removeNamedNPCRegistry("packet-select");
                    NPC redirect = CitizensAPI.getNPCRegistry().createNPC(EntityType.ARMOR_STAND, "Redirect");
                    redirect.addTrait(new ClickRedirectTrait(npcs.get(2)));
                    redirect.spawn(at.clone().add(0, 0, 2));
                    Bukkit.dispatchCommand(player(), "npc sel");
                    check(CitizensAPI.getDefaultNPCSelector().getSelected(player()) == npcs.get(2), "nearby helper redirects to parent");
                    redirect.destroy();
                    NPC despawned = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, "Unspawned");
                    CitizensAPI.getDefaultNPCSelector().deselect(player());
                    Bukkit.dispatchCommand(player(), "npc sel");
                    check(CitizensAPI.getDefaultNPCSelector().getSelected(player()) == npcs.get(1), "unspawned NPC ignored");
                    despawned.destroy();
                    CitizensAPI.getDefaultNPCSelector().deselect(player());
                    later(phase, () -> {});
                    break;
                case "selectclickprepare":
                    CitizensAPI.getDefaultNPCSelector().deselect(player());
                    player().setItemInHand(new ItemStack(Material.STICK));
                    later(phase, () -> {});
                    break;
                case "selectclick":
                    check(CitizensAPI.getDefaultNPCSelector().getSelected(player()) == npcs.get(0), "stick right-click selected NPC");
                    int events = selectionEvents;
                    Bukkit.getPluginManager().callEvent(new NPCRightClickEvent(npcs.get(0), player()));
                    check(selectionEvents == events, "repeated stick clicks do not reselect or parse UUID as integer");
                    player().setItemInHand(new ItemStack(Material.AIR));
                    later(phase, () -> {});
                    break;
                case "far":
                    player().teleport(at.clone().add(200, 0, 0));
                    later(phase, () -> check(!npcs.get(0).getTraitNullable(PacketNPC.class).isTracking(player()), "far viewer unlinked"));
                    break;
                case "near":
                    player().teleport(at.clone().add(0, 0, 2));
                    later(phase, () -> check(npcs.get(0).getTraitNullable(PacketNPC.class).isTracking(player()), "near viewer linked"));
                    break;
                case "world":
                    if (other == null) {
                        other = new WorldCreator("packet_other").type(WorldType.FLAT).generateStructures(false).createWorld();
                    }
                    player().teleport(new Location(other, 0.5, 8, 0.5));
                    later(phase, () -> check(!npcs.get(0).getTraitNullable(PacketNPC.class).isTracking(player()), "world viewer unlinked"));
                    break;
                case "filter":
                    hideMerchant = true;
                    npcs.get(0).getTraitNullable(PacketNPC.class).hide(player());
                    later(phase, () -> check(!npcs.get(0).getTraitNullable(PacketNPC.class).isTracking(player()), "seen event cancellation respected"));
                    break;
                case "unfilter":
                    hideMerchant = false;
                    later(phase, () -> check(npcs.get(0).getTraitNullable(PacketNPC.class).isTracking(player()), "viewer can return after filtering"));
                    break;
                case "cancel":
                    NPC cancelled = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, "Cancelled");
                    cancelNextSpawn = true;
                    cancelled.spawn(at);
                    later(phase, () -> {
                        check(cancelled.getEntity() == null, "cancelled spawn released its entity");
                        check(!cancelled.isSpawned(), "cancelled spawn remains despawned");
                        cancelled.destroy();
                    });
                    break;
                case "chunk":
                    Bukkit.getPluginManager().callEvent(new ChunkUnloadEvent(at.getChunk()));
                    later(phase, () -> check(npcs.get(0).isSpawned(), "chunk unload cannot despawn a virtual NPC"));
                    break;
                case "toggle":
                    NPC toggle = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, "Toggle");
                    toggle.removeTrait(PacketNPC.class);
                    toggle.spawn(at);
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (!toggle.getEntity().isValid()) {
                            getLogger().severe("[PACKET-TEST] toggle FAIL real mode did not spawn");
                        }
                        toggle.getOrAddTrait(PacketNPC.class);
                    }, 5);
                    later(phase, () -> {
                        check(toggle.isSpawned() && !toggle.getEntity().isValid(), "real NPC converted to packets");
                        toggle.destroy();
                    });
                    break;
                case "save":
                    CitizensAPI.getNPCRegistry().saveToStore();
                    later(phase, () -> {});
                    break;
                case "tabrefresh":
                    for (NPC npc : npcs) {
                        if (!(npc.getEntity() instanceof Player)) continue;
                        Player humanNPC = (Player) npc.getEntity();
                        NMS.sendTabListRemove(player(), Collections.singletonList(humanNPC));
                        NMS.sendTabListAdd(player(), humanNPC);
                        // Bukkit's update-display-name action must use the same persistent alias, too.
                        humanNPC.setPlayerListName("Temporary");
                    }
                    later(phase, () -> {
                        check("Dealer".equals(npcs.get(0).getRawName()), "Tab refresh preserves raw names");
                        check("\u00a78[NPC] Alex".equals(npcs.get(1).getFullName()), "Tab refresh preserves head names");
                        check("alex".equals(((SkinnableEntity) npcs.get(1).getEntity()).getSkinName()), "Tab alias preserves skin lookup");
                    });
                    break;
                case "reload":
                    List<Integer> ids = new ArrayList<>();
                    for (NPC npc : npcs) ids.add(npc.getId());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens reload");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens reload");
                    npcs.clear();
                    for (int id : ids) npcs.add(CitizensAPI.getNPCRegistry().getById(id));
                    later(phase, () -> {
                        check("Dealer".equals(npcs.get(0).getRawName()), "raw name survives reload");
                        check("\u00a78[NPC] Dealer".equals(npcs.get(0).getFullName()), "single prefix survives reload");
                    });
                    break;
                case "reconnect":
                    player().setGameMode(GameMode.CREATIVE);
                    player().teleport(at.clone().add(0, 0, 2));
                    later(phase, () -> check(npcs.get(0).getTraitNullable(PacketNPC.class).isTracking(player()), "reconnected viewer linked"));
                    break;
                case "cleanup":
                    for (NPC npc : npcs) {
                        int id = npc.getEntity().getEntityId();
                        npc.destroy();
                        check(npc.getEntity() == null, "backing object released");
                        check(PacketNPC.getByEntityId(id) == null, "interaction ID removed");
                    }
                    npcs.clear();
                    later(phase, () -> {
                        for (NPC helper : CitizensAPI.getTemporaryNPCRegistry()) {
                            check(!helper.isSpawned(), "holograms destroyed with parent");
                        }
                    });
                    break;
                default:
                    throw new IllegalArgumentException(phase);
            }
        } catch (Throwable error) {
            getLogger().severe("[PACKET-TEST] FAIL " + error);
            error.printStackTrace();
        }
        return true;
    }
}
