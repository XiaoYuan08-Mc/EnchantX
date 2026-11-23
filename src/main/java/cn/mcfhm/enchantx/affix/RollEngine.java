package cn.mcfhm.enchantx.affix;

import cn.mcfhm.enchantx.util.Mats;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** 词条掷点引擎：选池、选档、选数值、守冲突与槽位。 */
public final class RollEngine {

    private final Random random;

    public RollEngine() {
        this.random = new Random();
    }

    public AffixData roll(AffixManager manager, ItemStack item, int capacity, double power,
                          AffixData current, Set<Integer> locked, double scale) {
        AffixData out = AffixData.empty();
        if (item == null || capacity <= 0) {
            return out;
        }
        for (int slot = 1; slot <= capacity; slot++) {
            AffixInstance keep = current == null ? null : current.bySlot(slot);
            if (keep != null && locked != null && locked.contains(slot)) {
                out.add(keep);
                continue;
            }
            AffixInstance generated = generate(manager, item, out, power, scale, slot);
            if (generated != null) {
                out.add(generated);
            }
        }
        return out;
    }

    /** 只补一条（hybrid 模式用），返回 null 表示没有合适词条。 */
    public AffixInstance rollOne(AffixManager manager, ItemStack item, AffixData current,
                                 int capacity, double power, double scale) {
        int slot = current == null ? 1 : current.freeSlot(capacity);
        if (slot <= 0) {
            return null;
        }
        return generate(manager, item, current == null ? AffixData.empty() : current, power, scale, slot);
    }

    private AffixInstance generate(AffixManager manager, ItemStack item, AffixData existing,
                                   double power, double scale, int slot) {
        String category = Mats.category(item.getType());
        List<AffixDef> pool = new ArrayList<>();
        for (AffixDef def : manager.candidates(category)) {
            if (manager.canAdd(def, existing)) {
                pool.add(def);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }

        double totalTierWeight = 0.0;
        for (AffixDef def : pool) {
            for (int tier : def.tierKeys()) {
                totalTierWeight += def.weight() * def.tierWeight(tier, power);
            }
        }
        if (totalTierWeight <= 0) {
            return null;
        }

        double pick = random.nextDouble() * totalTierWeight;
        AffixDef chosenDef = null;
        int chosenTier = 1;
        for (AffixDef def : pool) {
            for (int tier : def.tierKeys()) {
                double w = def.weight() * def.tierWeight(tier, power);
                if (pick < w) {
                    chosenDef = def;
                    chosenTier = tier;
                    break;
                }
                pick -= w;
            }
            if (chosenDef != null) {
                break;
            }
        }
        if (chosenDef == null) {
            chosenDef = pool.get(0);
            chosenTier = chosenDef.tierKeys().get(0);
        }

        AffixTier tier = chosenDef.tier(chosenTier);
        double value = tier.roll(random) * scale;
        return new AffixInstance(chosenDef.id(), chosenTier, value, slot);
    }

    public Random random() {
        return random;
    }
}
