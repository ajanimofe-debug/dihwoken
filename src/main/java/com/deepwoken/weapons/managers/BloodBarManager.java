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

public class BloodBarManager {

    private final DeepwokenWeapons plugin;
    private final Map<UUID, BossBar> bars  = new HashMap<>();
    private final Map<UUID, Double>  blood = new HashMap<>();

    private static final double DRAIN_PER_TICK = 0.004;
    private static final double GAIN_PER_HIT   = 0.12;
    private static final double RESET_ON_FULL  = 0.5;

    public BloodBarManager(DeepwokenWeapons plugin) {
        this.plugin = plugin;
        startDrainTask();
    }

    public void showBar(Player player) {
        UUID uuid = player.getUniqueId();
        if (bars.containsKey(uuid)) return;
        BossBar bar = Bukkit.createBossBar("§4❤ §cBlood §4❤", BarColor.RED, BarStyle.SEGMENTED_10);
        bar.setProgress(blood.getOrDefault(uuid, 0.0));
        bar.addPlayer(player);
        bars.put(uuid, bar);
        blood.putIfAbsent(uuid, 0.0);
    }

    public void hideBar(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    public void toggleBar(Player player) {
        if (bars.containsKey(player.getUniqueId())) {
            hideBar(player);
            player.sendMessage(Component.text("Blood Bar hidden.", NamedTextColor.GRAY));
        } else {
            showBar(player);
            player.sendMessage(Component.text("Blood Bar shown!", NamedTextColor.RED));
        }
    }

    public void onWeaponHit(Player player) {
        UUID uuid = player.getUniqueId();
        if (!bars.containsKey(uuid)) showBar(player);
        double current = Math.min(1.0, blood.getOrDefault(uuid, 0.0) + GAIN_PER_HIT);
        blood.put(uuid, current);
        updateBar(uuid, current);
        if (current >= 1.0) triggerBloodFury(player);
    }

    private void triggerBloodFury(Player player) {
        blood.put(player.getUniqueId(), RESET_ON_FULL);
        updateBar(player.getUniqueId(), RESET_ON_FULL);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,    60, 0, false, true, true));
        player.getWorld().spawnParticle(org.bukkit.Particle.HEART, player.getLocation().add(0,1,0), 10, 0.5, 0.5, 0.5, 0.1);
        player.sendActionBar(Component.text("⚡ BLOOD FURY! ⚡", NamedTextColor.RED));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SHOOT, 0.6f, 1.8f);
    }

    private void updateBar(UUID uuid, double value) {
        BossBar bar = bars.get(uuid);
        if (bar == null) return;
        double v = Math.max(0.0, Math.min(1.0, value));
        bar.setProgress(v);
        bar.setColor(v >= 0.75 ? BarColor.RED : v >= 0.4 ? BarColor.PINK : BarColor.WHITE);
    }

    private void startDrainTask() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Map.Entry<UUID, Double> entry : blood.entrySet()) {
                    double current = Math.max(0.0, entry.getValue() - DRAIN_PER_TICK);
                    blood.put(entry.getKey(), current);
                    updateBar(entry.getKey(), current);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void cleanup() {
        bars.values().forEach(BossBar::removeAll);
        bars.clear(); blood.clear();
    }
}
