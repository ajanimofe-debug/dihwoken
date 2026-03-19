package com.deepwoken.weapons.managers;

import com.deepwoken.weapons.DeepwokenWeapons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the Blood Bar — a BossBar that fills as the player lands hits.
 * When full it triggers a Strength I burst and resets to half.
 * Drains slowly over time.
 */
public class BloodBarManager {

    private final DeepwokenWeapons plugin;

    /** Maps player UUID → their blood BossBar */
    private final Map<UUID, BossBar> bars = new HashMap<>();

    /** Maps player UUID → current blood amount (0.0 – 1.0) */
    private final Map<UUID, Double> blood = new HashMap<>();

    private static final double DRAIN_PER_TICK  = 0.004;   // per second (20 ticks)
    private static final double GAIN_PER_HIT    = 0.12;    // per successful hit
    private static final double FULL_THRESHOLD  = 1.0;
    private static final double RESET_ON_FULL   = 0.5;

    public BloodBarManager(DeepwokenWeapons plugin) {
        this.plugin = plugin;
        startDrainTask();
    }

    /** Show the blood bar for a player (called on join or toggle). */
    public void showBar(Player player) {
        UUID uuid = player.getUniqueId();
        if (bars.containsKey(uuid)) return; // already shown

        BossBar bar = Bukkit.createBossBar(
            "§4❤ §cBlood §4❤",
            BarColor.RED,
            BarStyle.SEGMENTED_10
        );
        bar.setProgress(blood.getOrDefault(uuid, 0.0));
        bar.addPlayer(player);
        bars.put(uuid, bar);
        blood.putIfAbsent(uuid, 0.0);
    }

    /** Remove the blood bar for a player. */
    public void hideBar(Player player) {
        UUID uuid = player.getUniqueId();
        BossBar bar = bars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    /** Toggle bar on/off. */
    public void toggleBar(Player player) {
        if (bars.containsKey(player.getUniqueId())) {
            hideBar(player);
            player.sendMessage(Component.text("Blood Bar hidden.", NamedTextColor.GRAY));
        } else {
            showBar(player);
            player.sendMessage(Component.text("Blood Bar shown!", NamedTextColor.RED));
        }
    }

    /**
     * Called whenever the player successfully hits an entity with a Deepwoken weapon.
     * Increases blood and checks for the full-bar trigger.
     */
    public void onWeaponHit(Player player) {
        UUID uuid = player.getUniqueId();

        // If they don't have a bar yet, create one automatically
        if (!bars.containsKey(uuid)) showBar(player);

        double current = blood.getOrDefault(uuid, 0.0);
        current = Math.min(FULL_THRESHOLD, current + GAIN_PER_HIT);
        blood.put(uuid, current);
        updateBar(uuid, current);

        if (current >= FULL_THRESHOLD) {
            triggerBloodFury(player);
        }
    }

    /** Blood Fury — fires when the bar fills completely. */
    private void triggerBloodFury(Player player) {
        // Reset bar to half
        blood.put(player.getUniqueId(), RESET_ON_FULL);
        updateBar(player.getUniqueId(), RESET_ON_FULL);

        // Grant Strength I + Speed I briefly
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, false, true, true));

        // Visual + sound feedback
        player.getWorld().spawnParticle(
            org.bukkit.Particle.HEART,
            player.getLocation().add(0, 1, 0),
            10, 0.5, 0.5, 0.5, 0.1
        );
        player.sendActionBar(Component.text("⚡ BLOOD FURY! ⚡", NamedTextColor.RED));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SHOOT, 0.6f, 1.8f);
    }

    private void updateBar(UUID uuid, double value) {
        BossBar bar = bars.get(uuid);
        if (bar == null) return;
        double clamped = Math.max(0.0, Math.min(1.0, value));
        bar.setProgress(clamped);

        // Change color based on fill level
        if (clamped >= 0.75) {
            bar.setColor(BarColor.RED);
        } else if (clamped >= 0.4) {
            bar.setColor(BarColor.PINK);
        } else {
            bar.setColor(BarColor.WHITE);
        }
    }

    /** Passive drain so the bar doesn't stay full indefinitely. */
    private void startDrainTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Double> entry : blood.entrySet()) {
                    UUID uuid = entry.getKey();
                    double current = entry.getValue();
                    if (current <= 0.0) continue;

                    current = Math.max(0.0, current - DRAIN_PER_TICK);
                    blood.put(uuid, current);
                    updateBar(uuid, current);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /** Clean up all bars on plugin disable. */
    public void cleanup() {
        bars.values().forEach(BossBar::removeAll);
        bars.clear();
        blood.clear();
    }
}
