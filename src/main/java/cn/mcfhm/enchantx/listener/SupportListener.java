package cn.mcfhm.enchantx.listener;

import cn.mcfhm.enchantx.combat.Profile;
import cn.mcfhm.enchantx.combat.ProfileService;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** 耐久、经验、时运、自动熔炼、饱食与缓存失效。 */
public final class SupportListener implements Listener {

    private final ProfileService profiles;
    private final Random random = new Random();
    private final Map<String, String> smeltMap = new HashMap<>();
    private final java.util.Set<java.util.UUID> veinGuard = new java.util.HashSet<>();

    public SupportListener(ProfileService profiles) {
        this.profiles = profiles;
        smeltMap.put("RAW_IRON", "IRON_INGOT");
        smeltMap.put("RAW_GOLD", "GOLD_INGOT");
        smeltMap.put("RAW_COPPER", "COPPER_INGOT");
        smeltMap.put("COBBLESTONE", "STONE");
        smeltMap.put("COBBLED_DEEPSLATE", "DEEPSLATE");
        smeltMap.put("SAND", "GLASS");
        smeltMap.put("RED_SAND", "GLASS");
        smeltMap.put("CLAY_BALL", "BRICK");
        smeltMap.put("NETHERRACK", "NETHER_BRICK");
        smeltMap.put("ANCIENT_DEBRIS", "NETHERITE_SCRAP");
        smeltMap.put("WET_SPONGE", "SPONGE");
        smeltMap.put("CACTUS", "GREEN_DYE");
        smeltMap.put("KELP", "DRIED_KELP");
        smeltMap.put("SEA_PICKLE", "LIME_DYE");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (veinGuard.contains(player.getUniqueId())) {
            return;
        }
        double vein = profiles.profile(player).get("vein");
        if (vein <= 0) {
            return;
        }
        Block origin = event.getBlock();
        Material type = origin.getType();
        if (!isVeinTarget(type)) {
            return;
        }
        int limit = Math.min(10, Math.max(1, (int) Math.round(vein)));
        veinGuard.add(player.getUniqueId());
        try {
            java.util.ArrayDeque<Block> queue = new java.util.ArrayDeque<>();
            java.util.Set<String> visited = new java.util.HashSet<>();
            queue.add(origin);
            visited.add(blockKey(origin));
            int broken = 0;
            while (!queue.isEmpty() && broken < limit) {
                Block current = queue.poll();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            Block next = current.getRelative(dx, dy, dz);
                            if (next.getType() != type || !visited.add(blockKey(next))) {
                                continue;
                            }
                            if (next.getLocation().distanceSquared(origin.getLocation()) > 13.0) {
                                continue;
                            }
                            BlockBreakEvent probe = new BlockBreakEvent(next, player);
                            Bukkit.getPluginManager().callEvent(probe);
                            if (probe.isCancelled()) {
                                continue;
                            }
                            next.breakNaturally(player.getInventory().getItemInMainHand());
                            broken++;
                            queue.add(next);
                            if (!consumeTool(player)) {
                                return;
                            }
                            if (broken >= limit) {
                                break;
                            }
                        }
                        if (broken >= limit) {
                            break;
                        }
                    }
                    if (broken >= limit) {
                        break;
                    }
                }
            }
        } finally {
            veinGuard.remove(player.getUniqueId());
        }
    }

    /** 连锁额外挖的方块也吃耐久：工具碎了就停下。 */
    private boolean consumeTool(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType().isAir() || tool.getType().getMaxDurability() <= 0) {
            return true;
        }
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return true;
        }
        int next = damageable.getDamage() + 1;
        if (next >= tool.getType().getMaxDurability()) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.9f);
            return false;
        }
        damageable.setDamage(next);
        tool.setItemMeta(meta);
        return true;
    }

    private boolean isVeinTarget(Material material) {        String name = material.name();
        return name.contains("ORE")
                || name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE")
                || name.equals("ANCIENT_DEBRIS");
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + "|" + block.getX() + "|" + block.getY() + "|" + block.getZ();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        double save = profiles.profile(player).get("durability_save");
        if (save > 0 && random.nextDouble() < Math.min(0.9, save)) {
            event.setCancelled(true);
            return;
        }
        if (save < 0 && random.nextDouble() < Math.min(0.9, -save)) {
            event.setDamage(event.getDamage() + Math.max(1, (int) Math.round(-save * event.getDamage())));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExp(PlayerExpChangeEvent event) {
        double bonus = profiles.profile(event.getPlayer()).get("exp_bonus");
        if (bonus > 0) {
            event.setAmount((int) Math.max(0, Math.round(event.getAmount() * (1.0 + Math.min(1.0, bonus)))));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        Profile profile = profiles.profile(player);
        double fortune = profile.get("fortune");
        double smelt = profile.get("auto_smelt");
        if (fortune <= 0 && smelt <= 0) {
            return;
        }
        Location location = event.getBlock().getLocation().add(0.5, 0.5, 0.5);

        for (Item drop : event.getItems()) {
            ItemStack stack = drop.getItemStack();
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (smelt > 0 && random.nextDouble() < Math.min(0.95, smelt)) {
                ItemStack smelted = smelt(stack);
                if (smelted != null) {
                    drop.setItemStack(smelted);
                    stack = smelted;
                }
            }
            if (fortune > 0) {
                int extra = (int) Math.floor(fortune);
                if (random.nextDouble() < fortune - extra) {
                    extra++;
                }
                for (int i = 0; i < Math.min(3, extra); i++) {
                    ItemStack copy = stack.clone();
                    if (copy.getAmount() < copy.getMaxStackSize()) {
                        player.getWorld().dropItemNaturally(location, copy);
                    }
                }
            }
        }
    }

    private ItemStack smelt(ItemStack stack) {
        String target = smeltMap.get(stack.getType().name());
        if (target == null) {
            return null;
        }
        Material material = Material.matchMaterial(target);
        if (material == null) {
            return null;
        }
        // 带自定义名 / lore / PDC 的物品（商店货、任务物）要连元数据一起搬到产物上
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        boolean custom = meta != null && (meta.hasDisplayName() || meta.hasLore()
                || !meta.getPersistentDataContainer().isEmpty()
                || !meta.getEnchants().isEmpty());
        if (custom) {
            ItemStack out = stack.clone();
            out.setType(material);
            return out;
        }
        return new ItemStack(material, stack.getAmount());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getFoodLevel() >= player.getFoodLevel()) {
            return;
        }
        double save = profiles.profile(player).get("hunger_save");
        if (save > 0 && random.nextDouble() < Math.min(0.6, save)) {
            event.setFoodLevel(player.getFoodLevel());
        }
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        profiles.invalidate(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onHeldChange(PlayerItemHeldEvent event) {
        profiles.invalidate(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        profiles.invalidate(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        profiles.invalidate(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            profiles.invalidate(player.getUniqueId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        profiles.invalidate(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        profiles.invalidate(event.getPlayer().getUniqueId());
    }
}
