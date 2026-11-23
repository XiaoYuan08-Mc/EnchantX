package cn.mcfhm.enchantx.util;

import java.text.DecimalFormat;

/** 文案与数值格式化。 */
public final class Texts {

    private static final DecimalFormat FLAT = new DecimalFormat("0.##");
    private static final DecimalFormat ONE = new DecimalFormat("0.0");

    public static final String[] TIER_COLORS = {
            "\u00a77", "\u00a77", "\u00a7a", "\u00a79", "\u00a75", "\u00a76"
    };

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII"};

    private Texts() {
    }

    public static String tierColor(int tier) {
        int i = Math.max(0, Math.min(TIER_COLORS.length - 1, tier));
        return TIER_COLORS[i];
    }

    public static String roman(int tier) {
        return tier >= 1 && tier < ROMAN.length ? ROMAN[tier] : String.valueOf(tier);
    }

    public static String flat(double v) {
        return FLAT.format(v);
    }

    public static String percent(double v) {
        return ONE.format(v * 100.0) + "%";
    }

    public static String seconds(double v) {
        return ONE.format(v) + "s";
    }
}
