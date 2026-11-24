package cn.mcfhm.enchantx;

import cn.mcfhm.enchantx.affix.AffixData;
import cn.mcfhm.enchantx.affix.AffixDef;
import cn.mcfhm.enchantx.affix.AffixInstance;
import cn.mcfhm.enchantx.affix.AffixTier;
import cn.mcfhm.enchantx.item.AffixItems;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

/** 启动自检：不依赖玩家，验证掷点、存储、属性与 lore 处理。 */
public final class SelfTest {

    private SelfTest() {
    }

    public static void run(EnchantX plugin) {
        Logger log = plugin.getLogger();
        AffixItems items = plugin.items();
        int pass = 0;
        int fail = 0;

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        AffixData data = plugin.engine().roll(plugin.manager(), sword, items.capacity(sword.getType()),
                0.8, AffixData.empty(), null, plugin.settings().globalScale());
        ItemStack copy = sword.clone();
        items.write(copy, data);

        int capacity = items.capacity(sword.getType());
        pass += check(log, data.size() > 0, "掷点产出词条 " + data.size() + "/" + capacity);
        pass += check(log, data.size() <= capacity, "不超槽位 " + data.size() + " <= " + capacity);

        String raw = items.debugRaw(copy);
        AffixData back = AffixData.parse(raw);
        pass += check(log, back.size() == data.size(), "PDC 往返一致 (" + back.size() + " 条)");
        for (AffixInstance instance : back.list()) {
            pass += check(log, plugin.manager().get(instance.defId()) != null,
                    "回读词条可解析 " + instance.defId());
        }

        ItemMeta meta = copy.getItemMeta();
        List<String> lore = meta == null || meta.getLore() == null ? List.of() : meta.getLore();
        boolean header = !lore.isEmpty() && ChatColor.stripColor(lore.get(0)).startsWith("◈");
        pass += check(log, header, "lore 头部渲染");
        boolean lines = lore.stream().filter(l -> ChatColor.stripColor(l).startsWith("◆")).count() == data.size();
        pass += check(log, lines, "lore 行数与词条数一致");

        int modifiers = 0;
        if (meta != null) {
            for (Attribute attribute : org.bukkit.Registry.ATTRIBUTE) {
                Collection<AttributeModifier> mods = meta.getAttributeModifiers(attribute);
                if (mods == null) {
                    continue;
                }
                for (AttributeModifier modifier : mods) {
                    if (modifier.getKey() != null && "enchantx".equals(modifier.getKey().getNamespace())) {
                        modifiers++;
                    }
                }
            }
        }
        int expectedMods = 0;
        for (AffixInstance instance : data.list()) {
            AffixDef def = plugin.manager().get(instance.defId());
            if (def != null && def.isAttribute() && Math.abs(instance.value()) > 0.0001) {
                expectedMods++;
            }
        }
        pass += check(log, modifiers == expectedMods,
                "属性修饰符数量 " + modifiers + " = " + expectedMods);

        // 冲突判定
        AffixDef sharpness = plugin.manager().get("sharpness");
        AffixDef blunt = plugin.manager().get("blunt");
        if (sharpness != null && blunt != null) {
            AffixData withBlunt = AffixData.empty()
                    .add(new AffixInstance("blunt", 1, -1.0, 1));
            pass += check(log, !plugin.manager().canAdd(sharpness, withBlunt), "冲突组拦截剑刃类互斥");
            pass += check(log, plugin.manager().canAdd(sharpness, AffixData.empty()), "空装备可加锋利");
        }

        // 词条书
        AffixDef first = plugin.manager().defs().values().iterator().next();
        ItemStack book = items.createBook(first, first.maxTier());
        AffixItems.BookRef ref = items.readBook(book);
        pass += check(log, ref != null && ref.defId().equals(first.id()), "词条书读写");

        // 全量档位数值与渲染（按词条适用部位挑代表装备）
        int rendered = 0;
        int skipped = 0;
        int badTier = 0;
        int badConflict = 0;
        int badLore = 0;
        int badGroup = 0;
        for (AffixDef def : plugin.manager().defs().values()) {
            for (int tier : def.tierKeys()) {
                AffixTier t = def.tier(tier);
                if (t.min() > t.max()) {
                    badTier++;
                }
            }
            for (String conflict : def.conflicts()) {
                if (plugin.manager().get(conflict) == null) {
                    badConflict++;
                }
            }
            Material probeMaterial = probeMaterial(def);
            if (probeMaterial == null) {
                skipped++;
                continue;
            }
            AffixTier probeTier = def.tier(def.maxTier());
            AffixInstance instance = new AffixInstance(def.id(), def.maxTier(), probeTier.max(), 1);
            ItemStack probe = new ItemStack(probeMaterial);
            items.write(probe, AffixData.empty().add(instance));
            rendered++;
            ItemMeta probeMeta = probe.getItemMeta();
            List<String> probeLore = probeMeta == null || probeMeta.getLore() == null
                    ? List.of() : probeMeta.getLore();
            long probeLines = probeLore.stream()
                    .filter(l -> ChatColor.stripColor(l).startsWith("◆")).count();
            if (probeLines != 1) {
                badLore++;
                log.warning("[selftest] lore 异常：" + def.id() + " → " + probeLines + " 行");
            }
            if (def.isAttribute() && probeMeta != null) {
                Collection<AttributeModifier> mods = probeMeta.getAttributeModifiers(def.attribute());
                EquipmentSlotGroup expected = expectedGroup(probeMaterial);
                if (mods != null) {
                    for (AttributeModifier modifier : mods) {
                        if (modifier.getKey() != null
                                && "enchantx".equals(modifier.getKey().getNamespace())
                                && modifier.getSlotGroup() != expected) {
                            badGroup++;
                            log.warning("[selftest] 属性作用位异常：" + def.id()
                                    + " 期望 " + expected + " 实际 " + modifier.getSlotGroup());
                        }
                    }
                }
            }
        }
        pass += check(log, badGroup == 0, "属性修饰符作用位正确（异常 " + badGroup + "）");
        pass += check(log, badLore == 0, "全量单条 lore 渲染正常（异常 " + badLore + "）");
        pass += check(log, badTier == 0, "档位区间合法（非法 " + badTier + "）");
        pass += check(log, badConflict == 0, "conflicts 指向存在（悬空 " + badConflict + "）");
        pass += check(log, rendered + skipped == plugin.manager().size(),
                "全量词条渲染 " + rendered + "/" + plugin.manager().size()
                        + "（无对应装备 " + skipped + "）");
        log.info("自检完成：通过 " + pass + "，失败 " + fail);
        if (fail > 0) {
            log.warning("EnchantX 自检存在失败项，请检查上方日志");
        }
    }

    private static final java.util.Map<String, Material> PROBE = java.util.Map.ofEntries(
            java.util.Map.entry("SWORD", Material.DIAMOND_SWORD),
            java.util.Map.entry("AXE", Material.DIAMOND_AXE),
            java.util.Map.entry("PICKAXE", Material.DIAMOND_PICKAXE),
            java.util.Map.entry("SHOVEL", Material.DIAMOND_SHOVEL),
            java.util.Map.entry("HOE", Material.DIAMOND_HOE),
            java.util.Map.entry("HELMET", Material.DIAMOND_HELMET),
            java.util.Map.entry("CHESTPLATE", Material.DIAMOND_CHESTPLATE),
            java.util.Map.entry("LEGGINGS", Material.DIAMOND_LEGGINGS),
            java.util.Map.entry("BOOTS", Material.DIAMOND_BOOTS),
            java.util.Map.entry("BOW", Material.BOW),
            java.util.Map.entry("CROSSBOW", Material.CROSSBOW),
            java.util.Map.entry("TRIDENT", Material.TRIDENT),
            java.util.Map.entry("MACE", Material.MACE),
            java.util.Map.entry("SHIELD", Material.SHIELD),
            java.util.Map.entry("ELYTRA", Material.ELYTRA),
            java.util.Map.entry("FISHING_ROD", Material.FISHING_ROD)
    );

    private static EquipmentSlotGroup expectedGroup(Material material) {
        return switch (cn.mcfhm.enchantx.util.Mats.category(material)) {
            case "HELMET" -> EquipmentSlotGroup.HEAD;
            case "CHESTPLATE", "ELYTRA" -> EquipmentSlotGroup.CHEST;
            case "LEGGINGS" -> EquipmentSlotGroup.LEGS;
            case "BOOTS" -> EquipmentSlotGroup.FEET;
            default -> EquipmentSlotGroup.HAND;
        };
    }

    /** 按词条适用范围挑一件代表装备，用于渲染自检。 */
    private static Material probeMaterial(AffixDef def) {        for (String target : def.targets()) {
            if (target.equals("ANY")) {
                return Material.DIAMOND_SWORD;
            }
            Material material = PROBE.get(target);
            if (material != null) {
                return material;
            }
        }
        return null;
    }

    private static int check(Logger log, boolean condition, String message) {        if (condition) {
            log.info("[selftest] ✓ " + message);
            return 1;
        }
        log.warning("[selftest] ✗ " + message);
        return 0;
    }
}
