package cn.mcfhm.enchantx.affix;

import java.util.Random;

/** 单个档位的数值区间与权重。 */
public final class AffixTier {

    private final int tier;
    private final double min;
    private final double max;
    private final double weight;
    private final int cost;

    public AffixTier(int tier, double min, double max, double weight, int cost) {
        this.tier = tier;
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        this.weight = Math.max(0.0001, weight);
        this.cost = Math.max(1, cost);
    }

    public int tier() {
        return tier;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double weight() {
        return weight;
    }

    public int cost() {
        return cost;
    }

    public double roll(Random random) {
        if (max - min < 0.000001) {
            return min;
        }
        return min + random.nextDouble() * (max - min);
    }
}
