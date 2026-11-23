package cn.mcfhm.enchantx.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 插件运行配置。 */
public final class Settings {

    public enum Mode {
        AFFIX,
        HYBRID,
        VANILLA;

        public static Mode parse(String raw) {
            if (raw == null) {
                return HYBRID;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return HYBRID;
            }
        }
    }

    private final Mode mode;
    private final double hybridChance;
    private final double hybridScale;
    private final double globalScale;
    private final double reforgePower;
    private final int rerollCost;
    private final int lockCost;
    private final int upgradeCost;
    private final int salvagePerTier;
    private final boolean debug;
    private final Map<String, Integer> slotsByMaterial = new LinkedHashMap<>();
    private final int defaultSlots;
    private final double bookDropChance;
    private final double bookLootChance;
    private final double bookFishChance;
    private final int buyCost;
    private final int buyTier;

    private Settings(FileConfiguration c) {
        this.mode = Mode.parse(c.getString("settings.mode", "HYBRID"));
        this.hybridChance = clamp(c.getDouble("settings.hybrid-chance", 0.65), 0, 1);
        this.hybridScale = clamp(c.getDouble("settings.hybrid-scale", 0.6), 0.1, 3);
        this.globalScale = clamp(c.getDouble("settings.global-scale", 1.0), 0.1, 3);
        this.reforgePower = clamp(c.getDouble("settings.reforge-power", 0.6), 0, 1);
        this.rerollCost = Math.max(1, c.getInt("costs.reroll", 6));
        this.lockCost = Math.max(0, c.getInt("costs.lock-surcharge", 3));
        this.upgradeCost = Math.max(1, c.getInt("costs.upgrade", 12));
        this.salvagePerTier = Math.max(1, c.getInt("costs.salvage-per-tier", 2));
        this.debug = c.getBoolean("settings.debug", false);
        this.defaultSlots = Math.max(1, c.getInt("slots.default", 2));
        this.bookDropChance = clamp(c.getDouble("books.drop-chance", 0.06), 0, 1);
        this.bookLootChance = clamp(c.getDouble("books.loot-chance", 0.10), 0, 1);
        this.bookFishChance = clamp(c.getDouble("books.fishing-chance", 0.08), 0, 1);
        this.buyCost = Math.max(1, c.getInt("books.buy-cost", 18));
        this.buyTier = Math.max(1, Math.min(5, c.getInt("books.buy-tier", 2)));
        var section = c.getConfigurationSection("slots.by-material");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                slotsByMaterial.put(key.toUpperCase(Locale.ROOT), Math.max(0, section.getInt(key)));
            }
        }
    }

    public static Settings load(FileConfiguration c) {
        return new Settings(c);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public Mode mode() {
        return mode;
    }

    public double hybridChance() {
        return hybridChance;
    }

    public double hybridScale() {
        return hybridScale;
    }

    public double globalScale() {
        return globalScale;
    }

    public double reforgePower() {
        return reforgePower;
    }

    public int rerollCost() {
        return rerollCost;
    }

    public int lockCost() {
        return lockCost;
    }

    public int upgradeCost() {
        return upgradeCost;
    }

    public int salvagePerTier() {
        return salvagePerTier;
    }

    public boolean debug() {
        return debug;
    }

    public int defaultSlots() {
        return defaultSlots;
    }

    public Map<String, Integer> slotsByMaterial() {
        return slotsByMaterial;
    }

    public double bookDropChance() {
        return bookDropChance;
    }

    public double bookLootChance() {
        return bookLootChance;
    }

    public double bookFishChance() {
        return bookFishChance;
    }

    public int buyCost() {
        return buyCost;
    }

    public int buyTier() {
        return buyTier;
    }
}
