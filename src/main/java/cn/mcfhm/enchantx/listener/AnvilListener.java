package cn.mcfhm.enchantx.listener;

import cn.mcfhm.enchantx.affix.AffixData;
import cn.mcfhm.enchantx.affix.AffixDef;
import cn.mcfhm.enchantx.affix.AffixInstance;
import cn.mcfhm.enchantx.affix.AffixTier;
import cn.mcfhm.enchantx.item.AffixItems;
import cn.mcfhm.enchantx.item.AffixItems.BookRef;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/** 铁砧：词条书移植、两件装备词条合并。 */
public final class AnvilListener implements Listener {

    private final AffixItems items;
    private final Random random = new Random();

    public AnvilListener(AffixItems items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        if (first == null || second == null || first.getType().isAir() || second.getType().isAir()) {
            return;
        }
        if (!items.isGear(first)) {
            return;
        }

        BookRef book = items.readBook(second);
        if (book != null) {
            applyBook(event, first, book);
            return;
        }

        AffixData other = items.read(second);
        if (other.isEmpty()) {
            return;
        }
        mergeItems(event, first, items.read(first), other);
    }

    private void applyBook(PrepareAnvilEvent event, ItemStack target, BookRef book) {
        AffixDef def = items.manager().get(book.defId());
        if (def == null) {
            return;
        }
        AffixData current = items.read(target);
        int capacity = items.capacity(target.getType());
        int slot = current.freeSlot(capacity);

        if (!items.manager().canAdd(def, current)) {
            event.setResult(null);
            event.getView().setRepairCost(39);
            return;
        }
        if (slot <= 0) {
            event.setResult(null);
            event.getView().setRepairCost(39);
            return;
        }

        AffixTier tier = def.tier(book.tier());
        double value = tier.roll(random) * items.settings().globalScale();
        AffixData result = current.copy().add(new AffixInstance(def.id(), book.tier(), value, slot));

        ItemStack out = target.clone();
        items.write(out, result);
        event.setResult(out);
        event.getView().setRepairCost(Math.max(1, book.tier() * 2));
    }

    private void mergeItems(PrepareAnvilEvent event, ItemStack target, AffixData base, AffixData other) {
        AffixData merged = base.copy();
        int capacity = items.capacity(target.getType());
        boolean changed = false;

        for (AffixInstance instance : other.list()) {
            AffixDef def = items.manager().get(instance.defId());
            if (def == null) {
                continue;
            }
            AffixInstance existing = merged.byId(def.id());
            if (existing != null) {
                if (instance.tier() > existing.tier()) {
                    merged.add(existing.upgrade(instance.tier(), instance.value()));
                    changed = true;
                }
                continue;
            }
            int slot = merged.freeSlot(capacity);
            if (slot <= 0 || !items.manager().canAdd(def, merged)) {
                continue;
            }
            merged.add(instance.withSlot(slot));
            changed = true;
        }

        if (!changed) {
            return;
        }
        ItemStack out = target.clone();
        items.write(out, merged);
        event.setResult(out);
        event.getView().setRepairCost(Math.max(2, other.size() * 2));
    }
}

