package cn.mcfhm.enchantx.task;

import cn.mcfhm.enchantx.EnchantX;
import cn.mcfhm.enchantx.combat.Profile;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** 被动词条：再生、夜视、水下呼吸，每 4 秒校准一次。 */
public final class PassiveTask implements Runnable {

    private final EnchantX plugin;

    public PassiveTask(EnchantX plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Profile profile = plugin.profiles().profile(player);

            double regen = profile.get("regen");
            if (regen > 0) {
                double max = maxHealth(player);
                if (player.getHealth() < max) {
                    player.setHealth(Math.min(max, player.getHealth() + max * Math.min(0.06, regen)));
                }
            }

            if (profile.get("night_vision") > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false));
            }
            if (profile.get("water_breathing") > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 400, 0, true, false));
            }
        }
    }

    private double maxHealth(Player player) {
        AttributeInstance instance = player.getAttribute(Attribute.MAX_HEALTH);
        return instance == null ? 20.0 : instance.getValue();
    }
}
