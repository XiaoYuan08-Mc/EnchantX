package cn.mcfhm.enchantx.command;

import cn.mcfhm.enchantx.EnchantX;
import cn.mcfhm.enchantx.affix.AffixData;
import cn.mcfhm.enchantx.affix.AffixDef;
import cn.mcfhm.enchantx.affix.AffixInstance;
import cn.mcfhm.enchantx.affix.AffixTier;
import cn.mcfhm.enchantx.gui.ReforgeGui;
import cn.mcfhm.enchantx.item.AffixItems;
import cn.mcfhm.enchantx.util.Texts;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** /enchantx 主指令。 */
public final class EnchantXCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§8[§6词条§8] §r";

    private final EnchantX plugin;
    private final Random random = new Random();

    public EnchantXCommand(EnchantX plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该指令只能由玩家执行");
            return true;
        }
        if (!player.hasPermission("enchantx.use")) {
            player.sendMessage(PREFIX + "§c你没有权限");
            return true;
        }
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "info" -> info(player);
            case "list" -> list(player, args);
            case "reroll" -> reroll(player, args);
            case "reforge", "gui" -> new ReforgeGui(plugin, player).open();
            case "salvage" -> salvage(player);
            case "buy", "shop" -> buy(player, args);
            case "strip" -> strip(player);
            case "reload" -> reload(player);
            case "give" -> give(player, args);
            case "fragments", "frag" -> fragments(player, args);
            case "help" -> help(player);
            default -> player.sendMessage(PREFIX + "§7未知子指令，试试 §f/ex help");
        }
        return true;
    }

    private void info(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        AffixItems items = plugin.items();
        if (!items.isGear(held)) {
            player.sendMessage(PREFIX + "§7手持一件装备再看信息");
            return;
        }
        int capacity = items.capacity(held.getType());
        AffixData data = items.read(held);
        player.sendMessage(PREFIX + "§f" + held.getType().name() + " §8· §7词条 §f"
                + data.size() + "§8/§f" + capacity);
        for (AffixInstance instance : data.list()) {
            AffixDef def = items.manager().get(instance.defId());
            if (def == null) {
                continue;
            }
            String color = def.positive() ? Texts.tierColor(instance.tier()) : "§c";
            player.sendMessage("  §8" + instance.slot() + ". " + color + def.name() + " "
                    + Texts.roman(instance.tier()) + " §8· §7" + display(def, instance.value())
                    + " §8(" + def.effect() + ")");
        }
        if (data.isEmpty()) {
            player.sendMessage("  §8（没有词条）");
        }
        player.sendMessage(PREFIX + "§7碎片 §d" + plugin.fragments().get(player)
                + " §8· §7模式 §f" + plugin.settings().mode());
    }

    private String display(AffixDef def, double value) {
        return switch (def.display()) {
            case PERCENT -> Texts.percent(value);
            case SECONDS -> Texts.seconds(value);
            case INT -> String.valueOf((int) Math.round(value));
            case FLAT -> Texts.flat(value);
        };
    }

    private void list(Player player, String[] args) {
        String pool = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null;
        int page = 1;
        if (args.length >= 3) {
            try {
                page = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException ignored) {
            }
        }
        List<AffixDef> defs = new ArrayList<>();
        if (pool == null) {
            defs.addAll(plugin.manager().defs().values());
        } else {
            List<AffixDef> poolDefs = plugin.manager().pools().get(pool);
            if (poolDefs == null) {
                player.sendMessage(PREFIX + "§c没有这个池: " + pool);
                return;
            }
            defs.addAll(poolDefs);
        }
        int perPage = 12;
        int from = (page - 1) * perPage;
        int to = Math.min(defs.size(), from + perPage);
        player.sendMessage(PREFIX + "§7共 §f" + defs.size() + " §7条，第 §f" + page
                + " §7页 §8（池: " + (pool == null ? "全部" : pool) + "）");
        for (int i = from; i < to; i++) {
            AffixDef def = defs.get(i);
            String targets = String.join("/", def.targets());
            player.sendMessage(" §8" + (i + 1) + ". §f" + def.id() + " §7" + def.name()
                    + " §8[" + textsTiers(def) + "] §8" + targets);
        }
        if (to < defs.size()) {
            player.sendMessage("§7下一页：§f/ex list " + (pool == null ? "all" : pool) + " " + (page + 1));
        }
    }

    private String textsTiers(AffixDef def) {
        StringBuilder sb = new StringBuilder();
        for (int tier : def.tierKeys()) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(tier);
        }
        return sb.toString();
    }

    private void reroll(Player player, String[] args) {
        ItemStack held = player.getInventory().getItemInMainHand();
        AffixItems items = plugin.items();
        if (!items.isGear(held)) {
            player.sendMessage(PREFIX + "§7手持装备才能重掷");
            return;
        }
        int times = 1;
        if (args.length >= 2) {
            try {
                times = Math.max(1, Math.min(20, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
            }
        }
        int unit = plugin.settings().rerollCost();
        int total = unit * times;
        if (!plugin.fragments().take(player, total)) {
            player.sendMessage(PREFIX + "§c碎片不足，需要 §f" + total + " §c，当前 §f"
                    + plugin.fragments().get(player));
            return;
        }
        AffixData current = items.read(held);
        AffixData result = current;
        for (int i = 0; i < times; i++) {
            result = plugin.engine().roll(items.manager(), held, items.capacity(held.getType()),
                    plugin.settings().reforgePower(), result, null, plugin.settings().globalScale());
        }
        ItemStack copy = held.clone();
        items.write(copy, result);
        player.getInventory().setItemInMainHand(copy);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
        player.sendMessage(PREFIX + "§7重掷 " + times + " 次，消耗 §d" + total + " §7碎片");
    }

    private void salvage(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        AffixItems items = plugin.items();
        if (!items.isGear(held)) {
            player.sendMessage(PREFIX + "§7手持装备才能拆解");
            return;
        }
        AffixData data = items.read(held);
        if (data.isEmpty()) {
            player.sendMessage(PREFIX + "§7这件装备没有词条");
            return;
        }
        int fragments = 0;
        for (AffixInstance instance : data.list()) {
            fragments += instance.tier() * plugin.settings().salvagePerTier();
        }
        ItemStack copy = held.clone();
        items.strip(copy);
        player.getInventory().setItemInMainHand(copy);
        plugin.fragments().add(player, fragments);
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.8f, 1.0f);
        player.sendMessage(PREFIX + "§7拆解完成，返还 §d" + fragments + " §7碎片");
    }

    private void strip(Player player) {
        if (!player.hasPermission("enchantx.admin")) {
            player.sendMessage(PREFIX + "§c没有权限");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!plugin.items().isGear(held)) {
            player.sendMessage(PREFIX + "§7手持装备才能清理");
            return;
        }
        ItemStack copy = held.clone();
        plugin.items().strip(copy);
        player.getInventory().setItemInMainHand(copy);
        player.sendMessage(PREFIX + "§7已清空该装备的词条（不返还碎片）");
    }

    private void reload(Player player) {
        if (!player.hasPermission("enchantx.admin")) {
            player.sendMessage(PREFIX + "§c没有权限");
            return;
        }
        plugin.reloadAll();
        player.sendMessage(PREFIX + "§a已重载，词条数 §f" + plugin.manager().size()
                + " §7模式 §f" + plugin.settings().mode());
    }

    private void give(Player player, String[] args) {
        if (!player.hasPermission("enchantx.admin")) {
            player.sendMessage(PREFIX + "§c没有权限");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(PREFIX + "§7用法: /ex give <词条id|池> [档位] [数量]");
            return;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        int tier = args.length >= 3 ? parseInt(args[2], 2) : 2;
        int amount = args.length >= 4 ? Math.max(1, Math.min(64, parseInt(args[3], 1))) : 1;

        List<AffixDef> poolDefs = plugin.manager().pools().get(key);
        AffixDef def = plugin.manager().get(key);
        List<AffixDef> targets = new ArrayList<>();
        if (def != null) {
            targets.add(def);
        } else if (poolDefs != null && !poolDefs.isEmpty()) {
            targets.addAll(poolDefs);
        } else {
            player.sendMessage(PREFIX + "§c找不到词条或池: " + key);
            return;
        }

        for (int i = 0; i < amount; i++) {
            AffixDef chosen = targets.get(random.nextInt(targets.size()));
            int chosenTier = Math.max(1, Math.min(chosen.maxTier(), tier));
            ItemStack book = plugin.items().createBook(chosen, chosenTier);
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItemNaturally(player.getLocation(), book);
            } else {
                player.getInventory().addItem(book);
            }
        }
        player.sendMessage(PREFIX + "§7发放 §f" + amount + " §7本词条书（档 §f" + tier + "）");
    }

    /** 用碎片直接购买词条书：/ex buy <池|词条id|any> [档位]。 */
    private void buy(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + "§7用法: /ex buy <池|词条id|any> [档位]");
            player.sendMessage(PREFIX + "§7当前碎片 §d" + plugin.fragments().get(player)
                    + " §8· §7单价 §d" + plugin.settings().buyCost());
            return;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        List<AffixDef> targets = new ArrayList<>();
        if (key.equals("any") || key.equals("all")) {
            targets.addAll(plugin.manager().defs().values());
        } else {
            AffixDef def = plugin.manager().get(key);
            if (def != null) {
                targets.add(def);
            } else {
                List<AffixDef> pool = plugin.manager().pools().get(key);
                if (pool != null) {
                    targets.addAll(pool);
                }
            }
        }
        if (targets.isEmpty()) {
            player.sendMessage(PREFIX + "§c找不到词条或池: " + key);
            return;
        }
        int cost = plugin.settings().buyCost();
        if (!plugin.fragments().take(player, cost)) {
            player.sendMessage(PREFIX + "§c碎片不足，需要 §f" + cost + " §c，当前 §f"
                    + plugin.fragments().get(player));
            return;
        }
        AffixDef chosen = targets.get(random.nextInt(targets.size()));
        int tier = args.length >= 3 ? Math.max(1, parseInt(args[2], plugin.settings().buyTier()))
                : plugin.settings().buyTier();
        tier = Math.min(tier, chosen.maxTier());
        ItemStack book = plugin.items().createBook(chosen, tier);
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), book);
        } else {
            player.getInventory().addItem(book);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.6f);
        player.sendMessage(PREFIX + "§7购得 §f" + chosen.name() + " " + Texts.roman(tier)
                + " §7，消耗 §d" + cost + " §7碎片");
    }

    private void fragments(Player player, String[] args) {
        if (!player.hasPermission("enchantx.admin")) {
            player.sendMessage(PREFIX + "§c没有权限");
            return;
        }
        if (args.length < 3) {
            player.sendMessage(PREFIX + "§7用法: /ex fragments <add|set|take> <数量>");
            return;
        }
        int amount = Math.max(0, parseInt(args[2], 0));
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> plugin.fragments().add(player, amount);
            case "set" -> plugin.fragments().set(player, amount);
            case "take" -> plugin.fragments().take(player, amount);
            default -> {
                player.sendMessage(PREFIX + "§7只支持 add / set / take");
                return;
            }
        }
        player.sendMessage(PREFIX + "§7当前碎片 §d" + plugin.fragments().get(player));
    }

    private void help(Player player) {
        player.sendMessage(PREFIX + "§7可用指令：");
        player.sendMessage(" §f/ex info §8- §7查看手持装备词条");
        player.sendMessage(" §f/ex list [池] [页] §8- §7词条图鉴");
        player.sendMessage(" §f/ex reforge §8- §7打开重铸台");
        player.sendMessage(" §f/ex reroll [次数] §8- §7快速重掷");
        player.sendMessage(" §f/ex salvage §8- §7拆解返还碎片");
        player.sendMessage(" §f/ex buy <池|id|any> [档] §8- §7用碎片买词条书");
        player.sendMessage(" §f/ex give <id|池> [档] [数量] §8- §7发词条书");
        player.sendMessage(" §f/ex fragments <add|set|take> <数量> §8- §7管理碎片");
        player.sendMessage(" §f/ex reload §8- §7重载配置");
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("info", "list", "reforge", "reroll", "salvage", "buy", "give",
                    "fragments", "strip", "reload", "help"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return filter(new ArrayList<>(plugin.manager().pools().keySet()), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("buy")) {
            List<String> options = new ArrayList<>(plugin.manager().pools().keySet());
            options.add("any");
            return filter(options, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("buy")) {
            return filter(List.of("1", "2", "3", "4", "5"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(new ArrayList<>(plugin.manager().defs().keySet()), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("fragments")) {
            return filter(List.of("add", "set", "take"), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(option);
            }
        }
        return out;
    }
}
