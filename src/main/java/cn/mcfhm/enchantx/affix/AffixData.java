package cn.mcfhm.enchantx.affix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 一件物品上的全部词条。编码格式： v1|id:tier:value:slot;id:tier:value:slot */
public final class AffixData {

    private final List<AffixInstance> instances = new ArrayList<>(4);

    public static AffixData empty() {
        return new AffixData();
    }

    public List<AffixInstance> list() {
        return instances;
    }

    public int size() {
        return instances.size();
    }

    public boolean isEmpty() {
        return instances.isEmpty();
    }

    public AffixInstance bySlot(int slot) {
        for (AffixInstance in : instances) {
            if (in.slot() == slot) {
                return in;
            }
        }
        return null;
    }

    public AffixInstance byId(String id) {
        for (AffixInstance in : instances) {
            if (in.defId().equals(id)) {
                return in;
            }
        }
        return null;
    }

    public AffixData add(AffixInstance instance) {
        instances.removeIf(in -> in.slot() == instance.slot());
        instances.add(instance);
        return this;
    }

    public AffixData removeSlot(int slot) {
        instances.removeIf(in -> in.slot() == slot);
        return this;
    }

    public AffixData copy() {
        AffixData data = new AffixData();
        data.instances.addAll(instances);
        return data;
    }

    public int freeSlot(int capacity) {
        for (int slot = 1; slot <= capacity; slot++) {
            if (bySlot(slot) == null) {
                return slot;
            }
        }
        return 0;
    }

    public int usedSlots() {
        int used = 0;
        for (AffixInstance in : instances) {
            used = Math.max(used, in.slot());
        }
        return used;
    }

    public String encode() {
        StringBuilder sb = new StringBuilder("v1|");
        boolean first = true;
        for (AffixInstance in : instances) {
            if (!first) {
                sb.append(';');
            }
            first = false;
            sb.append(in.defId()).append(':')
                    .append(in.tier()).append(':')
                    .append(format(in.value())).append(':')
                    .append(in.slot());
        }
        return sb.toString();
    }

    private static String format(double v) {
        String s = String.format(Locale.ROOT, "%.3f", v);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public static AffixData parse(String raw) {
        AffixData data = new AffixData();
        if (raw == null || raw.isBlank()) {
            return data;
        }
        String payload = raw.startsWith("v1|") ? raw.substring(3) : raw;
        if (payload.isBlank()) {
            return data;
        }
        for (String token : payload.split(";")) {
            String[] parts = token.split(":");
            if (parts.length < 4) {
                continue;
            }
            try {
                String id = parts[0];
                int tier = Integer.parseInt(parts[1]);
                double value = Double.parseDouble(parts[2]);
                int slot = Integer.parseInt(parts[3]);
                data.add(new AffixInstance(id, tier, value, slot));
            } catch (NumberFormatException ignored) {
                // 坏数据跳过，不影响整件物品
            }
        }
        return data;
    }
}
