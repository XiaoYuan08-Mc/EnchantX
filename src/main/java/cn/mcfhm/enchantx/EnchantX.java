package cn.mcfhm.enchantx;

import cn.mcfhm.enchantx.affix.AffixManager;
import cn.mcfhm.enchantx.affix.RollEngine;
import cn.mcfhm.enchantx.command.EnchantXCommand;
import cn.mcfhm.enchantx.combat.ProfileService;
import cn.mcfhm.enchantx.config.Settings;
import cn.mcfhm.enchantx.economy.FragmentBank;
import cn.mcfhm.enchantx.gui.ReforgeGui;
import cn.mcfhm.enchantx.item.AffixItems;
import cn.mcfhm.enchantx.listener.AnvilListener;
import cn.mcfhm.enchantx.listener.BookDropsListener;
import cn.mcfhm.enchantx.listener.CombatListener;
import cn.mcfhm.enchantx.listener.EnchantTableListener;
import cn.mcfhm.enchantx.listener.SupportListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/** EnchantX 主类：原版附魔 → 词条重制拓展。 */
public final class EnchantX extends JavaPlugin {

    private static final List<String> DEFAULT_AFFIX_FILES = List.of(
            "affixes/weapon.yml",
            "affixes/tool.yml",
            "affixes/armor.yml",
            "affixes/ranged.yml",
            "affixes/universal.yml",
            "affixes/helmet.yml",
            "affixes/chest.yml",
            "affixes/legs.yml",
            "affixes/boots.yml"
    );

    private Settings settings;
    private AffixManager manager;
    private AffixItems items;
    private RollEngine engine;
    private ProfileService profiles;
    private FragmentBank fragments;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultAffixes();

        settings = Settings.load(getConfig());

        manager = new AffixManager(this);
        manager.load();

        items = new AffixItems(this, manager, settings);
        engine = new RollEngine();
        profiles = new ProfileService(items, manager);
        fragments = new FragmentBank(this);

        getServer().getPluginManager().registerEvents(
                new EnchantTableListener(this, items, engine), this);
        getServer().getPluginManager().registerEvents(new AnvilListener(items), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this, profiles), this);
        getServer().getPluginManager().registerEvents(new SupportListener(profiles), this);
        getServer().getPluginManager().registerEvents(new BookDropsListener(this, items), this);
        getServer().getPluginManager().registerEvents(new ReforgeGui.Handler(), this);

        getServer().getScheduler().runTaskTimer(this, new cn.mcfhm.enchantx.task.PassiveTask(this), 80L, 80L);

        EnchantXCommand command = new EnchantXCommand(this);
        if (getCommand("enchantx") != null) {
            getCommand("enchantx").setExecutor(command);
            getCommand("enchantx").setTabCompleter(command);
        }

        getLogger().info("EnchantX 已启用：词条 " + manager.size() + " 条，模式 " + settings.mode());

        if (System.getProperty("enchantx.selftest") != null) {
            SelfTest.run(this);
        }
    }

    @Override
    public void onDisable() {
        if (profiles != null) {
            profiles.clear();
        }
    }

    private void saveDefaultAffixes() {
        for (String path : DEFAULT_AFFIX_FILES) {
            File file = new File(getDataFolder(), path);
            if (!file.exists()) {
                saveResource(path, false);
            }
        }
    }

    /** 重载配置与词条库，不影响已在物品上的数据。 */
    public void reloadAll() {
        reloadConfig();
        settings = Settings.load(getConfig());
        items.updateSettings(settings);
        manager.load();
        if (profiles != null) {
            profiles.clear();
        }
    }

    public Settings settings() {
        return settings;
    }

    public AffixManager manager() {
        return manager;
    }

    public AffixItems items() {
        return items;
    }

    public RollEngine engine() {
        return engine;
    }

    public ProfileService profiles() {
        return profiles;
    }

    public FragmentBank fragments() {
        return fragments;
    }

    public boolean debugEnabled() {
        return settings != null && settings.debug();
    }
}
