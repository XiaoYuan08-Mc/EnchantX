package cn.mcfhm.enchantx.economy;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** 词条碎片账户：存在玩家 PDC 里，跟着存档走，不占背包。 */
public final class FragmentBank {

    private final NamespacedKey key;

    public FragmentBank(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "fragments");
    }

    public int get(Player player) {
        Integer value = player.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }

    public void set(Player player, int amount) {
        player.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, Math.max(0, amount));
    }

    public void add(Player player, int amount) {
        if (amount == 0) {
            return;
        }
        set(player, get(player) + amount);
    }

    public boolean take(Player player, int amount) {
        int current = get(player);
        if (current < amount) {
            return false;
        }
        set(player, current - amount);
        return true;
    }
}
