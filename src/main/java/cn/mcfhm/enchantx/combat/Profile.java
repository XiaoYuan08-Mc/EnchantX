package cn.mcfhm.enchantx.combat;

import java.util.HashMap;
import java.util.Map;

/** 玩家身上由装备词条聚合出的运行时系数。 */
public final class Profile {

    private final Map<String, Double> effects = new HashMap<>(16);

    public void add(String key, double value) {
        effects.merge(key, value, Double::sum);
    }

    public double get(String key) {
        Double value = effects.get(key);
        return value == null ? 0.0 : value;
    }

    public boolean has(String key) {
        return effects.containsKey(key);
    }

    public void clear() {
        effects.clear();
    }

    public Map<String, Double> raw() {
        return effects;
    }
}
