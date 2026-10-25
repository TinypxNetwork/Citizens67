package net.citizensnpcs.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.citizensnpcs.api.gui.CitizensInventoryClickEvent;
import net.citizensnpcs.api.gui.InventoryMenuPage;
import net.citizensnpcs.api.gui.InventoryMenuSlot;
import net.citizensnpcs.api.gui.MenuContext;
import net.citizensnpcs.api.npc.NPC;

/** Equipment menu click handling using APIs available in Bukkit 1.8.8. */
public abstract class EquipmentMenuPage extends InventoryMenuPage {
    private MenuContext context;
    protected final NPC npc;

    protected EquipmentMenuPage(NPC npc) {
        this.npc = npc;
    }

    private boolean applyClick(InventoryClickEvent event, int pickupAmount) {
        InventoryMenuSlot slot = context.getSlot(event.getRawSlot());
        Collection<InventoryAction> filter = slot.getFilter();
        if (filter == null && slot.getClickHandlers().isEmpty()
                || filter != null && !filter.contains(event.getAction())) {
            event.setCancelled(true);
            return false;
        }
        switch (event.getAction()) {
            case PICKUP_ALL:
            case PICKUP_HALF:
            case PICKUP_ONE:
            case PLACE_ALL:
            case PLACE_SOME:
            case PLACE_ONE:
                break;
            case PICKUP_SOME:
                if (pickupAmount < 0) {
                    pickupAmount = Math.min(event.getCurrentItem().getAmount(),
                            event.getCursor().getType().getMaxStackSize() - event.getCursor().getAmount());
                }
                break;
            default:
                // The API result calculator does not support swaps, hotbar keys or slot drops.
                event.setCancelled(true);
                return false;
        }
        CitizensInventoryClickEvent click = new CitizensInventoryClickEvent(event, pickupAmount);
        if (event.isCancelled())
            return false;
        for (Consumer<CitizensInventoryClickEvent> handler : new ArrayList<>(slot.getClickHandlers())) {
            handler.accept(click);
            if (event.isCancelled())
                return false;
        }
        onClick(slot, event);
        return !event.isCancelled();
    }

    @SuppressWarnings("deprecation")
    private void handleShiftClick(InventoryClickEvent event, boolean fromEquipment) {
        event.setCancelled(true);
        ItemStack source = event.getCurrentItem();
        if (source == null || source.getType() == Material.AIR)
            return;
        Inventory destination = fromEquipment ? event.getWhoClicked().getInventory() : context.getInventory();
        ItemStack cursor = event.getCursor();
        for (int i = 0; i < destination.getSize(); i++) {
            ItemStack current = destination.getItem(i);
            boolean empty = current == null || current.getType() == Material.AIR;
            if (!empty && !current.isSimilar(source))
                continue;
            int room = Math.min(source.getType().getMaxStackSize(), destination.getMaxStackSize())
                    - (empty ? 0 : current.getAmount());
            int amount = Math.min(source.getAmount(), room);
            if (amount <= 0)
                continue;
            ItemStack transferred = source.clone();
            transferred.setAmount(amount);
            InventoryAction action = fromEquipment
                    ? (amount == source.getAmount() ? InventoryAction.PICKUP_ALL : InventoryAction.PICKUP_SOME)
                    : (amount == source.getAmount() ? InventoryAction.PLACE_ALL : InventoryAction.PLACE_SOME);
            InventoryClickEvent click = new InventoryClickEvent(event.getView(), event.getSlotType(),
                    fromEquipment ? event.getRawSlot() : i, event.getClick(), action);
            boolean accepted;
            try {
                if (!fromEquipment) {
                    event.setCursor(transferred);
                }
                accepted = applyClick(click, amount);
            } finally {
                event.setCursor(cursor);
            }
            if (!accepted)
                continue;
            ItemStack merged = empty ? transferred : current.clone();
            if (!empty) {
                merged.setAmount(current.getAmount() + amount);
            }
            destination.setItem(i, merged);
            ItemStack remaining = source.clone();
            remaining.setAmount(source.getAmount() - amount);
            event.setCurrentItem(remaining.getAmount() == 0 ? null : remaining);
            if (fromEquipment && remaining.getAmount() > 0) {
                source = remaining;
                continue;
            }
            return;
        }
    }

    @Override
    public void initialise(MenuContext context) {
        this.context = context;
    }

    public void onInventoryClick(InventoryClickEvent event) {
        if (event.isCancelled() || !event.getInventory().equals(context.getInventory()))
            return;
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0)
            return;
        boolean fromEquipment = rawSlot < context.getInventory().getSize();
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            handleShiftClick(event, fromEquipment);
        } else if (fromEquipment) {
            applyClick(event, -1);
        }
    }
}
