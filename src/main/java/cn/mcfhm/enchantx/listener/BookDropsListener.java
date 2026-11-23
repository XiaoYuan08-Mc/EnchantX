package cn.mcfhm.enchantx.listener;

import cn.mcfhm.enchantx.EnchantX;
import cn.mcfhm.enchantx.affix.AffixDef;
import cn.mcfhm.enchantx.item.AffixItems;
import cn.mcfhm.enchantx.util.Texts;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 词条书的野生产出：杀怪、箱子、钓鱼。 */
public final class BookDropsListener implements Listener {

    private final EnchantX plugin;
    private final AffixItems items;
    private final Random random = new Random();

    public BookDropsListener(EnchantX plugin, AffixItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        if (random.nextDouble() >= plugin.settings().bookDropChance()) {
            return;
        }
        BookRoll roll = randomBook(1, 3);
        if (roll == null) {
            return;
        }
        give(killer, roll.book(), roll);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        if (random.nextDouble() >= plugin.settings().bookLootChance()) {
            return;
        }
        BookRoll roll = randomBook(1, 4);
        if (roll == null) {
            return;
        }
        List<ItemStack> loot = new ArrayList<>(event.getLoot());
        loot.add(roll.book());
        event.setLoot(loot);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (random.nextDouble() >= plugin.settings().bookFishChance()) {
            return;
        }
        BookRoll roll = randomBook(1, 3);
        if (roll == null) {
            return;
        }
        give(event.getPlayer(), roll.book(), roll);
    }

    private void give(Player player, ItemStack book, BookRoll roll) {
        Location location = player.getLocation();
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(location, book);
        } else {
            player.getInventory().addItem(book);
        }
        player.sendActionBar("§d词条书 §8· §f" + roll.def().name() + " " + Texts.roman(roll.tier()));
    }

    private BookRoll randomBook(int minTier, int maxTier) {
        List<AffixDef> defs = new ArrayList<>(plugin.manager().defs().values());
        if (defs.isEmpty()) {
            return null;
        }
        AffixDef def = defs.get(random.nextInt(defs.size()));
        int cap = Math.min(def.maxTier(), maxTier);
        if (cap < minTier) {
            cap = def.maxTier();
        }
        int floor = Math.min(minTier, cap);
        double r = Math.pow(random.nextDouble(), 2.2);
        int tier = floor + (int) Math.floor(r * (cap - floor + 1));
        tier = Math.max(1, Math.min(cap, tier));
        return new BookRoll(def, tier, items.createBook(def, tier));
    }

    /** 一次掉落结果：词条、档位与成品书。 */
    private record BookRoll(AffixDef def, int tier, ItemStack book) {
    }
}
