package com.deepwoken.weapons.listeners;

import com.deepwoken.weapons.DeepwokenWeapons;
import com.deepwoken.weapons.managers.WeaponManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public class WeaponListener implements Listener {

    private final DeepwokenWeapons plugin;

    // Crit cooldowns: player UUID → weapon-id → last crit ms
    private final Map<UUID, Map<String, Long>> critCooldowns = new HashMap<>();

    // Blood poison tasks: player UUID → task id
    private final Map<UUID, Integer> bloodPoisonTasks = new HashMap<>();

    // Iron Requiem bullets (player UUID → bullets left)
    private final Map<UUID, Integer> bullets = new HashMap<>();

    private static final int CRIT_COOLDOWN_MS = 5000; // 5 seconds default

    public WeaponListener(DeepwokenWeapons plugin) {
        this.plugin = plugin;
        startPassiveTask();
    }

    // ─── Join / Quit ──────────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.getBloodBarManager().showBar(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getBloodBarManager().hideBar(e.getPlayer());
        UUID uuid = e.getPlayer().getUniqueId();
        Integer task = bloodPoisonTasks.remove(uuid);
        if (task != null) Bukkit.getScheduler().cancelTask(task);
    }

    // ─── M1 — EntityDamageByEntityEvent ──────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;

        String wid = WeaponManager.getWeaponId(player.getInventory().getItemInMainHand());
        if (wid == null) return;

        // Notify blood bar
        plugin.getBloodBarManager().onWeaponHit(player);

        switch (wid) {
            case "kyrsewinter"       -> m1Kyrsewinter(player, target);
            case "stormseye"         -> m1Stormseye(player, target);
            case "hero_blade_fire"   -> target.setFireTicks(100);
            case "hero_blade_ice"    -> slow(target, 2, 60);
            case "hero_blade_wind"   -> launch(target, 0.8);
            case "hero_blade_thunder"-> lightning(target.getLocation());
            case "boltcrusher"       -> m1Boltcrusher(player, target);
            case "hailbreaker"       -> m1Hailbreaker(player, target);
            case "deepspindle"       -> m1Deepspindle(player, target);
            case "red_death"         -> m1RedDeath(player, target);
            case "crypt_blade"       -> wither(target, 1, 100);
            case "bloodfouler"       -> m1Bloodfouler(player, target);
            case "yselys_pyre_keeper"-> target.setFireTicks(60);
            case "soulthorn"         -> m1Soulthorn(player, target);
            case "gale_pale"         -> m1GalePale(player, target);
            case "cold_point"        -> m1ColdPoint(player, target);
            case "iron_requiem"      -> m1IronRequiem(player, target, e);
            case "railblade"         -> {} // railblade M1 is just normal damage
        }
    }

    // ─── Kill — EntityDeathEvent ──────────────────────────────────────────────

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (!(e.getEntity().getKiller() instanceof Player player)) return;
        String wid = WeaponManager.getWeaponId(player.getInventory().getItemInMainHand());
        if (wid == null) return;

        switch (wid) {
            case "red_death" -> {
                // Lifesteal: heal 2 HP (1 heart)
                double newHp = Math.min(player.getMaxHealth(), player.getHealth() + 4.0);
                player.setHealth(newHp);
                spawnParticles(player.getLocation().add(0, 1, 0), Particle.HEART, 6);
                player.sendActionBar(Component.text("❤ Lifesteal!", NamedTextColor.RED));
            }
            case "crypt_blade" -> {
                // Spawn zombie at kill location
                Location loc = e.getEntity().getLocation();
                Zombie zombie = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
                zombie.setCustomName("§cCrypt Servant");
                zombie.setCustomNameVisible(true);
                // Remove zombie after 30 seconds
                new BukkitRunnable() {
                    @Override public void run() {
                        if (zombie.isValid()) zombie.remove();
                    }
                }.runTaskLater(plugin, 600L);
                player.sendActionBar(Component.text("💀 Crypt Servant summoned!", NamedTextColor.DARK_GREEN));
            }
        }
    }

    // ─── Crits — Right-Click ──────────────────────────────────────────────────

    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        var action = e.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Player player = e.getPlayer();
        String wid = WeaponManager.getWeaponId(player.getInventory().getItemInMainHand());
        if (wid == null) return;

        e.setCancelled(true); // prevent block placement etc.

        if (!checkCooldown(player, wid, CRIT_COOLDOWN_MS)) {
            long remaining = getRemainingCooldown(player, wid);
            player.sendActionBar(Component.text("⏱ Cooldown: " + (remaining / 1000.0) + "s", NamedTextColor.YELLOW));
            return;
        }

        switch (wid) {
            case "railblade"          -> critRailblade(player);
            case "yselys_pyre_keeper" -> critPyreKeeper(player);
            case "soulthorn"          -> critSoulthorn(player);
            case "gale_pale"          -> critGalePale(player);
            case "cold_point"         -> critColdPoint(player);
            case "iron_requiem"       -> critIronRequiem(player);
            case "bloodfouler"        -> critBloodfouler(player);
        }
    }

    // ─── Passive effects while holding ───────────────────────────────────────

    @EventHandler
    public void onItemSwitch(PlayerItemHeldEvent e) {
        Player player = e.getPlayer();
        // Remove old passives
        player.removePotionEffect(PotionEffectType.SPEED);

        // Apply new passives
        var newItem = player.getInventory().getItem(e.getNewSlot());
        String wid = WeaponManager.getWeaponId(newItem);
        if (wid == null) return;

        if (wid.equals("railblade") || wid.equals("hero_blade_wind")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, true, true));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  M1 IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    private void m1Kyrsewinter(Player p, LivingEntity target) {
        slow(target, 2, 80);
        // Freeze effect (damage and slowness)
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, true, true));
        spawnParticles(target.getLocation(), Particle.SNOWFLAKE, 20);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 1.5f);
    }

    private void m1Stormseye(Player p, LivingEntity target) {
        lightning(target.getLocation());
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, true, true));
    }

    private void m1Boltcrusher(Player p, LivingEntity target) {
        lightning(target.getLocation());
        // Big knockback
        Vector dir = target.getLocation().subtract(p.getLocation()).toVector().normalize().multiply(2.0);
        dir.setY(0.5);
        target.setVelocity(dir);

        // Hit nearby mobs too
        target.getNearbyEntities(5, 5, 5).forEach(e -> {
            if (e instanceof LivingEntity le && e != p) {
                lightning(le.getLocation());
                le.damage(4.0, p);
            }
        });
        spawnParticles(target.getLocation(), Particle.ELECTRIC_SPARK, 30);
    }

    private void m1Hailbreaker(Player p, LivingEntity target) {
        // AOE slowness in 5 block radius
        target.getNearbyEntities(5, 5, 5).forEach(e -> {
            if (e instanceof LivingEntity le && e != p) {
                slow(le, 2, 80);
                spawnParticles(le.getLocation(), Particle.SNOWFLAKE, 10);
            }
        });
        slow(target, 2, 80);
        spawnParticles(target.getLocation(), Particle.SNOWFLAKE, 25);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
    }

    private void m1Deepspindle(Player p, LivingEntity target) {
        wither(target, 1, 100);
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, true, true));
        spawnParticles(target.getLocation(), Particle.ASH, 30);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.5f, 1.8f);
    }

    private void m1RedDeath(Player p, LivingEntity target) {
        target.setFireTicks(60);
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0, false, true, true));
        spawnParticles(target.getLocation(), Particle.DRIPPING_LAVA, 10);
    }

    private void m1Bloodfouler(Player p, LivingEntity target) {
        // Add blood poison stack to target entity
        var pdc = target.getPersistentDataContainer();
        int stacks = pdc.getOrDefault(DeepwokenWeapons.BLOOD_POISON_KEY, PersistentDataType.INTEGER, 0);
        stacks = Math.min(10, stacks + 1);
        pdc.set(DeepwokenWeapons.BLOOD_POISON_KEY, PersistentDataType.INTEGER, stacks);

        // Apply visual poison effect
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0, false, true, true));
        spawnParticles(target.getLocation(), Particle.DRIPPING_WATER, 8);

        final int finalStacks = stacks;
        target.sendMessage(Component.text("⚠ Blood Poisoning: " + finalStacks + "/10", NamedTextColor.DARK_RED));

        // Schedule DoT tick (0.5 HP per stack per second)
        startBloodPoisonDot(target, p);
    }

    private void m1Soulthorn(Player p, LivingEntity target) {
        // Apply soul mark to target entity PDC
        var pdc = target.getPersistentDataContainer();
        int marks = pdc.getOrDefault(DeepwokenWeapons.SOUL_MARKS_KEY, PersistentDataType.INTEGER, 0);
        if (marks < 3) {
            marks++;
            pdc.set(DeepwokenWeapons.SOUL_MARKS_KEY, PersistentDataType.INTEGER, marks);
        }

        // Visual feedback per mark level
        if (marks == 1) {
            spawnParticles(target.getLocation().add(0,1,0), Particle.END_ROD, 5);
            p.sendActionBar(Component.text("✦ Soul Mark: " + marks + "/3", NamedTextColor.LIGHT_PURPLE));
        } else if (marks == 2) {
            spawnParticles(target.getLocation().add(0,1,0), Particle.END_ROD, 10);
            p.sendActionBar(Component.text("✦✦ Soul Mark: " + marks + "/3", NamedTextColor.LIGHT_PURPLE));
        } else {
            // 3 marks — rotating star aura
            spawnParticles(target.getLocation().add(0,1,0), Particle.TOTEM_OF_UNDYING, 20);
            spawnParticles(target.getLocation().add(0,0.5,0), Particle.END_ROD, 15);
            p.sendActionBar(Component.text("✦✦✦ SOUL MARKED!", NamedTextColor.GOLD));
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 2.0f);
        }
    }

    private void m1GalePale(Player p, LivingEntity target) {
        // Knockdown — heavy knockback + brief slowness (simulates falling down)
        Vector dir = target.getLocation().subtract(p.getLocation()).toVector().normalize().multiply(2.5);
        dir.setY(0.3);
        target.setVelocity(dir);
        slow(target, 1, 40);
        spawnParticles(target.getLocation(), Particle.CLOUD, 12);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.8f);
    }

    private void m1ColdPoint(Player p, LivingEntity target) {
        slow(target, 3, 100);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, true, true));
        spawnParticles(target.getLocation(), Particle.SNOWFLAKE, 15);
        spawnParticles(target.getLocation(), Particle.ITEM_SNOWBALL, 10);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.6f);
    }

    private void m1IronRequiem(Player p, LivingEntity target, EntityDamageByEntityEvent e) {
        int b = bullets.getOrDefault(p.getUniqueId(), 6);
        if (b > 0) {
            // Ranged shot — no extra effects, normal damage
            bullets.put(p.getUniqueId(), b - 1);
            p.sendActionBar(Component.text("🔫 Bullets: " + (b-1) + "/6", NamedTextColor.GRAY));
            spawnParticles(target.getLocation(), Particle.CRIT, 8);
        } else {
            // Rod effect — no bullet, apply rod: slowness + extra posture damage
            slow(target, 0, 40);
            e.setDamage(e.getDamage() * 0.7); // reduced M1 damage without bullet
            spawnParticles(target.getLocation(), Particle.ELECTRIC_SPARK, 5);
            p.sendActionBar(Component.text("⚡ Rod! No bullets left — reload with crit", NamedTextColor.RED));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CRIT IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    private void critRailblade(Player p) {
        // Lunge in look direction
        Vector dir = p.getLocation().getDirection().normalize().multiply(2.5);
        dir.setY(0.3);
        p.setVelocity(dir);
        spawnParticles(p.getLocation(), Particle.CLOUD, 10);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.8f, 2.0f);
        p.sendActionBar(Component.text("💨 Lunge!", NamedTextColor.GRAY));
    }

    private void critPyreKeeper(Player p) {
        if (!p.isOnGround()) {
            // ── In Air: Ground Slam ───────────────────────────────────────────
            p.sendActionBar(Component.text("🔥 Ground Slam!", NamedTextColor.GOLD));
            // Pull player down fast
            p.setVelocity(new Vector(0, -3.0, 0));

            // When they land, AOE damage + fire
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    ticks++;
                    if (p.isOnGround() || ticks > 40) {
                        // AOE explosion effect
                        p.getWorld().createExplosion(p.getLocation(), 0f, false, false);
                        spawnParticles(p.getLocation(), Particle.FLAME, 30);
                        spawnParticles(p.getLocation(), Particle.LAVA, 15);
                        p.getLocation().getNearbyLivingEntities(4).forEach(e -> {
                            if (e != p) {
                                e.damage(8.0, p);
                                e.setFireTicks(80);
                                // Pull toward ground slam point
                                Vector pull = p.getLocation().subtract(e.getLocation()).toVector().normalize().multiply(1.5);
                                e.setVelocity(pull);
                            }
                        });
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);

        } else if (p.isSprinting()) {
            // ── Sprinting: Lunge & Launch ─────────────────────────────────────
            p.sendActionBar(Component.text("🔥 Lunge & Launch!", NamedTextColor.GOLD));
            Vector lunge = p.getLocation().getDirection().normalize().multiply(2.0);
            lunge.setY(0.4);
            p.setVelocity(lunge);

            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    ticks++;
                    // Check for nearby target and launch them
                    p.getNearbyEntities(2.5, 2.5, 2.5).forEach(e -> {
                        if (e instanceof LivingEntity le && e != p) {
                            le.damage(6.0, p);
                            le.setFireTicks(60);
                            Vector launch = new Vector(0, 2.5, 0);
                            le.setVelocity(launch);
                            // Also pull player up with target
                            p.setVelocity(new Vector(0, 1.2, 0));
                            spawnParticles(le.getLocation(), Particle.FLAME, 20);
                        }
                    });
                    if (ticks >= 5) cancel();
                }
            }.runTaskTimer(plugin, 0L, 2L);

        } else if (p.isSneaking()) {
            // ── Crouching: Rapid Slash Combo (5 hits) ────────────────────────
            p.sendActionBar(Component.text("🔥 Rapid Slash Combo!", NamedTextColor.GOLD));
            for (int i = 0; i < 5; i++) {
                final int hit = i;
                new BukkitRunnable() {
                    @Override public void run() {
                        p.getNearbyEntities(2.5, 2.5, 2.5).forEach(e -> {
                            if (e instanceof LivingEntity le && e != p) {
                                le.damage(3.0, p);
                                le.setFireTicks(20);
                                spawnParticles(le.getLocation(), Particle.SWEEP_ATTACK, 3);
                            }
                        });
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.3f + (hit * 0.1f));
                    }
                }.runTaskLater(plugin, (long) (i * 4));
            }
        }
        // No else — standing just does fire M1 (handled above)
    }

    private void critSoulthorn(Player p) {
        // Find nearest target with soul marks
        LivingEntity target = getNearestTarget(p, 20);
        if (target == null) {
            p.sendActionBar(Component.text("No target in range.", NamedTextColor.GRAY));
            return;
        }

        var pdc = target.getPersistentDataContainer();
        int marks = pdc.getOrDefault(DeepwokenWeapons.SOUL_MARKS_KEY, PersistentDataType.INTEGER, 0);

        if (marks == 0) {
            // ── 0 marks: Glow slash ───────────────────────────────────────────
            spawnParticles(p.getLocation(), Particle.TOTEM_OF_UNDYING, 10);
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, true, true));
            p.sendActionBar(Component.text("✦ Soulthorn: Glowing slash", NamedTextColor.LIGHT_PURPLE));

        } else if (marks <= 2) {
            // ── 1-2 marks: Rise + Reel + Detonate ────────────────────────────
            p.sendActionBar(Component.text("✦✦ Soulthorn: Reel & Detonate!", NamedTextColor.LIGHT_PURPLE));
            p.setVelocity(new Vector(0, 1.5, 0)); // rise up

            new BukkitRunnable() {
                @Override public void run() {
                    // Pull target toward player
                    Vector pull = p.getLocation().subtract(target.getLocation()).toVector().normalize().multiply(3.0);
                    target.setVelocity(pull);
                    target.damage(8.0 * marks, p); // scale with marks
                    pdc.remove(DeepwokenWeapons.SOUL_MARKS_KEY); // clear marks
                    spawnParticles(target.getLocation(), Particle.TOTEM_OF_UNDYING, 25);
                    p.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
                }
            }.runTaskLater(plugin, 10L);

        } else {
            // ── 3 marks: Teleport & Downslash ────────────────────────────────
            p.sendActionBar(Component.text("✦✦✦ TRUE HYPERARMOR — Downslash!", NamedTextColor.GOLD));
            // Teleport player above target
            Location above = target.getLocation().add(0, 3, 0);
            p.teleport(above);
            spawnParticles(target.getLocation(), Particle.END_ROD, 20);

            // Resistance while executing (True Hyperarmor)
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 4, false, true, true));

            // Multi-hit downslash
            for (int i = 0; i < 4; i++) {
                new BukkitRunnable() {
                    @Override public void run() {
                        target.damage(6.0, p);
                        p.setVelocity(new Vector(0, -1.5, 0));
                        spawnParticles(target.getLocation(), Particle.SWEEP_ATTACK, 5);
                        p.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.2f);
                    }
                }.runTaskLater(plugin, (long)(i * 6 + 5));
            }

            // Clear soul marks
            pdc.remove(DeepwokenWeapons.SOUL_MARKS_KEY);
        }
    }

    private void critGalePale(Player p) {
        if (p.isSprinting()) {
            // ── Sprinting: Rush Sweep ─────────────────────────────────────────
            p.sendActionBar(Component.text("🌬 Rush Sweep!", NamedTextColor.WHITE));
            Vector lunge = p.getLocation().getDirection().normalize().multiply(3.0);
            p.setVelocity(lunge);

            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    ticks++;
                    p.getNearbyEntities(2.5, 2.5, 2.5).forEach(e -> {
                        if (e instanceof LivingEntity le && e != p) {
                            le.damage(10.0, p);
                            slow(le, 0, 60);
                            le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0, false, true, true));
                            spawnParticles(le.getLocation(), Particle.CLOUD, 8);
                        }
                    });
                    if (ticks >= 6) cancel();
                }
            }.runTaskTimer(plugin, 0L, 2L);

        } else {
            // ── Standing: 360° Sweep ──────────────────────────────────────────
            p.sendActionBar(Component.text("🌬 360° Sweep!", NamedTextColor.WHITE));
            p.getLocation().getNearbyLivingEntities(5).forEach(e -> {
                if (e != p) {
                    e.damage(12.0, p);
                    // Knockdown: high knockback + brief slow
                    Vector kb = e.getLocation().subtract(p.getLocation()).toVector().normalize().multiply(2.0);
                    kb.setY(0.4);
                    e.setVelocity(kb);
                    slow(e, 1, 60);
                    e.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0, false, true, true));
                }
            });
            spawnParticles(p.getLocation(), Particle.CLOUD, 40);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.6f);
        }
    }

    private void critColdPoint(Player p) {
        // Dash through nearest target
        LivingEntity target = getNearestTarget(p, 8);
        if (target == null) {
            p.sendActionBar(Component.text("No target in range.", NamedTextColor.GRAY));
            return;
        }

        p.sendActionBar(Component.text("❄ Pierce Dash!", NamedTextColor.AQUA));
        spawnParticles(p.getLocation(), Particle.SNOWFLAKE, 20);

        // Teleport player through target (to the other side)
        Vector dir = target.getLocation().subtract(p.getLocation()).toVector().normalize();
        Location through = target.getLocation().add(dir.multiply(2.0));
        p.teleport(through);

        target.damage(10.0, p);
        slow(target, 3, 120);
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, true, true));
        spawnParticles(target.getLocation(), Particle.SNOWFLAKE, 30);
        spawnParticles(target.getLocation(), Particle.ITEM_SNOWBALL, 15);
        p.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.8f);
    }

    private void critIronRequiem(Player p) {
        int b = bullets.getOrDefault(p.getUniqueId(), 0);

        if (p.isSprinting()) {
            // ── Sprinting: Spinning Fire ──────────────────────────────────────
            if (b <= 0) {
                p.sendActionBar(Component.text("🔫 No bullets! Load first.", NamedTextColor.RED));
                return;
            }
            p.sendActionBar(Component.text("🔫 Spinning Fire!", NamedTextColor.GRAY));
            int shots = Math.min(4, b);
            for (int i = 0; i < shots; i++) {
                final int shot = i;
                new BukkitRunnable() {
                    @Override public void run() {
                        // Fire in random direction around player
                        double angle = (Math.PI * 2 / shots) * shot;
                        Vector dir = new Vector(Math.cos(angle), 0.1, Math.sin(angle));
                        Snowball ball = p.launchProjectile(Snowball.class, dir.multiply(2.5));
                        handleProjectile(ball, p, true);
                    }
                }.runTaskLater(plugin, (long)(shot * 3));
            }
            bullets.put(p.getUniqueId(), b - shots);
            p.sendActionBar(Component.text("🔫 Bullets: " + (b - shots) + "/6", NamedTextColor.GRAY));

        } else {
            // ── Standing: Explosive Shot (also reloads 1 bullet) ─────────────
            p.sendActionBar(Component.text("🔫 Explosive Shot!", NamedTextColor.GRAY));
            Snowball ball = p.launchProjectile(Snowball.class, p.getLocation().getDirection().multiply(3.0));
            handleProjectile(ball, p, true); // explosive

            // Reload 1 bullet
            int newBullets = Math.min(6, b + 1);
            bullets.put(p.getUniqueId(), newBullets);
            p.sendActionBar(Component.text("🔫 Reloaded! Bullets: " + newBullets + "/6", NamedTextColor.GRAY));
        }
    }

    private void critBloodfouler(Player p) {
        if (p.isSprinting()) {
            // ── Sprinting: Rush Sweep ─────────────────────────────────────────
            p.sendActionBar(Component.text("🩸 Rush Sweep!", NamedTextColor.DARK_RED));
            Vector lunge = p.getLocation().getDirection().normalize().multiply(3.0);
            p.setVelocity(lunge);

            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    ticks++;
                    p.getNearbyEntities(2.5, 2.5, 2.5).forEach(e -> {
                        if (e instanceof LivingEntity le && e != p) {
                            le.damage(9.0, p);
                            m1Bloodfouler(p, le);
                        }
                    });
                    if (ticks >= 5) cancel();
                }
            }.runTaskTimer(plugin, 0L, 2L);

        } else {
            // ── Standing: 360° Blood Infect ───────────────────────────────────
            p.sendActionBar(Component.text("🩸 Blood Infect!", NamedTextColor.DARK_RED));
            p.getLocation().getNearbyLivingEntities(5).forEach(e -> {
                if (e != p) {
                    e.damage(8.0, p);
                    m1Bloodfouler(p, e);
                }
            });
            spawnParticles(p.getLocation(), Particle.DRIPPING_LAVA, 30);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.6f, 0.8f);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PASSIVE TASK (blood poison DoT, passive speed checks)
    // ═══════════════════════════════════════════════════════════════════════════

    private void startPassiveTask() {
        new BukkitRunnable() {
            @Override public void run() {
                // Check each online player's held weapon for passive effects
                for (Player p : Bukkit.getOnlinePlayers()) {
                    String wid = WeaponManager.getWeaponId(p.getInventory().getItemInMainHand());
                    if ((wid != null && (wid.equals("railblade") || wid.equals("hero_blade_wind")))
                            && !p.hasPotionEffect(PotionEffectType.SPEED)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, true, true));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private void lightning(Location loc) {
        loc.getWorld().strikeLightningEffect(loc);
    }

    private void slow(LivingEntity e, int amp, int duration) {
        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amp, false, true, true));
    }

    private void wither(LivingEntity e, int amp, int duration) {
        e.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, duration, amp, false, true, true));
    }

    private void launch(LivingEntity e, double power) {
        e.setVelocity(e.getVelocity().add(new Vector(0, power, 0)));
    }

    private void spawnParticles(Location loc, Particle particle, int count) {
        loc.getWorld().spawnParticle(particle, loc, count, 0.3, 0.3, 0.3, 0.05);
    }

    private LivingEntity getNearestTarget(Player p, double radius) {
        return p.getNearbyEntities(radius, radius, radius).stream()
                .filter(e -> e instanceof LivingEntity && e != p)
                .map(e -> (LivingEntity) e)
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation())))
                .orElse(null);
    }

    private void handleProjectile(Snowball ball, Player owner, boolean explosive) {
        // Store owner reference to handle hit in a separate listener if needed
        // For now use a check task
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks++;
                if (!ball.isValid() || ticks > 60) {
                    cancel();
                    return;
                }
                // Check for nearby entities near the ball
                ball.getNearbyEntities(0.5, 0.5, 0.5).forEach(e -> {
                    if (e instanceof LivingEntity le && e != owner) {
                        le.damage(explosive ? 12.0 : 6.0, owner);
                        if (explosive) {
                            ball.getWorld().createExplosion(ball.getLocation(), 0f, false, false);
                            spawnParticles(ball.getLocation(), Particle.EXPLOSION_EMITTER, 3);
                        }
                        ball.remove();
                        cancel();
                    }
                });
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startBloodPoisonDot(LivingEntity target, Player attacker) {
        // Run a repeating DoT on the entity
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks++;
                if (!target.isValid() || target.isDead()) { cancel(); return; }
                var pdc = target.getPersistentDataContainer();
                int stacks = pdc.getOrDefault(DeepwokenWeapons.BLOOD_POISON_KEY, PersistentDataType.INTEGER, 0);
                if (stacks <= 0) { cancel(); return; }

                // 0.5 HP per stack per second
                target.damage(stacks * 0.5, attacker);
                spawnParticles(target.getLocation().add(0,1,0), Particle.DRIPPING_WATER, 3);

                // Decay stacks over time
                if (ticks % 5 == 0) {
                    pdc.set(DeepwokenWeapons.BLOOD_POISON_KEY, PersistentDataType.INTEGER, Math.max(0, stacks - 1));
                }

                if (ticks > 100) cancel(); // max 10 seconds
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ── Cooldown helpers ──────────────────────────────────────────────────────

    private boolean checkCooldown(Player p, String wid, int ms) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        long last = critCooldowns
                .computeIfAbsent(uuid, k -> new HashMap<>())
                .getOrDefault(wid, 0L);
        if (now - last >= ms) {
            critCooldowns.get(uuid).put(wid, now);
            return true;
        }
        return false;
    }

    private long getRemainingCooldown(Player p, String wid) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        long last = critCooldowns
                .computeIfAbsent(uuid, k -> new HashMap<>())
                .getOrDefault(wid, 0L);
        return Math.max(0, CRIT_COOLDOWN_MS - (now - last));
    }
}
