package cn.mcfhm.enchantx.affix;

/** 词条实例：模板 id + 档位 + 掷出的数值 + 占用槽位。 */
public final class AffixInstance {

    private final String defId;
    private final int tier;
    private final double value;
    private final int slot;

    public AffixInstance(String defId, int tier, double value, int slot) {
        this.defId = defId;
        this.tier = tier;
        this.value = value;
        this.slot = slot;
    }

    public String defId() {
        return defId;
    }

    public int tier() {
        return tier;
    }

    public double value() {
        return value;
    }

    public int slot() {
        return slot;
    }

    public AffixInstance withValue(double newValue) {
        return new AffixInstance(defId, tier, newValue, slot);
    }

    public AffixInstance withSlot(int newSlot) {
        return new AffixInstance(defId, tier, value, newSlot);
    }

    public AffixInstance upgrade(int newTier, double newValue) {
        return new AffixInstance(defId, newTier, newValue, slot);
    }

    @Override
    public String toString() {
        return defId + ":" + tier + ":" + value + ":" + slot;
    }
}
