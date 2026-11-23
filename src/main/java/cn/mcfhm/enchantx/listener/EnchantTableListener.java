package cn.mcfhm.enchantx.listener;

import cn.mcfhm.enchantx.EnchantX;
import cn.mcfhm.enchantx.affix.AffixData;
import cn.mcfhm.enchantx.affix.AffixInstance;
import cn.mcfhm.enchantx.affix.RollEngine;
import cn.mcfhm.enchantx.config.Settings;
import cn.mcfhm.enchantx.item.AffixItems;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/** 附魔台入口：把"确定的附魔"改成"滚动的词条"。 */
public final class EnchantTableListener implements Listener {

    private final EnchantX plugin;
    private final AffixItems items;
    private final RollEngine engine;
    private final Random random = new Random();

    public EnchantTableListener(EnchantX plugin, AffixItems items, RollEngine engine) {
        this.plugin = plugin;
        this.items = items;
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        if (!items.isGear(item)) {
            return;
        }
        Settings settings = plugin.settings();
        int capacity = items.capacity(item.getType());
        double power = Math.min(1.0, Math.max(0.05, event.getExpLevelCost() / 30.0));

        switch (settings.mode()) {
            case AFFIX -> {
                try {
                    event.getEnchantsToAdd().clear();
                } catch (UnsupportedOperationException ignored) {
                    // 某些实现给的是不可变视图，忽略即可
                }
                AffixData data = engine.roll(items.manager(), item, capacity, power,
                        AffixData.empty(), null, settings.globalScale());
                ItemStack result = item.clone();
                items.write(result, data);
                event.setItem(result);
            }
            case HYBRID -> {
                if (random.nextDouble() >= settings.hybridChance()) {
                    return;
                }
                AffixData current = items.read(item);
                int slot = current.freeSlot(capacity);
                if (slot <= 0) {
                    return;
                }
                AffixInstance extra = engine.rollOne(items.manager(), item, current, capacity,
                        power, settings.hybridScale());
                if (extra == null) {
                    return;
                }
                AffixData next = current.copy().add(extra.withSlot(slot));
                ItemStack result = item.clone();
                items.write(result, next);
                event.setItem(result);
            }
            default -> {
            }
        }

        Player enchanter = event.getEnchanter();
        if (plugin.debugEnabled()) {
            plugin.getLogger().info("[enchant] " + enchanter.getName() + " lvl=" + event.getExpLevelCost()
                    + " mode=" + settings.mode() + " item=" + item.getType());
        }
    }
}
