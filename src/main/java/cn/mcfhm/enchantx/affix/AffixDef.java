package cn.mcfhm.enchantx.affix;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** 一条词条的定义（模板），运行时的具体数值见 {@link AffixInstance}。 */
public final class AffixDef {

    public static final String EFFECT_ATTRIBUTE = "attribute";

    private final String id;
    private final String name;
    private final String desc;
    private final String effect;
    private final Set<String> targets;
    private final Set<String> conflicts;
    private final String group;
    private final Attribute attribute;
    private final AttributeModifier.Operation operation;
    private final Display display;
    private final double weight;
    private final Map<Integer, AffixTier> tiers;
    private final int maxTier;
    private final boolean positive;

    private AffixDef(String id, String name, String desc, String effect, Set<String> targets,
                     Set<String> conflicts, String group, Attribute attribute,
                     AttributeModifier.Operation operation, Display display, double weight,
                     Map<Integer, AffixTier> tiers, boolean positive) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.effect = effect;
        this.targets = targets;
        this.conflicts = conflicts;
        this.group = group;
        this.attribute = attribute;
        this.operation = operation;
        this.display = display;
        this.weight = Math.max(0.0001, weight);
        this.tiers = tiers;
        this.maxTier = tiers.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
        this.positive = positive;
    }

    public static AffixDef parse(String id, ConfigurationSection section, Logger log) {
        String name = section.getString("name", id);
        String desc = section.getString("desc", "");
        String effect = section.getString("effect", EFFECT_ATTRIBUTE).trim().toLowerCase(Locale.ROOT);

        Set<String> targets = new LinkedHashSet<>();
        for (String t : section.getStringList("targets")) {
            targets.add(t.trim().toUpperCase(Locale.ROOT));
        }
        if (section.isString("targets")) {
            targets.add(section.getString("targets").trim().toUpperCase(Locale.ROOT));
        }
        if (targets.isEmpty()) {
            targets.add("ANY");
        }

        Set<String> conflicts = new LinkedHashSet<>();
        for (String c : section.getStringList("conflicts")) {
            conflicts.add(c.trim());
        }

        String group = section.getString("group", "");

        Attribute attribute = null;
        String attributeName = section.getString("attribute", null);
        if (attributeName != null && !attributeName.isBlank()) {
            attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(attributeName.trim().toLowerCase(Locale.ROOT)));
            if (attribute == null) {
                log.warning("词条 " + id + " 的 attribute 无法识别: " + attributeName);
            }
        }

        AttributeModifier.Operation operation = parseOperation(section.getString("operation", "add"));
        Display display = Display.parse(section.getString("display", null), Display.FLAT);
        double weight = section.getDouble("weight", 10.0);

        Map<Integer, AffixTier> tiers = new LinkedHashMap<>();
        ConfigurationSection tierSection = section.getConfigurationSection("tiers");
        if (tierSection == null) {
            log.warning("词条 " + id + " 缺少 tiers 配置，已跳过");
            return null;
        }
        for (String key : tierSection.getKeys(false)) {
            int tier;
            try {
                tier = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                log.warning("词条 " + id + " 的档位键不是数字: " + key);
                continue;
            }
            ConfigurationSection ts = tierSection.getConfigurationSection(key);
            if (ts == null) {
                continue;
            }
            double min = ts.getDouble("min");
            double max = ts.getDouble("max", min);
            double tw = ts.getDouble("weight", 10.0);
            int cost = ts.getInt("cost", tier);
            tiers.put(tier, new AffixTier(tier, min, max, tw, cost));
        }
        if (tiers.isEmpty()) {
            log.warning("词条 " + id + " 没有任何档位，已跳过");
            return null;
        }

        boolean positive = section.getBoolean("positive", true);
        return new AffixDef(id, name, desc, effect, targets, conflicts, group, attribute,
                operation, display, weight, tiers, positive);
    }

    private static AttributeModifier.Operation parseOperation(String raw) {
        if (raw == null) {
            return AttributeModifier.Operation.ADD_NUMBER;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "add_scalar", "add_percent", "scalar", "percent" -> AttributeModifier.Operation.ADD_SCALAR;
            case "multiply", "multiply_scalar_1", "multiply_one" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
            default -> AttributeModifier.Operation.ADD_NUMBER;
        };
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String desc() {
        return desc;
    }

    public String effect() {
        return effect;
    }

    public boolean isAttribute() {
        return effect.equals(EFFECT_ATTRIBUTE) && attribute != null;
    }

    public Attribute attribute() {
        return attribute;
    }

    public AttributeModifier.Operation operation() {
        return operation;
    }

    public Display display() {
        return display;
    }

    public double weight() {
        return weight;
    }

    public Set<String> targets() {
        return targets;
    }

    public Set<String> conflicts() {
        return conflicts;
    }

    public String group() {
        return group;
    }

    public boolean positive() {
        return positive;
    }

    public Map<Integer, AffixTier> tiers() {
        return tiers;
    }

    public int maxTier() {
        return maxTier;
    }

    public AffixTier tier(int tier) {
        AffixTier t = tiers.get(tier);
        if (t != null) {
            return t;
        }
        int nearest = tiers.keySet().stream()
                .min((a, b) -> Math.abs(a - tier) - Math.abs(b - tier))
                .orElse(1);
        return tiers.get(nearest);
    }

    public boolean matches(String category) {
        return targets.contains("ANY") || targets.contains(category);
    }

    /** 档位权重随附魔等级（power 0-1）向高档倾斜。 */
    public double tierWeight(int tier, double power) {
        AffixTier t = tiers.get(tier);
        if (t == null) {
            return 0;
        }
        double factor = 1.0 + power * 2.6 * Math.max(0, tier - 1);
        return t.weight() * factor;
    }

    public List<Integer> tierKeys() {
        return new ArrayList<>(tiers.keySet());
    }

    public Set<String> allConflicts() {
        Set<String> all = new LinkedHashSet<>(conflicts);
        if (!group.isBlank()) {
            all.add("group:" + group);
        }
        return all;
    }
}
