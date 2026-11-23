package cn.mcfhm.enchantx.affix;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 词条库：从 affixes/*.yml 载入并做冲突判定。 */
public final class AffixManager {

    private final JavaPlugin plugin;
    private final Map<String, AffixDef> defs = new LinkedHashMap<>();
    private final Map<String, List<AffixDef>> byPool = new LinkedHashMap<>();
    private int loadErrors;

    public AffixManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        defs.clear();
        byPool.clear();
        loadErrors = 0;
        File dir = new File(plugin.getDataFolder(), "affixes");
        if (!dir.isDirectory()) {
            plugin.getLogger().warning("affixes 目录不存在: " + dir.getAbsolutePath());
            return;
        }
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("affixes 目录里没有任何 yml 文件");
            return;
        }
        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort(Comparator.comparing(File::getName));

        for (File file : sorted) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            String pool = yml.getString("pool", file.getName().replace(".yml", ""));
            ConfigurationSection root = yml.getConfigurationSection("affixes");
            if (root == null) {
                plugin.getLogger().warning(file.getName() + " 缺少 affixes 节点");
                continue;
            }
            List<AffixDef> poolList = byPool.computeIfAbsent(pool.toLowerCase(Locale.ROOT), k -> new ArrayList<>());
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                if (defs.containsKey(id)) {
                    plugin.getLogger().warning("词条 id 重复：" + id + "（" + file.getName() + "）");
                    loadErrors++;
                    continue;
                }
                AffixDef def = AffixDef.parse(id, section, plugin.getLogger());
                if (def == null) {
                    loadErrors++;
                    continue;
                }
                if (def.effect().equals(AffixDef.EFFECT_ATTRIBUTE) && def.attribute() == null) {
                    plugin.getLogger().warning("词条 " + id + " 声明为 attribute 但没有合法 attribute，已跳过");
                    loadErrors++;
                    continue;
                }
                defs.put(id, def);
                poolList.add(def);
            }
        }
        plugin.getLogger().info("已载入 " + defs.size() + " 条词条，共 " + byPool.size() + " 个池"
                + (loadErrors > 0 ? "（跳过 " + loadErrors + " 条）" : ""));
    }

    public AffixDef get(String id) {
        return defs.get(id);
    }

    public Map<String, AffixDef> defs() {
        return defs;
    }

    public Map<String, List<AffixDef>> pools() {
        return byPool;
    }

    public int size() {
        return defs.size();
    }

    public List<AffixDef> candidates(String category) {
        List<AffixDef> list = new ArrayList<>();
        for (AffixDef def : defs.values()) {
            if (def.matches(category)) {
                list.add(def);
            }
        }
        return list;
    }

    /** 新词条能否落在现有组合上：同 id、同组、双向 conflicts 都不允许。 */
    public boolean canAdd(AffixDef def, AffixData data) {
        return canAdd(def, data, null);
    }

    /**
     * 带物品类别的判定。category 为 null 时只做冲突判定（附魔台 / 重铸走候选池，池已按类别筛过）；
     * 铁砧这类"玩家自选词条"的入口必须传 category，否则武器词条能被贴到盔甲上。
     */
    public boolean canAdd(AffixDef def, AffixData data, String category) {
        if (def == null || data == null) {
            return false;
        }
        if (category != null && !def.matches(category)) {
            return false;
        }
        for (AffixInstance in : data.list()) {
            if (in.defId().equals(def.id())) {
                return false;
            }
            AffixDef other = defs.get(in.defId());
            if (other == null) {
                continue;
            }
            if (!def.group().isBlank() && def.group().equalsIgnoreCase(other.group())) {
                return false;
            }
            if (def.conflicts().contains(other.id()) || other.conflicts().contains(def.id())) {
                return false;
            }
        }
        return true;
    }
}
