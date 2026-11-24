package cn.mcfhm.enchantx.affix;

/** 词条数值的显示规则。 */
public enum Display {
    FLAT,
    PERCENT,
    SECONDS,
    INT;

    public static Display parse(String raw, Display fallback) {
        if (raw == null) {
            return fallback;
        }
        return switch (raw.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "PERCENT", "%" -> PERCENT;
            case "SECONDS", "SECOND", "S" -> SECONDS;
            case "INT", "INTEGER", "LEVEL" -> INT;
            case "FLAT", "NUMBER" -> FLAT;
            default -> fallback;
        };
    }
}
