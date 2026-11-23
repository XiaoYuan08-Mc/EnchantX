package cn.mcfhm.enchantx.listener;

import cn.mcfhm.enchantx.EnchantX;
import cn.mcfhm.enchantx.combat.Profile;
import cn.mcfhm.enchantx.combat.ProfileService;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** 战斗结算：暴击、破甲、吸血、处决、反伤、闪避、元素附伤。 */
public final class CombatListener implements Listener {

    private final EnchantX plugin;
    private final ProfileService profiles;
    private final Random random = new Random();
    private final Set<UUID> reflecting = new HashSet<>();

    public CombatListener(EnchantX plugin, ProfileService profiles) {
        this.plugin = plugin;
        this.profiles = profiles;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker == victim) {
            return;
        }
        if (reflecting.contains(attacker.getUniqueId())) {
            return;
        }

        Profile attackerProfile = profiles.profile(attacker);
        Profile victimProfile = victim instanceof Player victimPlayer
                ? profiles.profile(victimPlayer) : null;

        if (victimProfile != null) {
            double dodge = victimProfile.get("dodge");
            if (dodge > 0 && random.nextDouble() < Math.min(0.35, dodge)) {
                event.setCancelled(true);
                victim.getWorld().spawnParticle(Particle.SMOKE, victim.getLocation().add(0, 1, 0),
                        8, 0.25, 0.35, 0.25, 0.02);
                return;
            }
        }

        double damage = event.getDamage();
        boolean projectile = event.getDamager() instanceof Projectile;

        double firstStrike = attackerProfile.get("first_strike");
        if (firstStrike > 0 && attacker.getAttackCooldown() > 0.9f) {
            damage += damage * Math.min(0.5, firstStrike);
        }

        double execute = attackerProfile.get("execute");
        if (execute > 0) {
            double maxHealth = maxHealth(victim);
            double ratio = victim.getHealth() / Math.max(1.0, maxHealth);
            if (ratio < 0.35) {
                damage += damage * Math.min(0.6, execute);
            }
        }

        damage += damage * bonusAgainst(victim.getType(), attackerProfile);

        double armorPen = attackerProfile.get("armor_pen");
        if (armorPen > 0) {
            double armor = armorValue(victim);
            damage += damage * Math.min(0.6, armorPen) * Math.min(1.0, armor / 20.0);
        }

        double trueDamage = attackerProfile.get("true_damage");
        if (trueDamage > 0) {
            damage += trueDamage;
        }

        double projectileMult = attackerProfile.get("projectile_mult");
        if (projectile && projectileMult > 0) {
            damage += damage * Math.min(0.6, projectileMult);
        }

        boolean crit = false;
        double critChance = attackerProfile.get("crit_chance");
        if (critChance > 0 && random.nextDouble() < Math.min(0.6, critChance)) {
            damage *= 1.5 + Math.max(0.0, Math.min(1.2, attackerProfile.get("crit_damage")));
            crit = true;
        }

        if (victimProfile != null) {
            double reduce = victimProfile.get("damage_reduce");
            double lastStand = victimProfile.get("last_stand");
            if (lastStand > 0) {
                double maxHealth = maxHealth(victim);
                if (victim.getHealth() / Math.max(1.0, maxHealth) < 0.4) {
                    reduce += lastStand;
                }
            }
            if (reduce > 0) {
                damage *= 1.0 - Math.min(0.8, reduce);
            }
            double taken = victimProfile.get("damage_taken");
            if (taken > 0) {
                damage *= 1.0 + Math.min(0.6, taken);
            }
        }

        event.setDamage(Math.max(0.1, damage));

        double combo = attackerProfile.get("combo");
        if (combo > 0 && random.nextDouble() < Math.min(0.6, combo)) {
            damage *= 1.6;
            event.setDamage(Math.max(0.1, damage));
            victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, victim.getLocation().add(0, 1, 0),
                    4, 0.2, 0.2, 0.2, 0.0);
        }

        double lifesteal = attackerProfile.get("lifesteal");
        if (lifesteal > 0) {
            heal(attacker, damage * Math.min(0.35, lifesteal));
        }

        applyOnHit(victim, attackerProfile);
        applyKnockback(victim, attacker, attackerProfile.get("knockback"));
        sweepDamage(attacker, victim, damage * Math.min(0.5, attackerProfile.get("sweep")));

        if (crit) {
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.0, 0),
                    10, 0.25, 0.3, 0.25, 0.15);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.9f, 1.0f);
        }

        if (victimProfile != null) {
            double reflect = victimProfile.get("reflect");
            if (reflect > 0 && reflect < 1.0) {
                reflectDamage(victim, attacker, damage * reflect);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        double reduce = profiles.profile(player).get("fall_reduce");
        if (reduce > 0) {
            event.setDamage(event.getDamage() * (1.0 - Math.min(0.9, reduce)));
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        double healOnKill = profiles.profile(killer).get("heal_on_kill");
        if (healOnKill > 0) {
            heal(killer, maxHealth(killer) * Math.min(0.4, healOnKill));
        }
    }

    private void sweepDamage(Player attacker, LivingEntity origin, double amount) {
        if (amount <= 0) {
            return;
        }
        List<LivingEntity> targets = new java.util.ArrayList<>();
        for (org.bukkit.entity.Entity entity : origin.getWorld()
                .getNearbyEntities(origin.getLocation(), 2.6, 1.6, 2.6)) {
            if (entity instanceof LivingEntity living && living != origin && living != attacker
                    && !living.isDead()) {
                targets.add(living);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        reflecting.add(attacker.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                int hit = 0;
                for (LivingEntity target : targets) {
                    if (hit++ >= 4) {
                        break;
                    }
                    target.damage(amount, attacker);
                }
            } finally {
                reflecting.remove(attacker.getUniqueId());
            }
        });
    }

    private void reflectDamage(LivingEntity victim, Player attacker, double amount) {
        if (amount <= 0 || amount > 40) {
            return;
        }
        reflecting.add(attacker.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                attacker.damage(amount, victim);
            } finally {
                reflecting.remove(attacker.getUniqueId());
            }
        });
    }

    private void applyOnHit(LivingEntity victim, Profile profile) {
        double poison = profile.get("poison");
        if (poison > 0 && random.nextDouble() < Math.min(0.7, poison)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0));
        }
        double wither = profile.get("wither");
        if (wither > 0 && random.nextDouble() < Math.min(0.6, wither)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
        }
        double slow = profile.get("slow");
        if (slow > 0 && random.nextDouble() < Math.min(0.6, slow)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
        }
        double ignite = profile.get("ignite");
        if (ignite > 0) {
            victim.setFireTicks(Math.max(victim.getFireTicks(), (int) (Math.min(12.0, ignite) * 20)));
        }
    }

    private void applyKnockback(LivingEntity victim, Player attacker, double strength) {
        if (strength <= 0) {
            return;
        }
        Vector direction = victim.getLocation().toVector()
                .subtract(attacker.getLocation().toVector());
        if (direction.lengthSquared() < 0.0001) {
            return;
        }
        direction.normalize().multiply(Math.min(2.0, strength)).setY(0.25);
        victim.setVelocity(victim.getVelocity().add(direction));
    }

    private double bonusAgainst(EntityType type, Profile profile) {
        String name = type.name();
        double bonus = 0;
        if (name.contains("ZOMBIE") || name.contains("SKELETON") || name.contains("DROWNED")
                || name.contains("HUSK") || name.contains("STRAY") || name.contains("PHANTOM")
                || name.contains("WITHER") || name.contains("ZOGLIN") || name.contains("BOGGED")) {
            bonus += profile.get("undead_bonus");
        }
        if (name.contains("SPIDER") || name.contains("SILVERFISH") || name.contains("ENDERMITE")
                || name.contains("BEE")) {
            bonus += profile.get("arthropod_bonus");
        }
        return Math.min(0.6, bonus);
    }

    private void heal(LivingEntity entity, double amount) {
        if (amount <= 0) {
            return;
        }
        double max = maxHealth(entity);
        entity.setHealth(Math.min(max, entity.getHealth() + amount));
    }

    private double maxHealth(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(Attribute.MAX_HEALTH);
        return instance == null ? 20.0 : instance.getValue();
    }

    private double armorValue(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(Attribute.ARMOR);
        return instance == null ? 0.0 : instance.getValue();
    }

    private Player resolveAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
