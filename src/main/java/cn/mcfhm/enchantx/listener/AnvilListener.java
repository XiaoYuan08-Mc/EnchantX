package cn.mcfhm.enchantx.listener;

import cn.mcfhm.enchantx.affix.AffixData;
import cn.mcfhm.enchantx.affix.AffixDef;
import cn.mcfhm.enchantx.affix.AffixInstance;
import cn.mcfhm.enchantx.affix.AffixTier;
import cn.mcfhm.enchantx.item.AffixItems;
import cn.mcfhm.enchantx.item.AffixItems.BookRef;
import cn.mcfhm.enchantx.util.Mats;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/** 铁砧：词条书移植、两件装备词条合并。 */
public final class AnvilListener implements Listener {

    private final AffixItems items;

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
        String category = Mats.category(target.getType());
        // 词条池必须匹配物品类别，否则武器词条能被贴到盔甲上（ProfileService 不区分类别，穿上就生效）
        if (!def.matches(category)) {
            event.setResult(null);
            event.getView().setRepairCost(39);
            return;
        }
        AffixData current = items.read(target);
        int capacity = items.capacity(target.getType());
        int slot = current.freeSlot(capacity);

        if (!items.manager().canAdd(def, current, category)) {
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
        double value = stableValue(def, tier, target, slot);
        AffixData result = current.copy().add(new AffixInstance(def.id(), book.tier(), value, slot));

        ItemStack out = baseResult(event, target).clone();
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
            if (slot <= 0 || !items.manager().canAdd(def, merged, Mats.category(target.getType()))) {
                continue;
            }
            merged.add(instance.withSlot(slot));
            changed = true;
        }

        if (!changed) {
            return;
        }
        ItemStack out = baseResult(event, target).clone();
        items.write(out, merged);
        event.setResult(out);
        event.getView().setRepairCost(Math.max(2, other.size() * 2));
    }

    /**
     * 数值由「装备现有词条串 + 词条 id + 档位 + 槽位」决定，同一对物品反复放上/取下拿到同一个值，
     * 避免把书放上去又拿下无限重掷。
     */
    private double stableValue(AffixDef def, AffixTier tier, ItemStack target, int slot) {
        long seed = 1125899906842597L;
        seed = seed * 31 + target.getType().name().hashCode();
        seed = seed * 31 + items.debugRaw(target).hashCode();
        seed = seed * 31 + def.id().hashCode();
        seed = seed * 31 + slot;
        return tier.roll(new Random(seed)) * items.settings().globalScale();
    }

    /**
     * 原版那次合成其实已经算出了结果（改名、耐久合并、原版附魔），在它基础上加词条，
     * 不要用一个裸 clone 整个替换掉，否则同一个铁砧操作里的改名 / 修复会失效。
     */
    private ItemStack baseResult(PrepareAnvilEvent event, ItemStack target) {
        ItemStack vanilla = event.getInventory().getResult();
        if (vanilla == null || vanilla.getType().isAir()) {
            return target;
        }
        return vanilla;
    }
}
