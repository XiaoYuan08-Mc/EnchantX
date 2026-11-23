package cn.mcfhm.enchantx.item;

import cn.mcfhm.enchantx.affix.AffixData;
import cn.mcfhm.enchantx.affix.AffixDef;
import cn.mcfhm.enchantx.affix.AffixInstance;
import cn.mcfhm.enchantx.affix.AffixManager;
import cn.mcfhm.enchantx.config.Settings;
import cn.mcfhm.enchantx.util.Mats;
import cn.mcfhm.enchantx.util.Texts;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** 物品侧的词条读写：PDC 存储、属性修饰符、lore 渲染、词条书。 */
public final class AffixItems {

    private static final String NS = "enchantx";
    private static final String MOD_PREFIX = "ax_";
    private static final String HEADER_MARK = "◈";
    private static final String LINE_MARK = "◆";

    private final JavaPlugin plugin;
    private final AffixManager manager;
    private Settings settings;
    private final NamespacedKey dataKey;
    private final NamespacedKey bookKey;

    public AffixItems(JavaPlugin plugin, AffixManager manager, Settings settings) {
        this.plugin = plugin;
        this.manager = manager;
        this.settings = settings;
        this.dataKey = new NamespacedKey(plugin, "affix");
        this.bookKey = new NamespacedKey(plugin, "book");
    }

    public AffixManager manager() {
        return manager;
    }

    public Settings settings() {
        return settings;
    }

    public void updateSettings(Settings newSettings) {
        this.settings = newSettings;
    }

    public boolean isGear(ItemStack item) {
        return item != null && item.getType() != Material.AIR && Mats.isGear(item.getType());
    }

    public int capacity(Material material) {
        int rank = Mats.materialRank(material, settings.slotsByMaterial(), settings.defaultSlots());
        return Math.max(1, Math.min(6, rank));
    }

    public AffixData read(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return AffixData.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return AffixData.empty();
        }
        String raw = meta.getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
        return AffixData.parse(raw);
    }

    public boolean hasAffixes(ItemStack item) {
        return !read(item).isEmpty();
    }

    /** 调试/自检用：直接返回 PDC 里的原始串。 */
    public String debugRaw(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return "";
        }
        String raw = meta.getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
        return raw == null ? "" : raw;
    }

    public void write(ItemStack item, AffixData data) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        stripModifiers(meta);
        stripLore(meta);
        if (data == null || data.isEmpty()) {
            meta.getPersistentDataContainer().remove(dataKey);
            meta.setEnchantmentGlintOverride(null);
        } else {
            meta.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, data.encode());
            applyModifiers(meta, data, item.getType());
            renderLore(meta, data, capacity(item.getType()));
            if (!meta.hasEnchants()) {
                meta.setEnchantmentGlintOverride(Boolean.TRUE);
            }
        }
        item.setItemMeta(meta);
    }

    /** 彻底移除词条（属性、lore、标记）。 */
    public AffixData strip(ItemStack item) {
        AffixData old = read(item);
        write(item, AffixData.empty());
        return old;
    }

    private void stripModifiers(ItemMeta meta) {
        for (Attribute attribute : org.bukkit.Registry.ATTRIBUTE) {
            Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(attribute);
            if (modifiers == null || modifiers.isEmpty()) {
                continue;
            }
            List<AttributeModifier> ours = new ArrayList<>();
            for (AttributeModifier modifier : modifiers) {
                NamespacedKey key = modifier.getKey();
                if (key != null && NS.equals(key.getNamespace()) && key.getKey().startsWith(MOD_PREFIX)) {
                    ours.add(modifier);
                }
            }
            for (AttributeModifier modifier : ours) {
                meta.removeAttributeModifier(attribute, modifier);
            }
        }
    }

    private void applyModifiers(ItemMeta meta, AffixData data, Material material) {
        EquipmentSlotGroup group = slotGroup(material);
        for (AffixInstance instance : data.list()) {
            AffixDef def = manager.get(instance.defId());
            if (def == null || !def.isAttribute()) {
                continue;
            }
            double amount = instance.value();
            if (def.operation() == AttributeModifier.Operation.ADD_NUMBER && Math.abs(amount) < 0.0001) {
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, MOD_PREFIX + instance.slot() + "_"
                    + def.id().toLowerCase(Locale.ROOT));
            meta.addAttributeModifier(def.attribute(), new AttributeModifier(
                    key, amount, def.operation(), group));
        }
    }

    /** 属性只在"该穿/该拿"的位置生效：盔甲部件按部位，其余按手持。 */
    private EquipmentSlotGroup slotGroup(Material material) {
        if (material == null) {
            return EquipmentSlotGroup.ANY;
        }
        return switch (Mats.category(material)) {
            case "HELMET" -> EquipmentSlotGroup.HEAD;
            case "CHESTPLATE", "ELYTRA" -> EquipmentSlotGroup.CHEST;
            case "LEGGINGS" -> EquipmentSlotGroup.LEGS;
            case "BOOTS" -> EquipmentSlotGroup.FEET;
            default -> EquipmentSlotGroup.HAND;
        };
    }

    private void stripLore(ItemMeta meta) {
        if (!meta.hasLore()) {
            return;
        }
        List<String> lore = meta.getLore();
        if (lore == null) {
            return;
        }
        List<String> kept = new ArrayList<>(lore.size());
        for (String line : lore) {
            if (isOurLine(line)) {
                continue;
            }
            kept.add(line);
        }
        if (kept.isEmpty()) {
            meta.setLore(new ArrayList<>());
        } else {
            meta.setLore(kept);
        }
    }

    private static boolean isOurLine(String line) {
        String plain = ChatColor.stripColor(line);
        return plain != null && (plain.startsWith(HEADER_MARK) || plain.startsWith(LINE_MARK));
    }

    private void renderLore(ItemMeta meta, AffixData data, int capacity) {
        List<String> body = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        List<String> block = new ArrayList<>();
        block.add("§8◈ §7词条 §f" + data.size() + "§8/§f" + capacity + " §8· " + qualityText(data));

        for (AffixInstance instance : data.list()) {
            AffixDef def = manager.get(instance.defId());
            if (def == null) {
                continue;
            }
            String nameColor = def.positive() ? Texts.tierColor(instance.tier()) : "§c";
            block.add("§8◆ " + nameColor + Texts.roman(instance.tier()) + " " + def.name()
                    + " §8" + valueText(def, instance.value()));
        }
        body.addAll(0, block);
        meta.setLore(body);
    }

    private String valueText(AffixDef def, double value) {
        String sign = value >= 0 ? "+" : "-";
        double abs = Math.abs(value);
        String text = switch (def.display()) {
            case PERCENT -> Texts.percent(abs);
            case SECONDS -> Texts.seconds(abs);
            case INT -> String.valueOf((int) Math.round(abs));
            case FLAT -> Texts.flat(abs);
        };
        return sign + text;
    }

    private String qualityText(AffixData data) {
        int score = 0;
        for (AffixInstance instance : data.list()) {
            score += instance.tier();
        }
        if (score <= 2) {
            return "§7粗制";
        }
        if (score <= 5) {
            return "§f普通";
        }
        if (score <= 8) {
            return "§a精良";
        }
        if (score <= 11) {
            return "§9稀有";
        }
        if (score <= 14) {
            return "§5史诗";
        }
        return "§6传说";
    }

    /** 生成一本词条书：铁砧左格放装备、右格放本书，敲一下即可移植。 */
    public ItemStack createBook(AffixDef def, int tier) {
        ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName("§d词条书 §8· §f" + def.name() + " " + Texts.roman(tier));
        List<String> lore = new ArrayList<>();
        if (!def.desc().isBlank()) {
            lore.add("§7" + def.desc());
        }
        var t = def.tier(tier);
        lore.add("§8区间 §7" + Texts.flat(t.min()) + " ~ " + Texts.flat(t.max())
                + " §8· §7占 1 槽");
        lore.add("§8铁砧：左放装备、右放本书");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(bookKey, PersistentDataType.STRING, def.id() + ":" + tier);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        stack.setItemMeta(meta);
        return stack;
    }

    public BookRef readBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(bookKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(":");
        if (parts.length < 2) {
            return null;
        }
        try {
            return new BookRef(parts[0], Integer.parseInt(parts[1]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public void clearBookTag(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().remove(bookKey);
        item.setItemMeta(meta);
    }

    public record BookRef(String defId, int tier) {
    }
}
