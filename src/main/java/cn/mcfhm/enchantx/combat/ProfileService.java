package cn.mcfhm.enchantx.combat;

import cn.mcfhm.enchantx.affix.AffixData;
import cn.mcfhm.enchantx.affix.AffixDef;
import cn.mcfhm.enchantx.affix.AffixInstance;
import cn.mcfhm.enchantx.affix.AffixManager;
import cn.mcfhm.enchantx.item.AffixItems;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 战斗系数缓存：只在装备变化或超时后重建，热路径只读 Map。 */
public final class ProfileService {

    private static final long REFRESH_MS = 500L;

    private final AffixItems items;
    private final AffixManager manager;
    private final Map<UUID, Entry> cache = new HashMap<>(64);

    public ProfileService(AffixItems items, AffixManager manager) {
        this.items = items;
        this.manager = manager;
    }

    public Profile profile(Player player) {
        long now = System.currentTimeMillis();
        Entry entry = cache.get(player.getUniqueId());
        if (entry != null && now - entry.builtAt < REFRESH_MS) {
            return entry.profile;
        }
        Profile profile = build(player);
        cache.put(player.getUniqueId(), new Entry(profile, now));
        return profile;
    }

    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    public void clear() {
        cache.clear();
    }

    private Profile build(Player player) {
        Profile profile = new Profile();
        PlayerInventory inv = player.getInventory();
        apply(profile, inv.getItemInMainHand());
        apply(profile, inv.getItemInOffHand());
        for (ItemStack armor : inv.getArmorContents()) {
            apply(profile, armor);
        }
        return profile;
    }

    private void apply(Profile profile, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        AffixData data = items.read(item);
        if (data.isEmpty()) {
            return;
        }
        for (AffixInstance instance : data.list()) {
            AffixDef def = manager.get(instance.defId());
            if (def == null || def.isAttribute()) {
                continue;
            }
            profile.add(def.effect(), instance.value());
        }
    }

    private record Entry(Profile profile, long builtAt) {
    }
}
