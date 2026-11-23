package cn.mcfhm.enchantx.gui;

import cn.mcfhm.enchantx.EnchantX;
import cn.mcfhm.enchantx.affix.AffixData;
import cn.mcfhm.enchantx.affix.AffixDef;
import cn.mcfhm.enchantx.affix.AffixInstance;
import cn.mcfhm.enchantx.affix.AffixTier;
import cn.mcfhm.enchantx.config.Settings;
import cn.mcfhm.enchantx.item.AffixItems;
import cn.mcfhm.enchantx.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** 词条重铸台：重掷、锁定、升档、拆解。 */
public final class ReforgeGui implements InventoryHolder {

    private static final int SIZE = 27;
    private static final int SLOT_ITEM = 4;
    private static final int SLOT_AFFIX_START = 10;
    private static final int SLOT_FRAGMENTS = 18;
    private static final int SLOT_REROLL = 19;
    private static final int SLOT_CLOSE = 22;
    private static final int SLOT_SALVAGE = 24;
    private static final int MAX_VISIBLE = 6;

    private final EnchantX plugin;
    private final Player player;
    private final Set<Integer> locked = new HashSet<>();
    private final Random random = new Random();
    private Inventory inventory;

    public ReforgeGui(EnchantX plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, SIZE,
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().deserialize("§8词条重铸台"));
        render();
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void render() {
        AffixItems items = plugin.items();
        Settings settings = plugin.settings();
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, "§8", List.of());
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!items.isGear(held)) {
            inventory.setItem(SLOT_ITEM, icon(Material.BARRIER, "§c请手持装备", List.of("§7关闭后手持要重铸的装备再开")));
            inventory.setItem(SLOT_REROLL, icon(Material.ANVIL, "§8重掷", List.of("§7需要手持装备")));
            inventory.setItem(SLOT_SALVAGE, icon(Material.GRINDSTONE, "§8拆解", List.of("§7需要手持装备")));
            inventory.setItem(SLOT_CLOSE, icon(Material.BARRIER, "§7关闭", List.of()));
            inventory.setItem(SLOT_FRAGMENTS, fragmentIcon());
            return;
        }

        int capacity = items.capacity(held.getType());
        AffixData data = items.read(held);
        inventory.setItem(SLOT_ITEM, held.clone());

        for (int i = 0; i < MAX_VISIBLE; i++) {
            int slot = i + 1;
            int guiSlot = SLOT_AFFIX_START + i;
            if (slot > capacity) {
                inventory.setItem(guiSlot, icon(Material.BLACK_STAINED_GLASS_PANE, "§8—", List.of()));
                continue;
            }
            AffixInstance instance = data.bySlot(slot);
            if (instance == null) {
                inventory.setItem(guiSlot, icon(Material.GRAY_STAINED_GLASS_PANE,
                        "§8槽位 " + slot, List.of("§7空")));
            } else {
                inventory.setItem(guiSlot, affixIcon(items, instance, locked.contains(slot)));
            }
        }

        int cost = rerollCost();
        inventory.setItem(SLOT_REROLL, icon(Material.ANVIL, "§e重掷",
                List.of("§7消耗 §f" + cost + " §7碎片", "§7保留已锁定的词条")));
        inventory.setItem(SLOT_SALVAGE, icon(Material.GRINDSTONE, "§c拆解",
                List.of("§7清空词条并返还碎片")));
        inventory.setItem(SLOT_CLOSE, icon(Material.BARRIER, "§7关闭", List.of()));
        inventory.setItem(SLOT_FRAGMENTS, fragmentIcon());
    }

    private int rerollCost() {
        Settings settings = plugin.settings();
        int activeLocks = 0;
        for (int slot : locked) {
            if (slot <= MAX_VISIBLE) {
                activeLocks++;
            }
        }
        return settings.rerollCost() + activeLocks * settings.lockCost();
    }

    private ItemStack fragmentIcon() {
        return icon(Material.AMETHYST_SHARD, "§d碎片",
                List.of("§7持有 §f" + plugin.fragments().get(player)));
    }

    private ItemStack affixIcon(AffixItems items, AffixInstance instance, boolean isLocked) {
        AffixDef def = items.manager().get(instance.defId());
        if (def == null) {
            return icon(Material.BARRIER, "§c未知词条", List.of());
        }
        String color = def.positive() ? Texts.tierColor(instance.tier()) : "§c";
        String value = def.display() == cn.mcfhm.enchantx.affix.Display.PERCENT
                ? Texts.percent(instance.value())
                : def.display() == cn.mcfhm.enchantx.affix.Display.SECONDS
                ? Texts.seconds(instance.value())
                : def.display() == cn.mcfhm.enchantx.affix.Display.INT
                ? String.valueOf((int) Math.round(instance.value()))
                : Texts.flat(instance.value());

        List<String> lore = new java.util.ArrayList<>();
        lore.add("§8数值 §7" + value + " §8· §7档 §f" + Texts.roman(instance.tier()));
        if (!def.desc().isBlank()) {
            lore.add("§8" + def.desc());
        }
        lore.add("§8左键 §f升档 §8· §7右键 " + (isLocked ? "解锁" : "锁定"));
        if (isLocked) {
            lore.add("§b已锁定，重掷时保留");
        }
        String name = (isLocked ? "§b🔒 " : "") + color + def.name() + " " + Texts.roman(instance.tier());
        return icon(Material.PAPER, name, lore);
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(name));
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public void handle(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= SLOT_AFFIX_START && slot < SLOT_AFFIX_START + MAX_VISIBLE) {
            int index = slot - SLOT_AFFIX_START + 1;
            if (event.isRightClick()) {
                toggleLock(index);
            } else {
                upgrade(index);
            }
            return;
        }
        if (slot == SLOT_REROLL) {
            reroll();
            return;
        }
        if (slot == SLOT_SALVAGE) {
            salvage();
            return;
        }
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
        }
    }

    private ItemStack held() {
        return player.getInventory().getItemInMainHand();
    }

    private boolean validHeld() {
        if (plugin.items().isGear(held())) {
            return true;
        }
        message("§c请手持需要重铸的装备");
        return false;
    }

    private void toggleLock(int slot) {
        if (!validHeld()) {
            return;
        }
        AffixData data = plugin.items().read(held());
        if (data.bySlot(slot) == null) {
            return;
        }
        if (!locked.remove(slot)) {
            locked.add(slot);
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        render();
    }

    private void reroll() {
        if (!validHeld()) {
            return;
        }
        AffixItems items = plugin.items();
        Settings settings = plugin.settings();
        int cost = rerollCost();
        if (!plugin.fragments().take(player, cost)) {
            message("§c碎片不足，需要 §f" + cost + " §c，当前 §f" + plugin.fragments().get(player));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
            return;
        }
        ItemStack base = held();
        AffixData current = items.read(base);
        AffixData next = plugin.engine().roll(items.manager(), base, items.capacity(base.getType()),
                settings.reforgePower(), current, locked, settings.globalScale());
        apply(next);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.1f);
        message("§7已重掷，消耗 §f" + cost + " §7碎片");
        render();
    }

    private void upgrade(int slot) {
        if (!validHeld()) {
            return;
        }
        AffixItems items = plugin.items();
        AffixData data = items.read(held());
        AffixInstance instance = data.bySlot(slot);
        if (instance == null) {
            message("§7这个槽位是空的");
            return;
        }
        AffixDef def = items.manager().get(instance.defId());
        if (def == null) {
            return;
        }
        if (instance.tier() >= def.maxTier()) {
            message("§e已经是最高档");
            return;
        }
        int newTier = instance.tier() + 1;
        int cost = plugin.settings().upgradeCost() * newTier;
        if (!plugin.fragments().take(player, cost)) {
            message("§c碎片不足，升档需要 §f" + cost + " §c，当前 §f" + plugin.fragments().get(player));
            return;
        }
        AffixTier tier = def.tier(newTier);
        double value = tier.roll(random) * plugin.settings().globalScale();
        AffixData next = data.copy().add(instance.upgrade(newTier, value));
        apply(next);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.6f);
        message("§a" + def.name() + " 已升到 §f" + Texts.roman(newTier) + " §7（消耗 " + cost + " 碎片）");
        render();
    }

    private void salvage() {
        if (!validHeld()) {
            return;
        }
        AffixItems items = plugin.items();
        AffixData data = items.read(held());
        if (data.isEmpty()) {
            message("§7这件装备没有词条");
            return;
        }
        int fragments = 0;
        for (AffixInstance instance : data.list()) {
            fragments += instance.tier() * plugin.settings().salvagePerTier();
        }
        ItemStack copy = held().clone();
        items.strip(copy);
        player.getInventory().setItemInMainHand(copy);
        plugin.fragments().add(player, fragments);
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.7f, 1.0f);
        message("§7已拆解，返还 §d" + fragments + " §7碎片");
        player.closeInventory();
    }

    private void apply(AffixData data) {
        ItemStack copy = held().clone();
        plugin.items().write(copy, data);
        player.getInventory().setItemInMainHand(copy);
    }

    private void message(String text) {
        player.sendMessage("§8[§6词条§8] §r" + text);
    }

    /** 注册一次即可，用于接管重铸台的点击。 */
    public static final class Handler implements Listener {

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof ReforgeGui gui)) {
                return;
            }
            boolean top = event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getInventory());
            // 顶部格子完全由插件接管；底部背包放行，让玩家能整理自己东西，
            // 但"塞进界面"的几种操作必须拦：Shift 移动、双击收集、副手交换。
            boolean movesIntoGui = event.isShiftClick()
                    || event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR
                    || event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND;
            if (top || movesIntoGui) {
                event.setCancelled(true);
            }
            if (top) {
                gui.handle(event);
            }
        }
    }
}
