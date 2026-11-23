package cn.mcfhm.enchantx.economy;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 词条碎片账户：真值存在玩家 PDC 里，跟着存档走；内存里再放一层缓存，
 * 避免 GUI 每次刷新都去读 PDC。写入是直写（write-through），不会出现缓存与存档不一致。
 */
public final class FragmentBank {

    private final NamespacedKey key;
    private final Map<UUID, Integer> cache = new ConcurrentHashMap<>();

    public FragmentBank(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "fragments");
    }

    public int get(Player player) {
        if (player == null) {
            return 0;
        }
        Integer cached = cache.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }
        Integer stored = player.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        int amount = stored == null ? 0 : Math.max(0, stored);
        cache.put(player.getUniqueId(), amount);
        return amount;
    }

    public void set(Player player, int amount) {
        if (player == null) {
            return;
        }
        int safe = Math.max(0, amount);
        cache.put(player.getUniqueId(), safe);
        player.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, safe);
    }

    public void add(Player player, int amount) {
        if (player == null || amount == 0) {
            return;
        }
        set(player, get(player) + amount);
    }

    public boolean take(Player player, int amount) {
        if (player == null || amount < 0) {
            return false;
        }
        int current = get(player);
        if (current < amount) {
            return false;
        }
        set(player, current - amount);
        return true;
    }

    /** 玩家退出时丢掉缓存，避免 UUID 越攒越多；下次进来重新从 PDC 读。 */
    public void forget(Player player) {
        if (player != null) {
            cache.remove(player.getUniqueId());
        }
    }

    /** /enchantx reload 时清空，强制重读。 */
    public void clear() {
        cache.clear();
    }
}
