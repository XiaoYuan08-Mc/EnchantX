package cn.mcfhm.enchantx.util;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Map;

/** 材质分类与槽位容量判定。 */
public final class Mats {

    private Mats() {
    }

    public static String category(Material m) {
        if (m == null) {
            return "OTHER";
        }
        String n = m.name();
        if (n.equals("SHIELD")) return "SHIELD";
        if (n.equals("ELYTRA")) return "ELYTRA";
        if (n.contains("CROSSBOW")) return "CROSSBOW";
        if (n.equals("BOW")) return "BOW";
        if (n.equals("TRIDENT")) return "TRIDENT";
        if (n.equals("FISHING_ROD")) return "FISHING_ROD";
        if (n.equals("MACE")) return "MACE";
        if (n.equals("SPEAR")) return "SPEAR";
        if (n.contains("PICKAXE")) return "PICKAXE";
        if (n.contains("SHOVEL")) return "SHOVEL";
        if (n.contains("AXE")) return "AXE";
        if (n.contains("SWORD")) return "SWORD";
        if (n.contains("HOE")) return "HOE";
        if (n.contains("HELMET")) return "HELMET";
        if (n.contains("CHESTPLATE")) return "CHESTPLATE";
        if (n.contains("LEGGINGS")) return "LEGGINGS";
        if (n.contains("BOOTS")) return "BOOTS";
        return "OTHER";
    }

    private static final java.util.Set<String> GEAR = java.util.Set.of(
            "SWORD", "AXE", "PICKAXE", "SHOVEL", "HOE", "HELMET", "CHESTPLATE",
            "LEGGINGS", "BOOTS", "BOW", "CROSSBOW", "TRIDENT", "ELYTRA", "SHIELD",
            "FISHING_ROD", "MACE", "SPEAR"
    );

    public static boolean isGear(Material m) {
        return GEAR.contains(category(m));
    }

    /** 材质档位：用于槽位与管理台展示。0 表示未知材质，走默认值。 */
    public static int materialRank(Material m, Map<String, Integer> byPrefix, int fallback) {
        if (m == null) {
            return fallback;
        }
        String n = m.name().toUpperCase(Locale.ROOT);
        for (Map.Entry<String, Integer> e : byPrefix.entrySet()) {
            if (n.startsWith(e.getKey().toUpperCase(Locale.ROOT))) {
                return e.getValue();
            }
        }
        return fallback;
    }
}
