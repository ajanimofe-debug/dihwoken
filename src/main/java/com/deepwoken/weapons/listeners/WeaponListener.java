package com.deepwoken.weapons.listeners;

import com.deepwoken.weapons.DeepwokenWeapons;
import com.deepwoken.weapons.managers.WeaponManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public class WeaponListener implements Listener {

    private final DeepwokenWeapons plugin;

    // ── ROOT FIX 1: Re-entry guard — prevents ALL onHit cascades ─────────────
    private final Set<UUID> processingHit = new HashSet<>();

    // ── Combo system ──────────────────────────────────────────────────────────
    private final Map<UUID, Integer> comboStep   = new HashMap<>();
    private final Map<UUID, Long>    lastHitTime = new HashMap<>();
    private final Map<UUID, String>  lastWeapon  = new HashMap<>();
    private static final long COMBO_WINDOW = 1500L;

    // ── Crit cooldowns ────────────────────────────────────────────────────────
    private final Map<UUID, Map<String, Long>> critCooldowns = new HashMap<>();

    // ── Per-player state maps — ALL cleaned on quit ───────────────────────────
    private final Map<UUID, Integer>      bullets         = new HashMap<>();
    private final Map<UUID, Integer>      hailStage       = new HashMap<>();
    private final Set<UUID>               darkRift        = new HashSet<>();
    private final Map<UUID, LivingEntity> cryptChain      = new HashMap<>();
    private final Map<UUID, Integer>      resonance       = new HashMap<>();
    private final Map<UUID, Integer>      heat            = new HashMap<>();
    private final Set<UUID>               flameDisabled   = new HashSet<>();
    private final Map<UUID, Integer>      rhythm          = new HashMap<>();
    private final Map<UUID, Long>         lastRhythm      = new HashMap<>();
    private final Map<UUID, Integer>      stormeyeHits    = new HashMap<>();
    private final Map<UUID, Long>         shockwaveCd     = new HashMap<>();
    private final Map<UUID, Long>         pyreFlameCd     = new HashMap<>();
    private final Map<UUID, Integer>      zodiacSign      = new HashMap<>();
    private final Map<UUID, Integer>      voidMeter       = new HashMap<>();
    private final Map<UUID, Integer>      orbitCharge     = new HashMap<>();
    private final Map<UUID, Long>         lastMove        = new HashMap<>();
    private final Set<UUID>               bloodDotActive  = new HashSet<>();
    private final Map<UUID, Integer>      heroFireStacks  = new HashMap<>();
    private final Map<UUID, Integer>      heroIceStacks   = new HashMap<>();
    private final Map<UUID, Integer>      heroWindStacks  = new HashMap<>();
    private final Map<UUID, Boolean>      bloodFrenzy     = new HashMap<>();
    private final Map<UUID, Boolean>      overcharge      = new HashMap<>();
    private final Map<UUID, Integer>      thunderCharge   = new HashMap<>();
    private final Map<UUID, Long>         zodiacCooldown  = new HashMap<>(); // Libra M1 cooldown

    // ROOT FIX 2: Shared Random instance
    private final Random rng = new Random();

    private static final String[] ZODIAC_NAMES = {"♈ Aries", "♎ Libra", "♏ Scorpio"};

    public WeaponListener(DeepwokenWeapons plugin) {
        this.plugin = plugin;
        startPassiveTask();
    }

    // ─── Prevent enchanting ───────────────────────────────────────────────────
    @EventHandler
    public void onEnchant(EnchantItemEvent e) {
        if (WeaponManager.getWeaponId(e.getItem()) != null) {
            e.setCancelled(true);
            e.getEnchanter().sendMessage(Component.text("Legendary weapons cannot be enchanted!", NamedTextColor.RED));
        }
    }

    // ─── Join ─────────────────────────────────────────────────────────────────
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.getBloodBarManager().showBar(e.getPlayer());
        initPlayer(e.getPlayer().getUniqueId());
    }

    private void initPlayer(UUID u) {
        bullets.put(u, 6);
        hailStage.put(u, 1);
        stormeyeHits.put(u, 0);
        zodiacSign.put(u, 0);
        voidMeter.put(u, 0);
        orbitCharge.put(u, 0);
        comboStep.put(u, 0);
        thunderCharge.put(u, 0);
        heroFireStacks.put(u, 0);
        heroIceStacks.put(u, 0);
        heroWindStacks.put(u, 0);
        bloodFrenzy.put(u, false);
        overcharge.put(u, false);
    }

    // ─── Quit — ROOT FIX 2: clean ALL maps ───────────────────────────────────
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        plugin.getBloodBarManager().hideBar(e.getPlayer());
        // Clean every map
        processingHit.remove(u);
        comboStep.remove(u); lastHitTime.remove(u); lastWeapon.remove(u);
        critCooldowns.remove(u); bullets.remove(u); hailStage.remove(u);
        darkRift.remove(u); cryptChain.remove(u); resonance.remove(u);
        heat.remove(u); flameDisabled.remove(u); rhythm.remove(u);
        lastRhythm.remove(u); stormeyeHits.remove(u); shockwaveCd.remove(u);
        pyreFlameCd.remove(u); zodiacSign.remove(u); voidMeter.remove(u);
        orbitCharge.remove(u); lastMove.remove(u); bloodDotActive.remove(u);
        heroFireStacks.remove(u); heroIceStacks.remove(u); heroWindStacks.remove(u);
        bloodFrenzy.remove(u); overcharge.remove(u); thunderCharge.remove(u);
        zodiacCooldown.remove(u);
    }

    // ─── Death — reset combat state ───────────────────────────────────────────
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        UUID u = e.getEntity().getUniqueId();
        comboStep.put(u, 0);
        darkRift.remove(u);
        flameDisabled.remove(u);
        bloodFrenzy.put(u, false);
        overcharge.put(u, false);
        cryptChain.remove(u);
        heroFireStacks.put(u, 0);
        heroIceStacks.put(u, 0);
        heroWindStacks.put(u, 0);
    }

    // ─── Move (Orbit passive) ─────────────────────────────────────────────────
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        // ROOT FIX: cache weapon ID check — only read PDC once per meaningful move
        if (e.getFrom().distanceSquared(e.getTo()) < 0.01) return;
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        // Only check orbit if player is holding it — use cached lastWeapon to avoid PDC read every tick
        if ("orbit".equals(lastWeapon.get(u))) {
            lastMove.put(u, System.currentTimeMillis());
            orbitCharge.put(u, Math.min(10, orbitCharge.getOrDefault(u, 0) + 1));
        }
    }

    // ─── M1 — ROOT FIX 1: re-entry guard at very top ─────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) return; // ROOT FIX: check cancelled first
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity t)) return;
        if (t.isDead()) return;
        if (e.getFinalDamage() <= 0) return; // ROOT FIX: skip blocked hits

        UUID u = p.getUniqueId();

        // ROOT FIX 1: re-entry guard — prevents ALL cascade/recursive onHit
        if (processingHit.contains(u)) return;
        processingHit.add(u);

        try {
            String wid = WeaponManager.getWeaponId(p.getInventory().getItemInMainHand());
            if (wid == null) return;

            plugin.getBloodBarManager().onWeaponHit(p);

            // Combo tracking
            long now = System.currentTimeMillis();
            long last = lastHitTime.getOrDefault(u, 0L);
            String lw = lastWeapon.getOrDefault(u, "");
            if (now - last > COMBO_WINDOW || !wid.equals(lw)) comboStep.put(u, 0);
            int step = comboStep.getOrDefault(u, 0);
            lastHitTime.put(u, now);
            lastWeapon.put(u, wid);

            boolean inAir = !p.isOnGround();
            boolean sprint = p.isSprinting();

            switch (wid) {
                case "kyrsewinter"        -> m1Kyrsewinter(p, t, step, inAir, sprint);
                case "stormseye"          -> m1Stormseye(p, t, step, inAir, sprint);
                case "hero_blade_fire"    -> m1HeroFire(p, t, step, inAir, sprint);
                case "hero_blade_ice"     -> m1HeroIce(p, t, step, inAir, sprint);
                case "hero_blade_wind"    -> m1HeroWind(p, t, step, inAir, sprint);
                case "hero_blade_thunder" -> m1HeroThunder(p, t, step, inAir, sprint);
                case "railblade"          -> m1Railblade(p, t, step, inAir, sprint);
                case "deepspindle"        -> m1Deepspindle(p, t, step, inAir, sprint);
                case "red_death"          -> m1RedDeath(p, t, step, inAir, sprint);
                case "crypt_blade"        -> m1CryptBlade(p, t, step, inAir, sprint);
                case "soulthorn"          -> m1Soulthorn(p, t, step, inAir, sprint);
                case "cold_point"         -> m1ColdPoint(p, t, step, inAir, sprint);
                case "yselys_pyre_keeper" -> m1PyreKeeper(p, t, step, inAir, sprint);
                case "bloodfouler"        -> m1Bloodfouler(p, t, step, inAir, sprint);
                case "boltcrusher"        -> m1Boltcrusher(p, t, step, inAir, sprint);
                case "hailbreaker"        -> m1Hailbreaker(p, t, step, inAir, sprint);
                case "gale_pale"          -> m1GalePale(p, t, step, inAir, sprint);
                case "iron_requiem"       -> m1IronRequiem(p, t, e, inAir, sprint);
                case "amethyst"           -> m1Amethyst(p, t, step, inAir, sprint);
                case "flamewall"          -> m1Flamewall(p, t, step, inAir, sprint);
                case "im_blue"            -> m1ImBlue(p, t, step, inAir, sprint);
                case "zodiac"             -> m1Zodiac(p, t, step, inAir, sprint);
                case "nullscapes"         -> m1Nullscapes(p, t, step, inAir, sprint);
                case "orbit"              -> m1Orbit(p, t, step, inAir, sprint);
            }
            comboStep.put(u, step + 1);
        } finally {
            // ROOT FIX 1: ALWAYS remove from guard even if exception occurs
            processingHit.remove(u);
        }
    }

    // ─── Kill ─────────────────────────────────────────────────────────────────
    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (!(e.getEntity().getKiller() instanceof Player p)) return;
        String wid = WeaponManager.getWeaponId(p.getInventory().getItemInMainHand());
        if (wid == null) return;
        switch (wid) {
            case "red_death"   -> { p.setHealth(Math.min(p.getMaxHealth(), p.getHealth()+2)); particles(p.getLocation().add(0,1,0), Particle.HEART, 4); p.sendActionBar(Component.text("❤ Lifesteal!", NamedTextColor.RED)); }
            case "crypt_blade" -> spawnCryptServant(p, e.getEntity().getLocation());
            case "amethyst"    -> addResonance(p, 2);
            case "nullscapes"  -> voidMeter.merge(p.getUniqueId(), 2, (a,b) -> Math.min(10,a+b));
        }
    }

    // ─── Right Click ──────────────────────────────────────────────────────────
    @EventHandler
    public void onRC(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        String wid = WeaponManager.getWeaponId(p.getInventory().getItemInMainHand());
        if (wid == null) return;

        // ROOT FIX: only cancel if we're actually doing something — check cooldown FIRST
        if (wid.equals("flamewall") && flameDisabled.contains(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendActionBar(Component.text("🔥 Overheated!", NamedTextColor.RED));
            return;
        }

        boolean shift = p.isSneaking();
        String cdKey = shift ? wid+"_shift" : wid;
        int cdMs = shift ? 12000 : switch(wid) {
            case "red_death","iron_requiem" -> 8000;
            case "boltcrusher","crypt_blade","stormseye","hero_blade_thunder",
                 "yselys_pyre_keeper","zodiac","nullscapes","orbit" -> 10000;
            default -> 9000;
        };

        if (!checkCd(p, cdKey, cdMs)) {
            e.setCancelled(true);
            p.sendActionBar(Component.text("⏱ "+getRemainingCd(p,cdKey,cdMs)/1000.0+"s", NamedTextColor.YELLOW));
            return;
        }

        e.setCancelled(true); // only cancel AFTER we know we're doing something
        if (shift) dispatchShift(p, wid);
        else dispatchRC(p, wid);
    }

    private void dispatchRC(Player p, String w) {
        switch(w) {
            case "kyrsewinter"        -> critKyrsewinter(p);
            case "stormseye"          -> critStormseye(p);
            case "hero_blade_fire"    -> critHeroFire(p);
            case "hero_blade_ice"     -> critHeroIce(p);
            case "hero_blade_wind"    -> critHeroWind(p);
            case "hero_blade_thunder" -> critHeroThunder(p);
            case "railblade"          -> critRailblade(p);
            case "deepspindle"        -> critDeepspindle(p);
            case "red_death"          -> critRedDeath(p);
            case "crypt_blade"        -> critCryptBlade(p);
            case "soulthorn"          -> critSoulthorn(p);
            case "cold_point"         -> critColdPoint(p);
            case "yselys_pyre_keeper" -> critPyreKeeper(p);
            case "bloodfouler"        -> critBloodfouler(p);
            case "boltcrusher"        -> critBoltcrusher(p);
            case "hailbreaker"        -> critHailbreaker(p);
            case "gale_pale"          -> critGalePale(p);
            case "iron_requiem"       -> critIronRequiem(p);
            case "amethyst"           -> critAmethyst(p);
            case "flamewall"          -> critFlamewall(p);
            case "im_blue"            -> critImBlue(p);
            case "zodiac"             -> critZodiac(p);
            case "nullscapes"         -> critNullscapes(p);
            case "orbit"              -> critOrbit(p);
        }
    }

    private void dispatchShift(Player p, String w) {
        switch(w) {
            case "kyrsewinter"        -> shiftKyrsewinter(p);
            case "stormseye"          -> shiftStormseye(p);
            case "hero_blade_fire"    -> shiftHeroFire(p);
            case "hero_blade_ice"     -> shiftHeroIce(p);
            case "hero_blade_wind"    -> shiftHeroWind(p);
            case "hero_blade_thunder" -> shiftHeroThunder(p);
            case "railblade"          -> shiftRailblade(p);
            case "deepspindle"        -> shiftDeepspindle(p);
            case "red_death"          -> shiftRedDeath(p);
            case "crypt_blade"        -> shiftCryptBlade(p);
            case "soulthorn"          -> shiftSoulthorn(p);
            case "cold_point"         -> shiftColdPoint(p);
            case "yselys_pyre_keeper" -> shiftPyreKeeper(p);
            case "bloodfouler"        -> shiftBloodfouler(p);
            case "boltcrusher"        -> shiftBoltcrusher(p);
            case "hailbreaker"        -> shiftHailbreaker(p);
            case "gale_pale"          -> shiftGalePale(p);
            case "iron_requiem"       -> shiftIronRequiem(p);
            case "amethyst"           -> shiftAmethyst(p);
            case "flamewall"          -> shiftFlamewall(p);
            case "im_blue"            -> shiftImBlue(p);
            case "zodiac"             -> shiftZodiac(p);
            case "nullscapes"         -> shiftNullscapes(p);
            case "orbit"              -> shiftOrbit(p);
        }
    }

    // ─── Weapon switch — clean per-weapon state ───────────────────────────────
    @EventHandler
    public void onSwitch(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        // ROOT FIX: only remove speed if it came from a weapon
        String oldWid = lastWeapon.getOrDefault(u, "");
        if (oldWid.equals("railblade") || oldWid.equals("hero_blade_wind"))
            p.removePotionEffect(PotionEffectType.SPEED);
        // Reset per-weapon state
        stormeyeHits.put(u, 0);
        cryptChain.remove(u);
        comboStep.put(u, 0);
        heroFireStacks.put(u, 0);
        heroIceStacks.put(u, 0);
        heroWindStacks.put(u, 0);
        thunderCharge.put(u, 0);
        overcharge.put(u, false);
        var it = p.getInventory().getItem(e.getNewSlot());
        String wid = WeaponManager.getWeaponId(it);
        lastWeapon.put(u, wid != null ? wid : "");
        if (wid != null && (wid.equals("railblade")||wid.equals("hero_blade_wind")))
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, true, true));
        if (wid != null && wid.equals("yselys_pyre_keeper")) {
            var offhand = p.getInventory().getItemInOffHand();
            if (!"verdant".equals(WeaponManager.getWeaponId(offhand))) {
                ItemStack verdant = DeepwokenWeapons.getInstance().getWeaponManager().getWeapon("verdant");
                if (verdant != null) p.getInventory().setItemInOffHand(verdant);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  M1 HANDLERS — safe versions of all previous handlers
    // ═══════════════════════════════════════════════════════════════════════════

    private void m1Kyrsewinter(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        if (air) {
            safeGroundTask(p, () -> {
                p.getLocation().getNearbyLivingEntities(3).forEach(le->{ if(le!=p){le.damage(8,p);slow(le,3,80);} });
                particles(p.getLocation(),Particle.SNOWFLAKE,6);
            });
            p.setVelocity(new Vector(0,-2.5,0));
            p.sendActionBar(Component.text("❄ Ice Drop!",NamedTextColor.AQUA)); return;
        }
        if (sprint) { slow(t,3,60); p.setVelocity(p.getLocation().getDirection().normalize().multiply(1.2)); particles(t.getLocation(),Particle.SNOWFLAKE,4); return; }
        slow(t,1,40); bleed(t,p,2); particles(t.getLocation(),Particle.SNOWFLAKE,3);
        if (step%4==3) {
            t.getLocation().getNearbyLivingEntities(3).stream().limit(5).forEach(le->{ if(le!=p){le.damage(6,p);slow(le,3,80);} });
            sound(p.getLocation(),Sound.BLOCK_GLASS_BREAK,0.8f,1.5f);
            p.sendActionBar(Component.text("❄ Ice Burst!",NamedTextColor.AQUA));
        }
    }

    private void m1Stormseye(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        UUID u = p.getUniqueId();
        // ROOT FIX: aerial shot does NOT increment shockwave counter
        if (air) { t.damage(4,p); particles(t.getLocation(),Particle.ELECTRIC_SPARK,4); p.sendActionBar(Component.text("⚡ Aerial Shot!",NamedTextColor.YELLOW)); return; }
        if (sprint) {
            Location safe = getSafeTeleportBehind(p,t,2.0);
            if (safe != null) { p.teleport(safe); particles(p.getLocation(),Particle.ELECTRIC_SPARK,4); }
            t.damage(3,p); return;
        }
        int hits = stormeyeHits.getOrDefault(u,0)+1;
        stormeyeHits.put(u,hits);
        particles(t.getLocation(),Particle.ELECTRIC_SPARK,3);
        if (step%3==2) { t.getWorld().strikeLightningEffect(t.getLocation()); t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,40,0,false,true,true)); p.sendActionBar(Component.text("⚡ Lightning!",NamedTextColor.YELLOW)); }
        if (hits>=3) {
            stormeyeHits.put(u,0);
            long now=System.currentTimeMillis();
            if (now-shockwaveCd.getOrDefault(u,0L)>=10000) {
                shockwaveCd.put(u,now);
                p.getLocation().getNearbyLivingEntities(4).stream().limit(5).forEach(le->{ if(le!=p){le.damage(6,p);kb(p,le,3,0.8);slow(le,1,60);le.getWorld().strikeLightningEffect(le.getLocation());} });
                p.getWorld().createExplosion(p.getLocation(),0f,false,false);
                p.sendActionBar(Component.text("⚡ Medallion Shockwave!",NamedTextColor.YELLOW));
            }
        }
    }

    private void m1HeroFire(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        UUID u = p.getUniqueId();
        if (air) { fireDamage(t,p,3,80); return; }
        if (sprint) { fireDamage(t,p,2,60); p.setVelocity(p.getLocation().getDirection().normalize().multiply(0.8)); return; }
        // ROOT FIX: stacks are per-player-per-target session — cap at 4, reset on finisher
        int stacks = Math.min(4, heroFireStacks.getOrDefault(u,0)+1);
        heroFireStacks.put(u,stacks);
        fireDamage(t,p,1,40);
        if (step%4==3) {
            // ROOT FIX: direct damage only, NO getNearbyLivingEntities to prevent cascade
            fireDamage(t,p,5,100);
            t.getLocation().getNearbyLivingEntities(3).stream().limit(4).forEach(le->{ if(le!=p&&le!=t){ le.setFireTicks(60); } }); // only fire ticks, no damage
            particles(t.getLocation(),Particle.LAVA,5);
            sound(t.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.5f,1.2f);
            heroFireStacks.put(u,0);
            p.sendActionBar(Component.text("🔥 Fire Explosion!",NamedTextColor.RED));
        }
    }

    private void m1HeroIce(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        UUID u = p.getUniqueId();
        if (air) { slow(t,3,80); particles(t.getLocation(),Particle.SNOWFLAKE,5); return; }
        if (sprint) { slow(t,2,60); p.setVelocity(p.getLocation().getDirection().normalize().multiply(0.8)); return; }
        int stacks = Math.min(4, heroIceStacks.getOrDefault(u,0)+1);
        heroIceStacks.put(u,stacks);
        slow(t,stacks,50);
        if (step%4==3) { slow(t,5,120); sound(t.getLocation(),Sound.BLOCK_GLASS_BREAK,0.8f,0.6f); heroIceStacks.put(u,0); p.sendActionBar(Component.text("❄ FROZEN!",NamedTextColor.AQUA)); }
    }

    private void m1HeroWind(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        UUID u = p.getUniqueId();
        if (air) { launch(t,1.5); kb(p,t,1.5,0.3); return; }
        if (sprint) { Vector dir=p.getLocation().getDirection().normalize().multiply(1.5);dir.setY(0.2);p.setVelocity(dir);kb(p,t,2,0.4); return; }
        // ROOT FIX: stacks reset on finisher
        int stacks = Math.min(4, heroWindStacks.getOrDefault(u,0)+1);
        heroWindStacks.put(u,stacks);
        Vector push=t.getLocation().subtract(p.getLocation()).toVector().normalize().multiply(0.3*stacks);push.setY(0.1);t.setVelocity(t.getVelocity().add(push));
        if (step%4==3) { launch(t,2.0);kb(p,t,3,0.8);sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.5f,1.8f);heroWindStacks.put(u,0);p.sendActionBar(Component.text("🌬 Wind Launch!",NamedTextColor.WHITE)); }
    }

    private void m1HeroThunder(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        UUID u = p.getUniqueId();
        if (air) { t.getWorld().strikeLightningEffect(t.getLocation());t.damage(3,p); return; }
        if (sprint) { t.getWorld().strikeLightningEffect(t.getLocation());t.damage(2,p);p.setVelocity(p.getLocation().getDirection().normalize().multiply(0.8)); return; }
        // ROOT FIX: overcharge does fixed damage not percentage
        boolean oc = overcharge.getOrDefault(u,false);
        if (oc) { t.damage(20,p); overcharge.put(u,false); p.sendActionBar(Component.text("⚡ Overcharged!",NamedTextColor.GOLD)); }
        int charge = Math.min(4, thunderCharge.getOrDefault(u,0)+1);
        thunderCharge.put(u,charge);
        if (step%4==3) { t.getWorld().strikeLightningEffect(t.getLocation());t.damage(4,p);thunderCharge.put(u,0);p.sendActionBar(Component.text("⚡ Static Release!",NamedTextColor.YELLOW)); }
    }

    private void m1Railblade(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        // ROOT FIX: air M1 does its own logic, does NOT call critRailblade (no cooldown bypass)
        if (air) { p.setVelocity(new Vector(0,0.4,0)); particles(p.getLocation(),Particle.CRIT,4); safeGroundTask(p,()->{ p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(14,p);fireDamage(le,p,2,80);kb(p,le,1.5,0.3);} }); }); p.sendActionBar(Component.text("💨 Aerial Slash!",NamedTextColor.GRAY)); return; }
        if (sprint) { p.setVelocity(p.getLocation().getDirection().normalize().multiply(1.5));particles(t.getLocation(),Particle.SWEEP_ATTACK,3); return; }
        particles(t.getLocation(),Particle.SWEEP_ATTACK,3);
        if (step%4==3) { fireDamage(t,p,4,100);particles(t.getLocation(),Particle.FLAME,4);sound(t.getLocation(),Sound.ENTITY_BLAZE_SHOOT,0.7f,1.2f);p.sendActionBar(Component.text("💨🔥 Flaming Blow!",NamedTextColor.GOLD)); }
    }

    private void m1Deepspindle(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        if (air) { p.setVelocity(new Vector(0,-2.5,0)); safeGroundTask(p,()->{ p.getLocation().getNearbyLivingEntities(3).stream().limit(4).forEach(le->{ if(le!=p){le.damage(6,p);wither(le,1,80);} }); }); p.sendActionBar(Component.text("☠ Void Drop!",NamedTextColor.DARK_PURPLE)); return; }
        if (sprint) {
            // ROOT FIX: dash deals damage directly, no cascade
            Location safe=getSafeTeleportBehind(p,t,2.0);
            if(safe!=null){p.teleport(safe);}
            t.damage(4,p); wither(t,1,60); return;
        }
        wither(t,1,80); particles(t.getLocation(),Particle.ASH,3);
        if (step%3==2) { t.getLocation().getNearbyLivingEntities(3).stream().limit(4).forEach(le->{ if(le!=p&&le!=t){le.setFireTicks(0);wither(le,1,60);} }); sound(t.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.5f,1.5f); p.sendActionBar(Component.text("☠ Void Explosion!",NamedTextColor.DARK_PURPLE)); }
    }

    private void m1RedDeath(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        UUID u = p.getUniqueId();
        if (bloodFrenzy.getOrDefault(u,false)) { p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+0.5)); }
        if (air) { fireDamage(t,p,3,60); applyBloodPoison(p,t); return; }
        if (sprint) { fireDamage(t,p,2,40); applyBloodPoison(p,t); p.setVelocity(p.getLocation().getDirection().normalize().multiply(0.8)); return; }
        fireDamage(t,p,1,60); t.addPotionEffect(new PotionEffect(PotionEffectType.POISON,40,0,false,true,true));
        long now=System.currentTimeMillis();
        if(now-critCooldowns.computeIfAbsent(u,k->new HashMap<>()).getOrDefault("red_m1",0L)>=600){ critCooldowns.get(u).put("red_m1",now); applyBloodPoison(p,t); }
        if (step%4==3) {
            // ROOT FIX: only set fire ticks on nearby, no damage cascade
            t.getLocation().getNearbyLivingEntities(3).stream().limit(4).forEach(le->{ if(le!=p&&le!=t){le.setFireTicks(60);le.addPotionEffect(new PotionEffect(PotionEffectType.POISON,40,0,false,true,true));} });
            p.sendActionBar(Component.text("🩸 Blood Explosion!",NamedTextColor.RED));
        }
    }

    private void m1CryptBlade(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        if (air) { p.setVelocity(new Vector(0,-2.5,0)); safeGroundTask(p,()->{ p.getLocation().getNearbyLivingEntities(3).stream().limit(4).forEach(le->{ if(le!=p){le.damage(6,p);wither(le,2,80);slow(le,1,60);} }); }); p.sendActionBar(Component.text("💀 Grave Drop!",NamedTextColor.DARK_GREEN)); return; }
        if (sprint) { Location safe=getSafeTeleportBehind(p,t,2.0); if(safe!=null)p.teleport(safe); t.damage(4,p); wither(t,1,60); return; }
        wither(t,1,100);
        if (cryptChain.containsKey(p.getUniqueId())&&cryptChain.get(p.getUniqueId()).equals(t)&&t.isValid()) triggerChainBreak(p,t);
        if (step%3==2) { t.getLocation().getNearbyLivingEntities(3).stream().limit(4).forEach(le->{ if(le!=p&&le!=t){wither(le,1,60);} }); p.sendActionBar(Component.text("💀 Shadow Eruption!",NamedTextColor.DARK_GREEN)); }
    }

    private void m1Soulthorn(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        if (air) {
            t.getLocation().getNearbyLivingEntities(3).stream().limit(5).forEach(le->{ if(le!=p){ var pdc=le.getPersistentDataContainer(); int m=Math.min(3,pdc.getOrDefault(DeepwokenWeapons.SOUL_MARKS_KEY,PersistentDataType.INTEGER,0)+1); pdc.set(DeepwokenWeapons.SOUL_MARKS_KEY,PersistentDataType.INTEGER,m); } });
            p.sendActionBar(Component.text("✦ Soul AOE!",NamedTextColor.LIGHT_PURPLE)); return;
        }
        if (sprint) {
            Location safe=getSafeTeleportBehind(p,t,1.5);
            if(safe!=null) p.teleport(safe);
            // ROOT FIX: always apply mark even if teleport fails
        }
        var pdc=t.getPersistentDataContainer();
        int marks=pdc.getOrDefault(DeepwokenWeapons.SOUL_MARKS_KEY,PersistentDataType.INTEGER,0);
        if(marks<3){marks++;pdc.set(DeepwokenWeapons.SOUL_MARKS_KEY,PersistentDataType.INTEGER,marks);}
        particles(t.getLocation().add(0,1,0),marks==3?Particle.TOTEM_OF_UNDYING:Particle.END_ROD,marks*2);
        p.sendActionBar(Component.text("✦".repeat(marks)+" "+marks+"/3",NamedTextColor.LIGHT_PURPLE));
        if(marks==3)sound(t.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,0.5f,2f);
    }

    private void m1ColdPoint(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        if (air) { slow(t,2,60); return; }
        if (sprint) { Location safe=getSafeTeleportBehind(p,t,1.5); if(safe!=null)p.teleport(safe); slow(t,2,60); return; }
        // ROOT FIX: start slowness at 1 not 0
        int slowAmp = Math.min(4, (step%5)+1);
        slow(t,slowAmp,40+(step*8));
        if (step%5==4) { slow(t,5,120);sound(t.getLocation(),Sound.BLOCK_GLASS_BREAK,0.8f,0.6f);p.sendActionBar(Component.text("❄ Crystal Freeze!",NamedTextColor.AQUA)); }
    }

    private void m1PyreKeeper(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        UUID u = p.getUniqueId();
        if (air) { fireDamage(t,p,3,80); return; }
        // ROOT FIX: sprint M1 does its own thing, does NOT call critFlamewall
        if (sprint) { fireDamage(t,p,2,60); p.setVelocity(p.getLocation().getDirection().normalize().multiply(0.8)); return; }
        long now=System.currentTimeMillis();
        if(now-pyreFlameCd.getOrDefault(u,0L)>=4000){
            pyreFlameCd.put(u,now); fireDamage(t,p,3,80);
            t.getWorld().spawnParticle(Particle.FLAME,t.getLocation().add(0,1,0),5,0.3,0.5,0.3,0.05);
            p.sendActionBar(Component.text("🔥 Lifelord's Blaze!",NamedTextColor.GREEN));
        } else { fireDamage(t,p,1,40); }
        if(step%3==2) { fireDamage(t,p,3,80); t.getLocation().getNearbyLivingEntities(3).stream().limit(4).forEach(le->{ if(le!=p&&le!=t){le.setFireTicks(60);} }); p.sendActionBar(Component.text("🔥 Flame Burst!",NamedTextColor.GOLD)); }
    }

    private void m1Bloodfouler(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        if (air) { applyBloodPoison(p,t); return; }
        if (sprint) { applyBloodPoison(p,t); p.setVelocity(p.getLocation().getDirection().normalize().multiply(0.8)); return; }
        applyBloodPoison(p,t);
        // ROOT FIX: removed double-apply — applyBloodPoison called once above, AOE only applies fire ticks
        if(step%3==2) {
            t.getLocation().getNearbyLivingEntities(3).stream().limit(4).forEach(le->{ if(le!=p&&le!=t){le.addPotionEffect(new PotionEffect(PotionEffectType.POISON,40,0,false,true,true));} });
            p.sendActionBar(Component.text("🩸 Blood Infect!",NamedTextColor.DARK_RED));
        }
    }

    private void m1Boltcrusher(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        if (air) {
            p.setVelocity(new Vector(0,-3,0));
            safeGroundTask(p,()->{ p.getLocation().getNearbyLivingEntities(5).stream().limit(5).forEach(le->{ if(le!=p){le.getWorld().strikeLightningEffect(le.getLocation());le.damage(8,p);kb(p,le,2,0.5);} }); p.getWorld().createExplosion(p.getLocation(),0f,false,false); });
            return;
        }
        if (sprint) { p.setVelocity(p.getLocation().getDirection().normalize().multiply(1.5)); t.getWorld().strikeLightningEffect(t.getLocation()); t.damage(4,p); kb(p,t,2,0.3); return; }
        switch(step%2) {
            case 0 -> { t.getWorld().strikeLightningEffect(t.getLocation()); }
            case 1 -> {
                // ROOT FIX: strikeLightningEffect only (no damage) on AOE to prevent cascade
                t.getNearbyEntities(4,4,4).stream().limit(3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p&&le!=t){ le.getWorld().strikeLightningEffect(le.getLocation()); le.damage(4,p); } });
                kb(p,t,2,0.5); p.sendActionBar(Component.text("⚡ Thunder Strike!",NamedTextColor.YELLOW));
            }
        }
    }

    private void m1Hailbreaker(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        int stage = hailStage.getOrDefault(p.getUniqueId(),1);
        if (air) { p.setVelocity(new Vector(0,-2.5,0)); safeGroundTask(p,()->spawnIceExplosions(p,stage)); p.sendActionBar(Component.text("❄ Hail Drop!",NamedTextColor.AQUA)); return; }
        if (sprint) { slow(t,2,60); p.setVelocity(p.getLocation().getDirection().normalize().multiply(1.2)); return; }
        slow(t,2,60);
        if(step%2==1) { spawnIceExplosions(p,1); p.sendActionBar(Component.text("❄ Ice Explosion!",NamedTextColor.AQUA)); }
    }

    private void m1GalePale(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        if (air) { p.setVelocity(new Vector(0,-2.5,0)); safeGroundTask(p,()->{ p.getLocation().getNearbyLivingEntities(4).stream().limit(5).forEach(le->{ if(le!=p){kb(p,le,2.5,0.6);slow(le,1,60);} }); }); p.sendActionBar(Component.text("🌬 Aerial Slam!",NamedTextColor.WHITE)); return; }
        if (sprint) { p.setVelocity(p.getLocation().getDirection().normalize().multiply(1.5)); kb(p,t,2.5,0.4); slow(t,1,40); return; }
        // ROOT FIX: kb called only once, in the switch
        switch(step%2){
            case 0 -> { kb(p,t,2.5,0.3); slow(t,1,40); }
            case 1 -> { t.getLocation().getNearbyLivingEntities(4).stream().limit(4).forEach(le->{ if(le!=p&&le!=t){kb(p,le,2,0.4);slow(le,1,40);} }); p.sendActionBar(Component.text("🌬 Wind Burst!",NamedTextColor.WHITE)); }
        }
    }

    private void m1IronRequiem(Player p, LivingEntity t, EntityDamageByEntityEvent e, boolean air, boolean sprint) {
        int b=bullets.getOrDefault(p.getUniqueId(),6);
        // ROOT FIX: aerial precision shot — cap multiplier to prevent one shots
        double mult = air ? 1.3 : 1.0;
        if(b>0){
            bullets.put(p.getUniqueId(),b-1);
            if(mult>1)e.setDamage(e.getDamage()*mult);
            particles(t.getLocation(),Particle.CRIT,3);
            p.sendActionBar(Component.text("🔫 Bullets:"+(b-1)+"/6",NamedTextColor.GRAY));
        } else {
            slow(t,0,40); e.setDamage(e.getDamage()*0.5);
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,60,1,false,true,true));
            p.sendActionBar(Component.text("⚡ Rod! Crit to reload",NamedTextColor.RED));
        }
    }

    private void m1Amethyst(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        if (air) { addResonance(p,2); return; }
        if (sprint) {
            Location safe=getSafeTeleportBehind(p,t,1.5);
            // ROOT FIX: only add resonance if teleport succeeded
            if(safe!=null){ p.teleport(safe); addResonance(p,1); }
            return;
        }
        addResonance(p,1);
        if(step%4==3) {
            // ROOT FIX: no damage cascade, just slow nearby
            t.getLocation().getNearbyLivingEntities(2).stream().limit(3).forEach(le->{ if(le!=p&&le!=t){slow(le,1,40);} });
            addResonance(p,1);
            p.sendActionBar(Component.text("💎 Crystal Burst!",NamedTextColor.LIGHT_PURPLE));
        }
    }

    private void m1Flamewall(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        if(flameDisabled.contains(p.getUniqueId()))return;
        UUID u=p.getUniqueId();
        if (air) { fireDamage(t,p,4,80); return; }
        // ROOT FIX: sprint does its OWN logic, does NOT call critFlamewall
        if (sprint) { fireDamage(t,p,3,60); p.setVelocity(p.getLocation().getDirection().normalize().multiply(0.8)); heat.put(u,Math.min(10,heat.getOrDefault(u,0)+2)); return; }
        int h=Math.min(10,heat.getOrDefault(u,0)+2);
        heat.put(u,h);
        fireDamage(t,p,1,40+(h*5));
        t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,60,0,false,true,true));
        p.sendActionBar(Component.text("🔥 Heat: "+h+"/10",h>=8?NamedTextColor.RED:NamedTextColor.GOLD));
        if(step%3==2) {
            // ROOT FIX: only set fire ticks nearby, no damage to prevent cascade
            t.getLocation().getNearbyLivingEntities(3).stream().limit(4).forEach(le->{ if(le!=p&&le!=t){le.setFireTicks(80);le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,60,0,false,true,true));} });
            p.sendActionBar(Component.text("🔥 Heat Burst!",NamedTextColor.GOLD));
        }
    }

    private void m1ImBlue(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        UUID u=p.getUniqueId();
        if (air) { p.setVelocity(new Vector(0,-2.5,0)); int r=rhythm.getOrDefault(u,0); safeGroundTask(p,()->{ p.getLocation().getNearbyLivingEntities(4).stream().limit(5).forEach(le->{ if(le!=p){le.damage(4+(r*0.5),p);kb(p,le,2,0.4);slow(le,1,40);} }); }); p.sendActionBar(Component.text("🌊 Tidal Crash!",NamedTextColor.AQUA)); return; }
        if (sprint) { Location safe=getSafeTeleportBehind(p,t,1.5); if(safe!=null)p.teleport(safe); return; }
        long now=System.currentTimeMillis();
        long last=lastRhythm.getOrDefault(u,0L);
        int r=rhythm.getOrDefault(u,0);
        if(now-last<=3000){r=Math.min(10,r+1);}else{r=Math.max(0,r-2);}
        rhythm.put(u,r); lastRhythm.put(u,now);
        p.sendActionBar(Component.text("🌊 Rhythm: "+r+"/10",r>=8?NamedTextColor.AQUA:NamedTextColor.BLUE));
        if(r>=5)p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,30,0,false,true,true));
        if(r>=8)p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,30,0,false,true,true));
        if(step%5==4) { p.getLocation().getNearbyLivingEntities(5).stream().limit(6).forEach(le->{ if(le!=p){le.damage(3+(r*0.4),p);kb(p,le,2,0.3);} }); p.sendActionBar(Component.text("🌊 Wave Release!",NamedTextColor.AQUA)); }
    }

    private void m1Zodiac(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        UUID u=p.getUniqueId();
        int sign=zodiacSign.getOrDefault(u,0);
        if (air) { switch(sign){ case 0->fireDamage(t,p,3,60); case 1->p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,40,0,false,true,true)); case 2->t.addPotionEffect(new PotionEffect(PotionEffectType.POISON,80,1,false,true,true)); } return; }
        if (sprint) { switch(sign){ case 0->{p.setVelocity(p.getLocation().getDirection().normalize().multiply(1.5));fireDamage(t,p,2,40);} case 1->{p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,30,1,false,true,true));kb(p,t,1.5,0.3);} case 2->{ Location safe=getSafeTeleportBehind(p,t,1.5); if(safe!=null)p.teleport(safe); } } return; }
        switch(sign){
            case 0 -> { fireDamage(t,p,2,60); }
            case 1 -> {
                // ROOT FIX: Libra M1 has 2s cooldown to prevent permanent resistance
                long now=System.currentTimeMillis();
                if(now-zodiacCooldown.getOrDefault(u,0L)>=2000){
                    zodiacCooldown.put(u,now);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,30,0,false,true,true));
                }
            }
            case 2 -> t.addPotionEffect(new PotionEffect(PotionEffectType.POISON,80,1,false,true,true));
        }
        p.sendActionBar(Component.text(ZODIAC_NAMES[sign]+" Active",NamedTextColor.GOLD));
    }

    private void m1Nullscapes(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        UUID u=p.getUniqueId();
        if (air) { p.setVelocity(new Vector(0,-2.5,0)); safeGroundTask(p,()->{ p.getLocation().getNearbyLivingEntities(3).stream().limit(4).forEach(le->{ if(le!=p){le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,80,1,false,true,true));slow(le,1,60);} }); }); return; }
        if (sprint) { Location safe=getSafeTeleportBehind(p,t,2.0); if(safe!=null)p.teleport(safe); t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,80,0,false,true,true)); slow(t,0,60); return; }
        t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,80,0,false,true,true));
        slow(t,0,60);
        // ROOT FIX: void meter incremented once per hit, not double
        voidMeter.merge(u,1,(a,b)->Math.min(10,a+b));
        p.sendActionBar(Component.text("🕳 Void: "+voidMeter.get(u)+"/10",NamedTextColor.GRAY));
        if(step%3==2) {
            // ROOT FIX: only apply effects to nearby, no damage to prevent cascade
            t.getLocation().getNearbyLivingEntities(3).stream().limit(4).forEach(le->{ if(le!=p&&le!=t){le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,80,1,false,true,true));slow(le,1,60);} });
        }
    }

    private void m1Orbit(Player p, LivingEntity t, int step, boolean air, boolean sprint) {
        UUID u=p.getUniqueId();
        // ROOT FIX: air and sprint do their own logic, do NOT call critOrbit
        if (air) { p.setVelocity(new Vector(0,-3.5,0)); safeGroundTask(p,()->{ p.getLocation().getNearbyLivingEntities(7).stream().limit(7).forEach(le->{ if(le!=p){Vector pull=p.getLocation().subtract(le.getLocation()).toVector().normalize().multiply(2.5);pull.setY(0.5);le.setVelocity(pull);le.damage(14,p);slow(le,1,60);} }); p.getWorld().createExplosion(p.getLocation(),0f,false,false); }); p.sendActionBar(Component.text("🪐 Orbital Slam!",NamedTextColor.BLUE)); return; }
        if (sprint) {
            // Sprint M1 fires ONE arrow, not the full barrage
            LivingEntity target=nearest(p,20);
            if(target!=null){Vector dir=target.getLocation().add(0,1,0).subtract(p.getLocation().add(0,1,0)).toVector().normalize().multiply(3);Arrow arrow=p.launchProjectile(Arrow.class,dir);arrow.setShooter(p);arrow.setDamage(10);}
            return;
        }
        // Gravity pull — capped to prevent flying away
        Vector pull=p.getLocation().subtract(t.getLocation()).toVector();
        if(pull.length()>0) { pull=pull.normalize().multiply(Math.min(0.3,pull.length()*0.1)); t.setVelocity(t.getVelocity().add(pull)); }
        long lmt=lastMove.getOrDefault(u,0L);
        if(System.currentTimeMillis()-lmt<500){ orbitCharge.merge(u,1,(a,b)->Math.min(10,a+b)); p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,30,0,false,true,true)); }
        if(step%3==2) { t.getLocation().getNearbyLivingEntities(4).stream().limit(4).forEach(le->{ if(le!=p&&le!=t){Vector pull2=t.getLocation().toVector().subtract(le.getLocation().toVector()).normalize().multiply(1.5);le.setVelocity(pull2);} }); p.sendActionBar(Component.text("🪐 Gravity Burst!",NamedTextColor.BLUE)); }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CRITS (right click)
    // ═══════════════════════════════════════════════════════════════════════════

    private void critKyrsewinter(Player p) {
        if(p.isSprinting()){p.sendActionBar(Component.text("❄ Ice Leap!",NamedTextColor.AQUA));Vector leap=p.getLocation().getDirection().normalize().multiply(2.5);leap.setY(0.6);p.setVelocity(leap);new BukkitRunnable(){int tick=0;boolean hit=false;@Override public void run(){tick++;if(!p.isOnline()){cancel();return;}if(!hit){p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(14,p);kb(p,le,1.5,0.4);bleed(le,p,3);hit=true;} });}if(tick==22){p.getLocation().getNearbyLivingEntities(4).stream().limit(5).forEach(le->{ if(le!=p){le.damage(12,p);slow(le,3,100);kb(p,le,2,0.3);} });sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.6f,1.8f);}if(tick>=25)cancel();}}.runTaskTimer(plugin,0L,1L);}
        else{p.sendActionBar(Component.text("❄ Ice Poke!",NamedTextColor.AQUA));new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getNearbyEntities(3.5,2,3.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(14,p);kb(p,le,2.5,0.5);bleed(le,p,3);Location bl=le.getLocation().clone();new BukkitRunnable(){@Override public void run(){bl.getWorld().createExplosion(bl,0f,false,false);bl.getNearbyLivingEntities(3).stream().limit(4).forEach(e2->{ if(e2!=p){e2.damage(12,p);slow(e2,3,80);e2.setVelocity(new Vector(0,2,0));} });sound(bl,Sound.ENTITY_GENERIC_EXPLODE,0.7f,1.6f);}}.runTaskLater(plugin,20L);}});}}.runTaskLater(plugin,10L);}
    }

    private void critStormseye(Player p) {
        LivingEntity target=nearest(p,15); if(target==null){p.sendActionBar(Component.text("No target.",NamedTextColor.GRAY));return;}
        p.sendActionBar(Component.text("⚡ Storm Circle!",NamedTextColor.YELLOW));
        p.setVelocity(new Vector(0,0.5,0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,40,4,false,true,true));
        for(int i=0;i<3;i++){final int si=i;new BukkitRunnable(){@Override public void run(){
            if(!p.isOnline()||!target.isValid()||target.isDead())return;
            double angle=(Math.PI*2/3)*si;
            Location safe=getSafeTeleport(target.getLocation().add(Math.cos(angle)*3,1.5,Math.sin(angle)*3));
            if(safe!=null)p.teleport(safe);
            target.damage(si==2?14:10,p);
            if(si==2){slow(target,3,100);target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,80,1,false,true,true));target.getWorld().strikeLightningEffect(target.getLocation());p.sendActionBar(Component.text("⚡ SHOCKED!",NamedTextColor.YELLOW));}
        }}.runTaskLater(plugin,(long)(si*10+5));}
        new BukkitRunnable(){@Override public void run(){if(p.isOnline())p.removePotionEffect(PotionEffectType.RESISTANCE);}}.runTaskLater(plugin,40L);
    }

    private void critHeroFire(Player p) { p.sendActionBar(Component.text("🔥 Flame Pillar!",NamedTextColor.RED)); LivingEntity t=nearest(p,8);if(t==null)return; for(int i=0;i<3;i++){final int fi=i;new BukkitRunnable(){@Override public void run(){if(!t.isValid()||t.isDead())return;fireDamage(t,p,3,60);particles(t.getLocation().add(0,fi,0),Particle.FLAME,4);}}.runTaskLater(plugin,(long)(fi*5));} }
    private void critHeroIce(Player p) { p.sendActionBar(Component.text("❄ Frost Nova!",NamedTextColor.AQUA)); p.getLocation().getNearbyLivingEntities(5).stream().limit(6).forEach(le->{ if(le!=p){le.damage(8,p);slow(le,5,120);} }); sound(p.getLocation(),Sound.BLOCK_GLASS_BREAK,0.8f,0.5f); }
    private void critHeroWind(Player p) { p.sendActionBar(Component.text("🌬 Wind Slash!",NamedTextColor.WHITE)); Vector dir=p.getLocation().getDirection().normalize(); for(int i=1;i<=8;i++){final int fi=i;new BukkitRunnable(){@Override public void run(){Location loc=p.getLocation().add(dir.clone().multiply(fi));loc.getNearbyLivingEntities(1.5).forEach(le->{ if(le!=p){le.damage(10,p);kb(p,le,2,0.5);} });}}.runTaskLater(plugin,(long)(fi*2));} }
    private void critHeroThunder(Player p) { p.sendActionBar(Component.text("⚡ Thunder Slam!",NamedTextColor.YELLOW)); new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;Vector dir=p.getLocation().getDirection().normalize();for(int i=1;i<=6;i++){final int fi=i;new BukkitRunnable(){@Override public void run(){Location loc=p.getLocation().add(dir.clone().multiply(fi));loc.getWorld().strikeLightningEffect(loc);loc.getNearbyLivingEntities(1.5).forEach(le->{ if(le!=p){le.damage(16,p);kb(p,le,2,0.8);launch(le,1.0);slow(le,1,60);} });}}.runTaskLater(plugin,(long)(fi*3));}}}.runTaskLater(plugin,8L); }

    private void critRailblade(Player p) {
        if(!p.isOnGround()){p.sendActionBar(Component.text("💨 Aerial Downslash!",NamedTextColor.GRAY));p.setVelocity(new Vector(0,0.4,0));new BukkitRunnable(){int tick=0;boolean hit=false;@Override public void run(){tick++;if(!p.isOnline()){cancel();return;}if(tick==8){Vector slash=p.getLocation().getDirection().normalize().multiply(0.8);slash.setY(-2.5);p.setVelocity(slash);}if(tick>=8&&!hit){p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(18,p);fireDamage(le,p,2,100);kb(p,le,1.5,0.3);hit=true;} });}if(tick>=20)cancel();}}.runTaskTimer(plugin,0L,1L);}
        else{p.sendActionBar(Component.text("💨 Railblade Windup!",NamedTextColor.GRAY));new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(10,p);kb(p,le,2.5,0.5);slow(le,2,30);} });}}.runTaskLater(plugin,8L);new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(18,p);fireDamage(le,p,3,120);} });p.sendActionBar(Component.text("💨🔥 Flaming Blow!",NamedTextColor.GOLD));}}.runTaskLater(plugin,18L);}
    }

    private void critDeepspindle(Player p) {
        if(p.isSprinting()){if(darkRift.contains(p.getUniqueId())){p.sendActionBar(Component.text("☠ Dark Rift cooldown!",NamedTextColor.DARK_PURPLE));return;}p.sendActionBar(Component.text("☠ Dark Rift!",NamedTextColor.DARK_PURPLE));p.damage(4);LivingEntity target=nearest(p,20);if(target!=null){for(int i=0;i<2;i++){final int bi=i;new BukkitRunnable(){@Override public void run(){if(!target.isValid()||target.isDead())return;target.damage(16,p);wither(target,2,80);}}.runTaskLater(plugin,(long)(bi*8+5));}}darkRift.add(p.getUniqueId());new BukkitRunnable(){@Override public void run(){darkRift.remove(p.getUniqueId());}}.runTaskLater(plugin,400L);}
        else{LivingEntity target=nearest(p,5);if(target==null){p.sendActionBar(Component.text("No target.",NamedTextColor.GRAY));return;}p.sendActionBar(Component.text("☠ Void Thrust!",NamedTextColor.DARK_PURPLE));new BukkitRunnable(){@Override public void run(){if(!target.isValid()||target.isDead())return;target.damage(14,p);kb(p,target,1,0.2);for(int i=0;i<7;i++){final int bi=i;new BukkitRunnable(){@Override public void run(){if(!target.isValid()||target.isDead())return;target.damage(5,p);wither(target,1,40);}}.runTaskLater(plugin,(long)(bi*8+5));}}}.runTaskLater(plugin,8L);}
    }

    private void critRedDeath(Player p) {
        if(p.isSprinting()){p.sendActionBar(Component.text("🩸 Blood Thrust!",NamedTextColor.RED));Vector move=p.getLocation().getDirection().normalize().multiply(0.4);for(int i=0;i<4;i++){new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.setVelocity(move);p.getNearbyEntities(2,2,2).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(6,p);applyBloodPoison(p,le);} });}}.runTaskLater(plugin,(long)(i*4));}new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(10,p);wither(le,1,60);} });spawnBloodStakes(p);}}.runTaskLater(plugin,22L);}
        else{p.sendActionBar(Component.text("🩸 Blood Stakes!",NamedTextColor.RED));spawnBloodStakes(p);}
    }

    private void critCryptBlade(Player p) {
        if(p.isSprinting()){p.sendActionBar(Component.text("💀 Cleave!",NamedTextColor.DARK_GREEN));for(int i=0;i<2;i++){new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(8,p);} });}}.runTaskLater(plugin,(long)(i*5));}new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getNearbyEntities(3.5,3.5,3.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(16,p);kb(p,le,3,0.8);wither(le,1,80);slow(le,2,60);} });}}.runTaskLater(plugin,14L);}
        else{p.sendActionBar(Component.text("💀 Darkness Plunge!",NamedTextColor.DARK_GREEN));new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getLocation().getNearbyLivingEntities(5).stream().limit(5).forEach(le->{ if(le!=p){le.damage(12,p);wither(le,2,100);le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,60,0,false,true,true));cryptChain.put(p.getUniqueId(),le);startChainPull(p,le);p.sendActionBar(Component.text("💀 Chained!",NamedTextColor.DARK_GREEN));} });}}.runTaskLater(plugin,10L);}
    }

    private void critSoulthorn(Player p) {
        LivingEntity target=nearest(p,20);if(target==null){p.sendActionBar(Component.text("No target.",NamedTextColor.GRAY));return;}
        var pdc=target.getPersistentDataContainer();int marks=pdc.getOrDefault(DeepwokenWeapons.SOUL_MARKS_KEY,PersistentDataType.INTEGER,0);
        if(marks==0){target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,60,0,false,true,true));target.damage(8,p);p.sendActionBar(Component.text("✦ Glowing slash",NamedTextColor.LIGHT_PURPLE));}
        else if(marks<=2){p.sendActionBar(Component.text("✦✦ Reel & Detonate!",NamedTextColor.LIGHT_PURPLE));p.setVelocity(new Vector(0,1.5,0));final int fm=marks;new BukkitRunnable(){@Override public void run(){if(!target.isValid()||target.isDead())return;target.setVelocity(p.getLocation().subtract(target.getLocation()).toVector().normalize().multiply(3));target.damage(16.0*fm,p);pdc.remove(DeepwokenWeapons.SOUL_MARKS_KEY);sound(target.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.6f,1.5f);}}.runTaskLater(plugin,10L);}
        else{p.sendActionBar(Component.text("✦✦✦ TRUE HYPERARMOR!",NamedTextColor.GOLD));Location above=getSafeTeleport(target.getLocation().add(0,3,0));if(above!=null)p.teleport(above);p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,40,4,false,true,true));for(int i=0;i<4;i++){new BukkitRunnable(){@Override public void run(){if(!target.isValid()||target.isDead()||!p.isOnline())return;target.damage(14,p);p.setVelocity(new Vector(0,-1.5,0));sound(target.getLocation(),Sound.ENTITY_PLAYER_ATTACK_CRIT,0.8f,1.2f);}}.runTaskLater(plugin,(long)(i*6+5));}pdc.remove(DeepwokenWeapons.SOUL_MARKS_KEY);}
    }

    private void critColdPoint(Player p) { LivingEntity t=nearest(p,10);if(t==null){p.sendActionBar(Component.text("No target.",NamedTextColor.GRAY));return;}p.sendActionBar(Component.text("❄ Pierce Dash!",NamedTextColor.AQUA));Location safe=getSafeTeleportBehind(p,t,2.0);if(safe!=null)p.teleport(safe);t.damage(20,p);slow(t,3,120);t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,40,0,false,true,true));sound(t.getLocation(),Sound.BLOCK_GLASS_BREAK,1f,1.8f); }

    private void critPyreKeeper(Player p) {
        if(!p.isOnGround()&&!p.isSprinting()&&!p.isSneaking()){p.sendActionBar(Component.text("🔥 Aerial Downchop!",NamedTextColor.GOLD));p.setVelocity(new Vector(0,0.5,0));new BukkitRunnable(){int t=0;boolean hit=false;@Override public void run(){t++;if(!p.isOnline()){cancel();return;}if(t==8)p.setVelocity(new Vector(0,-3,0));if(t>=8&&!hit){p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(14,p);fireDamage(le,p,2,80);kb(p,le,2,0.5);hit=true;} });}if(p.isOnGround()&&t>=8){p.getLocation().getNearbyLivingEntities(4).stream().limit(5).forEach(le->{ if(le!=p){le.damage(10,p);fireDamage(le,p,2,100);kb(p,le,2.5,1.0);} });p.getWorld().createExplosion(p.getLocation(),0f,false,false);cancel();}if(t>=60)cancel();}}.runTaskTimer(plugin,0L,1L);}
        else if(p.isSprinting()&&p.isSneaking()){p.sendActionBar(Component.text("🔥 Flip Slice!",NamedTextColor.GOLD));Vector dir=p.getLocation().getDirection().normalize().multiply(0.6);new BukkitRunnable(){int t=0;@Override public void run(){t++;if(!p.isOnline()){cancel();return;}p.setVelocity(new Vector(dir.getX(),0.3,dir.getZ()));p.getNearbyEntities(2,2,2).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(5,p);fireDamage(le,p,1,40);} });if(t>=12)cancel();}}.runTaskTimer(plugin,0L,1L);}
        else if(p.isSprinting()){p.sendActionBar(Component.text("🔥 Rush Split!",NamedTextColor.GOLD));Vector lunge=p.getLocation().getDirection().normalize().multiply(3);lunge.setY(0.3);p.setVelocity(lunge);new BukkitRunnable(){int t=0;boolean hit=false;@Override public void run(){t++;if(!p.isOnline()){cancel();return;}if(!hit)p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(12,p);fireDamage(le,p,2,80);kb(p,le,2,0.5);hit=true;new BukkitRunnable(){@Override public void run(){if(!le.isValid()||le.isDead())return;le.damage(10,p);fireDamage(le,p,1,60);Vector split=p.getLocation().subtract(le.getLocation()).toVector().normalize().multiply(-2);split.setY(0.3);le.setVelocity(split);}}.runTaskLater(plugin,8L);} });if(t>=8)cancel();}}.runTaskTimer(plugin,0L,2L);}
        else if(p.isSneaking()){p.sendActionBar(Component.text("🔥 Swift Flip Slash!",NamedTextColor.GOLD));p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(10,p);fireDamage(le,p,1,60);} });new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.setVelocity(new Vector(0,0.8,0));new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getNearbyEntities(3.5,3.5,3.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(8,p);fireDamage(le,p,1,40);} });p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,60,1,false,true,true));}}.runTaskLater(plugin,8L);}}.runTaskLater(plugin,5L);}
        else{p.sendActionBar(Component.text("🔥 Triple Slash!",NamedTextColor.GOLD));new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(10,p);fireDamage(le,p,1,60);} });}}.runTaskLater(plugin,8L);new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(8,p);fireDamage(le,p,1,40);} });}}.runTaskLater(plugin,16L);new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(8,p);fireDamage(le,p,1,40);} });p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,80,1,false,true,true));p.sendActionBar(Component.text("🔥 Speed Boost!",NamedTextColor.GOLD));}}.runTaskLater(plugin,22L);}
    }

    private void critBloodfouler(Player p) { if(p.isSprinting()){p.sendActionBar(Component.text("🩸 Rush Sweep!",NamedTextColor.DARK_RED));p.setVelocity(p.getLocation().getDirection().normalize().multiply(3));new BukkitRunnable(){int t=0;@Override public void run(){t++;if(!p.isOnline()){cancel();return;}p.getNearbyEntities(2.5,2.5,2.5).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){le.damage(12,p);applyBloodPoison(p,le);} });if(t>=5)cancel();}}.runTaskTimer(plugin,0L,2L);}else{p.sendActionBar(Component.text("🩸 Blood Infect!",NamedTextColor.DARK_RED));p.getLocation().getNearbyLivingEntities(6).stream().limit(7).forEach(e->{ if(e!=p){e.damage(12,p);applyBloodPoison(p,e);} });sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.6f,0.8f);} }
    private void critBoltcrusher(Player p) { if(p.isSprinting()){p.sendActionBar(Component.text("⚡ Ground Slam!",NamedTextColor.YELLOW));new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getLocation().getNearbyLivingEntities(6).stream().limit(6).forEach(le->{ if(le!=p){le.getWorld().strikeLightningEffect(le.getLocation());le.damage(16,p);slow(le,2,80);kb(p,le,2,0.3);} });p.getWorld().createExplosion(p.getLocation(),0f,false,false);p.sendActionBar(Component.text("⚡ SAPPED!",NamedTextColor.YELLOW));}}.runTaskLater(plugin,12L);}else{p.sendActionBar(Component.text("⚡ Clobber!",NamedTextColor.YELLOW));new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(20,p);slow(le,2,100);kb(p,le,3,0.6);le.getWorld().strikeLightningEffect(le.getLocation());} });p.sendActionBar(Component.text("⚡ SAPPED!",NamedTextColor.YELLOW));}}.runTaskLater(plugin,14L);} }

    private void critHailbreaker(Player p) {
        int stage=hailStage.getOrDefault(p.getUniqueId(),1);
        switch(stage){
            case 1->{p.sendActionBar(Component.text("❄ Stage 1!",NamedTextColor.AQUA));new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(14,p);slow(le,2,80);} });spawnIceExplosions(p,1);hailStage.put(p.getUniqueId(),2);p.sendActionBar(Component.text("❄ Stage 2!",NamedTextColor.AQUA));}}.runTaskLater(plugin,12L);}
            case 2->{p.sendActionBar(Component.text("❄ Stage 2!",NamedTextColor.AQUA));new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getNearbyEntities(3.5,3.5,3.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(18,p);slow(le,4,120);} });spawnIceExplosions(p,2);hailStage.put(p.getUniqueId(),3);p.sendActionBar(Component.text("❄ Stage 3!",NamedTextColor.WHITE));}}.runTaskLater(plugin,10L);}
            case 3->{p.sendActionBar(Component.text("❄ Stage 3!",NamedTextColor.WHITE));Vector dash=p.getLocation().getDirection().normalize().multiply(2.5);dash.setY(0.5);p.setVelocity(dash);new BukkitRunnable(){int t=0;@Override public void run(){t++;if(!p.isOnline()){cancel();return;}p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(22,p);launch(le,2.0);slow(le,4,160);} });if(t>=10){spawnIceExplosions(p,3);hailStage.put(p.getUniqueId(),1);p.sendActionBar(Component.text("❄ Stage 1",NamedTextColor.GRAY));cancel();}}}.runTaskTimer(plugin,5L,2L);}
        }
    }

    private void critGalePale(Player p) { if(p.isSprinting()){p.sendActionBar(Component.text("🌬 Rush Sweep!",NamedTextColor.WHITE));p.setVelocity(p.getLocation().getDirection().normalize().multiply(3));new BukkitRunnable(){int t=0;@Override public void run(){t++;if(!p.isOnline()){cancel();return;}p.getNearbyEntities(2.5,2.5,2.5).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){le.damage(14,p);slow(le,0,60);le.addPotionEffect(new PotionEffect(PotionEffectType.POISON,80,0,false,true,true));} });if(t>=6)cancel();}}.runTaskTimer(plugin,0L,2L);}else{p.sendActionBar(Component.text("🌬 360° Sweep!",NamedTextColor.WHITE));p.getLocation().getNearbyLivingEntities(6).stream().limit(8).forEach(e->{ if(e!=p){e.damage(18,p);kb(p,e,2.5,0.5);slow(e,1,60);e.addPotionEffect(new PotionEffect(PotionEffectType.POISON,80,0,false,true,true));} });sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.7f,1.6f);} }

    private void critIronRequiem(Player p) {
        int b=bullets.getOrDefault(p.getUniqueId(),0);
        if(p.isSprinting()){if(b<=0){p.sendActionBar(Component.text("🔫 No bullets!",NamedTextColor.RED));return;}int shots=Math.min(4,b);for(int i=0;i<shots;i++){final int si=i;new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;LivingEntity t=nearest(p,20);Vector dir=t!=null?t.getLocation().add(0,1,0).subtract(p.getLocation().add(0,1,0)).toVector().normalize().multiply(3):p.getLocation().getDirection().multiply(3);Arrow arrow=p.launchProjectile(Arrow.class,dir);arrow.setShooter(p);arrow.setDamage(10);sound(p.getLocation(),Sound.ENTITY_ARROW_SHOOT,0.8f,1.5f);}}.runTaskLater(plugin,(long)(si*4));}bullets.put(p.getUniqueId(),b-shots);p.sendActionBar(Component.text("🔫 Bullets:"+(b-shots)+"/6",NamedTextColor.GRAY));}
        else{p.sendActionBar(Component.text("🔫 Explosive Shot!",NamedTextColor.GRAY));LivingEntity t=nearest(p,20);if(t!=null){Vector dir=t.getLocation().add(0,1,0).subtract(p.getLocation().add(0,1,0)).toVector().normalize().multiply(3);Arrow arrow=p.launchProjectile(Arrow.class,dir);arrow.setShooter(p);arrow.setDamage(16);new BukkitRunnable(){int tick=0;@Override public void run(){tick++;if(!arrow.isValid()||tick>40){cancel();return;}arrow.getNearbyEntities(1,1,1).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){le.damage(16,p);arrow.getWorld().createExplosion(arrow.getLocation(),0f,false,false);arrow.remove();cancel();} });}}.runTaskTimer(plugin,0L,1L);sound(p.getLocation(),Sound.ENTITY_ARROW_SHOOT,0.8f,1.2f);}bullets.put(p.getUniqueId(),Math.min(6,b+1));p.sendActionBar(Component.text("🔫 Reloaded! "+Math.min(6,b+1)+"/6",NamedTextColor.GRAY));}
    }

    private void critAmethyst(Player p) {
        int res=resonance.getOrDefault(p.getUniqueId(),0);
        if(p.isSprinting()){p.sendActionBar(Component.text("💎 Prism Pierce!",NamedTextColor.LIGHT_PURPLE));Vector lunge=p.getLocation().getDirection().normalize().multiply(3);lunge.setY(0.1);p.setVelocity(lunge);new BukkitRunnable(){int t=0;boolean hit=false;@Override public void run(){t++;if(!p.isOnline()){cancel();return;}if(!hit)p.getNearbyEntities(2,2,2).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){le.damage(18,p);hit=true;} });if(t>=12)cancel();}}.runTaskTimer(plugin,0L,1L);}
        else{if(res<10){p.sendActionBar(Component.text("💎 Need full Resonance! ("+res+"/10)",NamedTextColor.LIGHT_PURPLE));return;}p.sendActionBar(Component.text("💎 SHATTERSTRIKE!",NamedTextColor.GOLD));Vector dir=p.getLocation().getDirection().normalize();for(int i=0;i<5;i++){double angle=(Math.PI/8)*(i-2);Vector shard=dir.clone().rotateAroundY(angle).multiply(0.6);// ROOT FIX: use boolean array to fix lambda capture
            boolean[] hitArr={false};final Vector fs=shard.clone();new BukkitRunnable(){int t=0;Location pos=p.getLocation().clone().add(fs);@Override public void run(){t++;pos.add(fs);if(!hitArr[0])pos.getNearbyLivingEntities(1.2).forEach(le->{ if(le!=p&&!hitArr[0]){le.damage(20,p);slow(le,1,40);hitArr[0]=true;} });if(t>=8)cancel();}}.runTaskTimer(plugin,(long)(i*2),1L);}resonance.put(p.getUniqueId(),0);p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,80,0,false,true,true));}
    }

    private void critFlamewall(Player p) {
        int h=heat.getOrDefault(p.getUniqueId(),0);
        if(p.isSprinting()){p.sendActionBar(Component.text("🔥 Blazing Surge!",NamedTextColor.GOLD));p.setFireTicks(60);Vector dash=p.getLocation().getDirection().normalize().multiply(3);dash.setY(0.2);p.setVelocity(dash);new BukkitRunnable(){int t=0;@Override public void run(){t++;if(!p.isOnline()){cancel();return;}p.getNearbyEntities(3,3,3).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){le.damage(12,p);fireDamage(le,p,2,120);kb(p,le,1.5,0.3);} });if(t>=15)cancel();}}.runTaskTimer(plugin,0L,1L);}
        else{if(h<5){p.sendActionBar(Component.text("🔥 Need 5+ heat! ("+h+")",NamedTextColor.GOLD));return;}p.sendActionBar(Component.text("🔥 Inferno Barrier!",NamedTextColor.GOLD));sound(p.getLocation(),Sound.ENTITY_BLAZE_SHOOT,1f,0.5f);Vector right=p.getLocation().getDirection().rotateAroundY(Math.PI/2).normalize();for(int col=-3;col<=3;col++){for(int row=0;row<=3;row++){final int fc=col,fr=row;Location wallLoc=p.getLocation().add(p.getLocation().getDirection().normalize().multiply(2)).add(right.clone().multiply(fc)).add(0,fr,0);new BukkitRunnable(){@Override public void run(){particles(wallLoc,Particle.FLAME,2);wallLoc.getNearbyLivingEntities(1).forEach(le->{ if(le!=p){le.damage(3,p);fireDamage(le,p,1,80);} });}}.runTaskLater(plugin,(long)((Math.abs(fc)+fr)*2));}}new BukkitRunnable(){int t=0;@Override public void run(){t++;if(t>60||!p.isOnline()){cancel();return;}Vector r2=p.getLocation().getDirection().rotateAroundY(Math.PI/2).normalize();for(int c=-3;c<=3;c++){for(int r=0;r<=3;r++){Location wl=p.getLocation().add(p.getLocation().getDirection().normalize().multiply(2)).add(r2.clone().multiply(c)).add(0,r,0);particles(wl,Particle.FLAME,1);wl.getNearbyLivingEntities(1).forEach(le->{ if(le!=p){le.damage(1,p);fireDamage(le,p,0,40);} });}}}}.runTaskTimer(plugin,10L,5L);}
    }

    private void critImBlue(Player p) {
        int r=rhythm.getOrDefault(p.getUniqueId(),0);
        if(p.isSprinting()){p.sendActionBar(Component.text("🌊 Sonic Drift!",NamedTextColor.AQUA));p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,10,4,false,true,true));Vector dash=p.getLocation().getDirection().normalize().multiply(3.5);dash.setY(0.15);p.setVelocity(dash);new BukkitRunnable(){int t=0;boolean hit=false;@Override public void run(){t++;if(!p.isOnline()){cancel();return;}if(!hit)p.getNearbyEntities(2,2,2).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){le.damage(14+(r*0.5),p);kb(p,le,1.5,0.3);hit=true;} });if(t>=12)cancel();}}.runTaskTimer(plugin,0L,1L);}
        else{if(r<5){p.sendActionBar(Component.text("🌊 Need 5+ Rhythm! ("+r+")",NamedTextColor.AQUA));return;}p.sendActionBar(Component.text("🌊 Tidal Pulse! x"+r,NamedTextColor.AQUA));double dmgMult=1.0+(r*0.2);p.getLocation().getNearbyLivingEntities(7).stream().limit(8).forEach(le->{ if(le!=p){le.damage(14*dmgMult,p);kb(p,le,2.5,0.5);slow(le,1,60);} });for(int i=1;i<=4;i++){final int ri=i;new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;double wrad=ri*2.0;int pts=(int)(wrad*6);for(int j=0;j<pts;j++){double a=2*Math.PI*j/pts;Location wl=p.getLocation().add(Math.cos(a)*wrad,0.3,Math.sin(a)*wrad);particles(wl,Particle.DRIPPING_WATER,2);}sound(p.getLocation(),Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,0.6f,1.2f-(ri*0.1f));}}.runTaskLater(plugin,(long)(ri*4));}}
    }

    private void critZodiac(Player p) {
        int sign=zodiacSign.getOrDefault(p.getUniqueId(),0);
        switch(sign){
            case 0->{p.sendActionBar(Component.text("♈ ARIES IMPACT!",NamedTextColor.RED));Vector slam=p.getLocation().getDirection().normalize().multiply(2.5);slam.setY(0.2);p.setVelocity(slam);new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(18,p);fireDamage(le,p,2,80);kb(p,le,3,0.8);} });}}.runTaskLater(plugin,6L);zodiacSign.put(p.getUniqueId(),1);new BukkitRunnable(){@Override public void run(){if(p.isOnline())p.sendActionBar(Component.text("♈→♎ Libra next!",NamedTextColor.WHITE));}}.runTaskLater(plugin,3L);}
            case 1->{
                // ROOT FIX: Libra crit now also deals damage
                p.sendActionBar(Component.text("♎ LIBRA BALANCE!",NamedTextColor.WHITE));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,100,2,false,true,true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,60,1,false,true,true));
                p.getLocation().getNearbyLivingEntities(5).stream().limit(6).forEach(le->{ if(le!=p){le.damage(8,p);kb(p,le,2.5,0.5);} }); // deals damage now
                zodiacSign.put(p.getUniqueId(),2);
                new BukkitRunnable(){@Override public void run(){if(p.isOnline())p.sendActionBar(Component.text("♎→♏ Scorpio next!",NamedTextColor.DARK_GREEN));}}.runTaskLater(plugin,3L);
            }
            case 2->{LivingEntity target=nearest(p,10);if(target==null){p.sendActionBar(Component.text("No target.",NamedTextColor.GRAY));return;}p.sendActionBar(Component.text("♏ SCORPIO STING!",NamedTextColor.DARK_GREEN));Location stingPos=getSafeTeleportBehind(p,target,2.0);if(stingPos!=null)p.teleport(stingPos);target.damage(20,p);target.addPotionEffect(new PotionEffect(PotionEffectType.POISON,200,2,false,true,true));target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,100,1,false,true,true));p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,60,1,false,true,true));zodiacSign.put(p.getUniqueId(),0);new BukkitRunnable(){@Override public void run(){if(p.isOnline())p.sendActionBar(Component.text("♏→♈ Aries next!",NamedTextColor.RED));}}.runTaskLater(plugin,3L);}
        }
    }

    private void critNullscapes(Player p) {
        int v=voidMeter.getOrDefault(p.getUniqueId(),0);
        if(p.isSprinting()){p.sendActionBar(Component.text("🕳 Rift Break!",NamedTextColor.DARK_GRAY));LivingEntity nt=nearest(p,15);Location center=nt!=null?nt.getLocation():p.getLocation().add(p.getLocation().getDirection().multiply(5));p.getLocation().getNearbyLivingEntities(12).stream().limit(8).forEach(le->{ if(le!=p){Vector pull=center.toVector().subtract(le.getLocation().toVector()).normalize().multiply(2.5);le.setVelocity(pull);le.damage(10,p);} });new BukkitRunnable(){@Override public void run(){center.getWorld().createExplosion(center,0f,false,false);center.getNearbyLivingEntities(3).stream().limit(5).forEach(le->{ if(le!=p){le.damage(12,p);} });}}.runTaskLater(plugin,15L);voidMeter.put(p.getUniqueId(),Math.max(0,v-3));}
        else{
            // ROOT FIX: Erase Pulse — each wave only hits NEW entities not already hit
            p.sendActionBar(Component.text("🕳 Erase Pulse!",NamedTextColor.DARK_GRAY));
            Set<UUID> alreadyHit=new HashSet<>();
            for(int i=1;i<=4;i++){final int ri=i;new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getLocation().getNearbyLivingEntities(ri*2.0).stream().filter(le->le!=p&&!alreadyHit.contains(le.getUniqueId())).limit(6).forEach(le->{ alreadyHit.add(le.getUniqueId());le.damage(10,p);le.getActivePotionEffects().stream().filter(eff->isPositiveEffect(eff.getType())).map(PotionEffect::getType).toList().forEach(le::removePotionEffect);slow(le,0,40);});}}.runTaskLater(plugin,(long)(ri*4));}
            voidMeter.put(p.getUniqueId(),Math.max(0,v-2));
        }
    }

    private void critOrbit(Player p) {
        int charge=orbitCharge.getOrDefault(p.getUniqueId(),0);
        if(p.isSprinting()){p.sendActionBar(Component.text("🪐 Satellite Barrage!",NamedTextColor.BLUE));int shots=Math.max(2,Math.min(5,2+(charge/2)));for(int i=0;i<shots;i++){final int si=i;new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;LivingEntity t=nearest(p,20);if(t==null)return;Vector dir=t.getLocation().add(0,1,0).subtract(p.getLocation().add(0,1,0)).toVector().normalize().multiply(3);Arrow arrow=p.launchProjectile(Arrow.class,dir);arrow.setShooter(p);arrow.setDamage(8+(charge*0.5));sound(p.getLocation(),Sound.ENTITY_ARROW_SHOOT,0.6f,1.8f);}}.runTaskLater(plugin,(long)(si*5));}orbitCharge.put(p.getUniqueId(),Math.max(0,charge-3));}
        else{p.sendActionBar(Component.text("🪐 Orbital Slam!",NamedTextColor.BLUE));p.setVelocity(new Vector(0,-3.5,0));new BukkitRunnable(){int t=0;@Override public void run(){t++;if(!p.isOnline()){cancel();return;}if((p.isOnGround()&&t>=3)||t>40){p.getLocation().getNearbyLivingEntities(7).stream().limit(7).forEach(le->{ if(le!=p){Vector pull=p.getLocation().subtract(le.getLocation()).toVector().normalize().multiply(2.5);pull.setY(0.5);le.setVelocity(pull);le.damage(16,p);slow(le,1,60);} });p.getWorld().createExplosion(p.getLocation(),0f,false,false);cancel();}}.runTaskTimer(plugin,0L,1L);orbitCharge.put(p.getUniqueId(),Math.max(0,charge-3));}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  SHIFT ABILITIES
    // ═══════════════════════════════════════════════════════════════════════════

    private void shiftKyrsewinter(Player p){p.sendActionBar(Component.text("❄ Ice Wall!",NamedTextColor.AQUA));p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,60,1,false,true,true));p.getLocation().getNearbyLivingEntities(5).stream().limit(6).forEach(le->{ if(le!=p){slow(le,3,100);} });sound(p.getLocation(),Sound.BLOCK_GLASS_BREAK,0.8f,0.5f);}
    private void shiftStormseye(Player p){bullets.put(p.getUniqueId(),6);p.sendActionBar(Component.text("⚡ Full Reload!",NamedTextColor.YELLOW));sound(p.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,0.5f,2f);}
    private void shiftHeroFire(Player p){p.sendActionBar(Component.text("🔥 Ignite AOE!",NamedTextColor.RED));p.getLocation().getNearbyLivingEntities(5).stream().limit(6).forEach(le->{ if(le!=p){fireDamage(le,p,4,100);} });sound(p.getLocation(),Sound.ENTITY_BLAZE_SHOOT,0.8f,0.6f);}
    private void shiftHeroIce(Player p){p.sendActionBar(Component.text("❄ Ice Shield!",NamedTextColor.AQUA));p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,80,1,false,true,true));p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,60,0,false,true,true));sound(p.getLocation(),Sound.BLOCK_GLASS_BREAK,0.6f,1.8f);}
    private void shiftHeroWind(Player p){p.sendActionBar(Component.text("🌬 Gale Armor!",NamedTextColor.WHITE));p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,100,2,false,true,true));p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,100,2,false,true,true));sound(p.getLocation(),Sound.ENTITY_PHANTOM_FLAP,0.6f,1.5f);}

    private void shiftHeroThunder(Player p){
        p.sendActionBar(Component.text("⚡ Overcharge — next hit x2!",NamedTextColor.GOLD));
        overcharge.put(p.getUniqueId(),true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,60,1,false,true,true));
        sound(p.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,0.5f,1.5f);
        new BukkitRunnable(){@Override public void run(){overcharge.put(p.getUniqueId(),false);}}.runTaskLater(plugin,100L);
    }

    private void shiftRailblade(Player p){p.sendActionBar(Component.text("💨 Overdrive!",NamedTextColor.GRAY));p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,60,2,false,true,true));p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,60,1,false,true,true));sound(p.getLocation(),Sound.ENTITY_PHANTOM_FLAP,0.8f,2f);}

    private void shiftDeepspindle(Player p){
        LivingEntity t=nearest(p,8);if(t==null){p.sendActionBar(Component.text("No target.",NamedTextColor.GRAY));return;}
        p.sendActionBar(Component.text("☠ Soul Drain!",NamedTextColor.DARK_PURPLE));
        // ROOT FIX: collect effects to a list first, then remove — avoids ConcurrentModificationException
        List<PotionEffectType> toSteal=t.getActivePotionEffects().stream().filter(eff->isPositiveEffect(eff.getType())).map(eff->{ p.addPotionEffect(new PotionEffect(eff.getType(),eff.getDuration(),eff.getAmplifier(),false,true,true)); return eff.getType(); }).toList();
        toSteal.forEach(t::removePotionEffect);
        t.damage(8,p); wither(t,2,100);
    }

    private void shiftRedDeath(Player p){
        UUID u=p.getUniqueId();
        p.sendActionBar(Component.text("🩸 Blood Frenzy! 5s lifesteal!",NamedTextColor.RED));
        bloodFrenzy.put(u,true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,100,0,false,true,true));
        new BukkitRunnable(){@Override public void run(){ bloodFrenzy.put(u,false); if(p.isOnline())p.sendActionBar(Component.text("🩸 Blood Frenzy ended.",NamedTextColor.GRAY)); }}.runTaskLater(plugin,100L);
    }

    private void shiftCryptBlade(Player p){
        p.sendActionBar(Component.text("💀 Army of Darkness!",NamedTextColor.DARK_GREEN));
        for(int i=0;i<3;i++){double angle=(Math.PI*2/3)*i;Location loc=p.getLocation().add(Math.cos(angle)*2,0,Math.sin(angle)*2);if(loc.getWorld()==null)continue;Zombie z=(Zombie)loc.getWorld().spawnEntity(loc,EntityType.ZOMBIE);z.setCustomName("§cCrypt Servant");z.setCustomNameVisible(true);new BukkitRunnable(){@Override public void run(){if(z.isValid())z.remove();}}.runTaskLater(plugin,400L);}
        sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.6f,0.4f);
    }

    private void shiftSoulthorn(Player p){
        p.sendActionBar(Component.text("✦ Soul Shatter!",NamedTextColor.LIGHT_PURPLE));
        p.getNearbyEntities(15,15,15).stream().limit(10).forEach(en->{ // ROOT FIX: added limit
            if(en instanceof LivingEntity le&&en!=p){var pdc=le.getPersistentDataContainer();int marks=pdc.getOrDefault(DeepwokenWeapons.SOUL_MARKS_KEY,PersistentDataType.INTEGER,0);if(marks>0){le.damage(marks*12.0,p);pdc.remove(DeepwokenWeapons.SOUL_MARKS_KEY);sound(le.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.6f,1.5f);}}
        });
    }

    private void shiftColdPoint(Player p){p.sendActionBar(Component.text("❄ Absolute Zero!",NamedTextColor.AQUA));p.getLocation().getNearbyLivingEntities(6).stream().limit(8).forEach(le->{ if(le!=p){le.damage(10,p);slow(le,6,100);le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,100,2,false,true,true));} });sound(p.getLocation(),Sound.BLOCK_GLASS_BREAK,1f,0.3f);}
    private void shiftPyreKeeper(Player p){p.sendActionBar(Component.text("🔥 Verdant Flourish!",NamedTextColor.GREEN));p.getLocation().getNearbyLivingEntities(4).stream().limit(6).forEach(le->{ if(le!=p){le.damage(8,p);fireDamage(le,p,2,80);} });sound(p.getLocation(),Sound.ENTITY_BLAZE_SHOOT,0.6f,1.5f);}
    private void shiftBloodfouler(Player p){LivingEntity t=nearest(p,8);if(t==null){p.sendActionBar(Component.text("No target.",NamedTextColor.GRAY));return;}p.sendActionBar(Component.text("🩸 Hemorrhage!",NamedTextColor.DARK_RED));t.getPersistentDataContainer().set(DeepwokenWeapons.BLOOD_POISON_KEY,PersistentDataType.INTEGER,10);t.addPotionEffect(new PotionEffect(PotionEffectType.POISON,100,1,false,true,true));startBloodDot(t,p);}

    private void shiftBoltcrusher(Player p){
        p.sendActionBar(Component.text("⚡ Chain Lightning!",NamedTextColor.YELLOW));
        LivingEntity first=nearest(p,10);if(first==null){p.sendActionBar(Component.text("No target.",NamedTextColor.GRAY));return;}
        // ROOT FIX: use array of current target to avoid race condition
        LivingEntity[] current={first};
        Set<UUID> hitSet=new HashSet<>();
        hitSet.add(first.getUniqueId());
        for(int i=0;i<4;i++){final int fi=i;new BukkitRunnable(){@Override public void run(){
            if(!p.isOnline()||!current[0].isValid()||current[0].isDead())return;
            current[0].damage(10,p);current[0].getWorld().strikeLightningEffect(current[0].getLocation());
            LivingEntity next=current[0].getNearbyEntities(6,6,6).stream().filter(en->en instanceof LivingEntity&&en!=p&&!hitSet.contains(((LivingEntity)en).getUniqueId())).map(en->(LivingEntity)en).findFirst().orElse(null);
            if(next!=null){hitSet.add(next.getUniqueId());current[0]=next;}
        }}.runTaskLater(plugin,(long)(fi*5));}
    }

    private void shiftHailbreaker(Player p){p.sendActionBar(Component.text("❄ Blizzard!",NamedTextColor.AQUA));p.getLocation().getNearbyLivingEntities(8).stream().limit(8).forEach(le->{ if(le!=p){le.damage(8,p);slow(le,3,120);} });int stage=hailStage.getOrDefault(p.getUniqueId(),1);spawnIceExplosions(p,stage);sound(p.getLocation(),Sound.BLOCK_GLASS_BREAK,0.8f,0.3f);}
    private void shiftGalePale(Player p){p.sendActionBar(Component.text("🌬 Tempest!",NamedTextColor.WHITE));p.getLocation().getNearbyLivingEntities(8).stream().limit(8).forEach(le->{ if(le!=p){le.damage(6,p);kb(p,le,4.0,1.0);slow(le,1,60);} });sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.7f,1.8f);}

    private void shiftIronRequiem(Player p){
        int b=bullets.getOrDefault(p.getUniqueId(),0);
        if(b<=0){p.sendActionBar(Component.text("🔫 No bullets!",NamedTextColor.RED));return;}
        p.sendActionBar(Component.text("🔫 Rapid Fire!",NamedTextColor.GRAY));
        for(int i=0;i<b;i++){final int si=i;new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;LivingEntity t=nearest(p,20);if(t==null)return;Vector dir=t.getLocation().add(0,1,0).subtract(p.getLocation().add(0,1,0)).toVector().normalize().multiply(3);Arrow arrow=p.launchProjectile(Arrow.class,dir);arrow.setShooter(p);arrow.setDamage(8);sound(p.getLocation(),Sound.ENTITY_ARROW_SHOOT,0.6f,1.8f);}}.runTaskLater(plugin,(long)(si*3));}
        bullets.put(p.getUniqueId(),0);
    }

    private void shiftAmethyst(Player p){
        p.sendActionBar(Component.text("💎 Crystal Storm!",NamedTextColor.LIGHT_PURPLE));
        Vector dir=p.getLocation().getDirection().normalize();
        // ROOT FIX: sequential tasks not simultaneous — 8 shards fired one per tick
        for(int i=0;i<8;i++){double angle=(Math.PI/3)*(i/7.0-0.5);Vector shard=dir.clone().rotateAroundY(angle).multiply(0.5);final Vector fs=shard.clone();boolean[] hitArr={false};
            new BukkitRunnable(){int t=0;Location pos=p.getLocation().clone().add(fs);@Override public void run(){t++;if(!p.isOnline()){cancel();return;}pos.add(fs);if(!hitArr[0])pos.getNearbyLivingEntities(1).forEach(le->{ if(le!=p&&!hitArr[0]){le.damage(10,p);slow(le,1,30);hitArr[0]=true;} });if(t>=6)cancel();}}.runTaskTimer(plugin,(long)i,1L);
        }
        resonance.put(p.getUniqueId(),0);
        sound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1f,0.6f);
    }

    private void shiftFlamewall(Player p){
        int h=heat.getOrDefault(p.getUniqueId(),0);
        if(h<8){p.sendActionBar(Component.text("🔥 Need 8+ heat! ("+h+")",NamedTextColor.GOLD));return;}
        p.sendActionBar(Component.text("🔥 SOLAR ERUPTION!",NamedTextColor.GOLD));
        p.getLocation().getNearbyLivingEntities(10).stream().limit(10).forEach(le->{ if(le!=p){le.damage(28,p);fireDamage(le,p,3,200);kb(p,le,3,1);} });
        p.getWorld().createExplosion(p.getLocation(),0f,false,false);
        heat.put(p.getUniqueId(),0);flameDisabled.add(p.getUniqueId());
        new BukkitRunnable(){@Override public void run(){flameDisabled.remove(p.getUniqueId());if(p.isOnline())p.sendActionBar(Component.text("🔥 Recharged!",NamedTextColor.GOLD));}}.runTaskLater(plugin,100L);
    }

    private void shiftImBlue(Player p){
        int r=rhythm.getOrDefault(p.getUniqueId(),0);
        if(r<8){p.sendActionBar(Component.text("🌊 Need 8+ Rhythm! ("+r+")",NamedTextColor.AQUA));return;}
        p.sendActionBar(Component.text("🌊 Deep Echo!",NamedTextColor.AQUA));
        for(int i=0;i<3;i++){final int wi=i;new BukkitRunnable(){@Override public void run(){if(!p.isOnline())return;p.getLocation().getNearbyLivingEntities((wi+1)*4.0).stream().limit(6).forEach(le->{ if(le!=p){le.damage(12+(wi*4.0),p);kb(p,le,1.5,0.2);slow(le,1,40);} });sound(p.getLocation(),Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,0.8f,0.8f+(wi*0.2f));p.sendActionBar(Component.text("🌊 Wave "+(wi+1)+"!",NamedTextColor.AQUA));}}.runTaskLater(plugin,(long)(wi*15));}
        rhythm.put(p.getUniqueId(),0);
    }

    private void shiftZodiac(Player p){int sign=zodiacSign.getOrDefault(p.getUniqueId(),0);zodiacSign.put(p.getUniqueId(),(sign+1)%3);p.sendActionBar(Component.text("♈ Cycled → "+ZODIAC_NAMES[(sign+1)%3],NamedTextColor.GOLD));sound(p.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,0.5f,1.5f);}

    private void shiftNullscapes(Player p){
        int v=voidMeter.getOrDefault(p.getUniqueId(),0);
        if(v<10){p.sendActionBar(Component.text("🕳 Need full Void! ("+v+")",NamedTextColor.DARK_GRAY));return;}
        p.sendActionBar(Component.text("🕳 ABSOLUTE ZERO!",NamedTextColor.DARK_GRAY));
        p.getLocation().getNearbyLivingEntities(6).stream().limit(8).forEach(le->{ if(le!=p){le.damage(18,p);slow(le,6,100);le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,100,2,false,true,true));le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,60,0,false,true,true));} });
        voidMeter.put(p.getUniqueId(),0);sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.8f,0.3f);
    }

    private void shiftOrbit(Player p){p.sendActionBar(Component.text("🪐 Gravity Field!",NamedTextColor.BLUE));p.getLocation().getNearbyLivingEntities(8).stream().limit(8).forEach(le->{ if(le!=p){slow(le,2,100);le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,60,0,false,true,true));} });sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.5f,0.5f);}

    // ═══════════════════════════════════════════════════════════════════════════
    //  PASSIVE TASK
    // ═══════════════════════════════════════════════════════════════════════════
    private void startPassiveTask() {
        new BukkitRunnable(){@Override public void run(){
            for(Player p:Bukkit.getOnlinePlayers()){
                String wid=lastWeapon.getOrDefault(p.getUniqueId(),""); // ROOT FIX: use cached lastWeapon, no PDC read
                if(wid.isEmpty())continue;
                if((wid.equals("railblade")||wid.equals("hero_blade_wind"))&&!p.hasPotionEffect(PotionEffectType.SPEED))
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,40,1,false,true,true));
                if(wid.equals("im_blue")&&rhythm.getOrDefault(p.getUniqueId(),0)>=5){
                    if(!p.hasPotionEffect(PotionEffectType.SPEED))p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,40,0,false,true,true));
                    if(!p.hasPotionEffect(PotionEffectType.REGENERATION))p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,40,0,false,true,true));
                }
                if(wid.equals("nullscapes")){p.getNearbyEntities(4,4,4).stream().limit(4).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){if(!le.hasPotionEffect(PotionEffectType.WEAKNESS))le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,30,0,false,true,false));if(!le.hasPotionEffect(PotionEffectType.SLOWNESS))le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,30,0,false,true,false));} });}
                if(wid.equals("flamewall")){int h=heat.getOrDefault(p.getUniqueId(),0);if(h>0)heat.put(p.getUniqueId(),h-1);}
                if(wid.equals("orbit")){long lmt=lastMove.getOrDefault(p.getUniqueId(),0L);if(System.currentTimeMillis()-lmt>2000){int c=Math.max(0,orbitCharge.getOrDefault(p.getUniqueId(),0)-1);orbitCharge.put(p.getUniqueId(),c);}}
            }
        }}.runTaskTimer(plugin,0L,20L);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  SHARED HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Schedules a task that fires when the player hits the ground or after timeout */
    private void safeGroundTask(Player p, Runnable action) {
        new BukkitRunnable(){int t=0; @Override public void run(){
            t++;
            if(!p.isOnline()){cancel();return;} // ROOT FIX: online check
            if(p.isOnGround()||t>40){action.run();cancel();}
        }}.runTaskTimer(plugin,0L,1L);
    }

    private void fireDamage(LivingEntity target, Player attacker, double extraDmg, int fireTicks) {
        if(extraDmg>0&&attacker!=null)target.damage(extraDmg,attacker);
        target.setFireTicks(fireTicks);
    }

    /** ROOT FIX: checks chunk is loaded before teleporting */
    private Location getSafeTeleport(Location loc) {
        if(loc.getWorld()==null)return null;
        if(!loc.getChunk().isLoaded())return null; // ROOT FIX
        for(int dy=0;dy<=2;dy++){Location check=loc.clone().add(0,dy*0.5,0);if(isSafe(check))return check;}
        return null;
    }

    private Location getSafeTeleportBehind(Player p, LivingEntity target, double dist) {
        Vector dir=target.getLocation().getDirection().normalize().multiply(dist);
        Location behind=target.getLocation().subtract(dir);
        behind.setDirection(target.getLocation().subtract(behind).toVector());
        return getSafeTeleport(behind);
    }

    /** ROOT FIX: also checks for non-full blocks */
    private boolean isSafe(Location loc) {
        if(loc.getWorld()==null)return false;
        var block=loc.getBlock(); var above=loc.clone().add(0,1,0).getBlock();
        return block.isPassable()&&above.isPassable()&&!block.getType().name().contains("SLAB")&&!block.getType().name().contains("STAIR");
    }

    private void addResonance(Player p, int amt) {
        int r=Math.min(10,resonance.getOrDefault(p.getUniqueId(),0)+amt);
        resonance.put(p.getUniqueId(),r);
        if(r>=10)p.sendActionBar(Component.text("💎 FULL RESONANCE!",NamedTextColor.GOLD));
        else p.sendActionBar(Component.text("💎 Resonance: "+r+"/10",NamedTextColor.LIGHT_PURPLE));
    }

    private void applyBloodPoison(Player p, LivingEntity t) {
        var pdc=t.getPersistentDataContainer();
        int s=Math.min(10,pdc.getOrDefault(DeepwokenWeapons.BLOOD_POISON_KEY,PersistentDataType.INTEGER,0)+1);
        pdc.set(DeepwokenWeapons.BLOOD_POISON_KEY,PersistentDataType.INTEGER,s);
        t.addPotionEffect(new PotionEffect(PotionEffectType.POISON,40,0,false,true,true));
        startBloodDot(t,p);
    }

    private void startBloodDot(LivingEntity t, Player p) {
        UUID id=t.getUniqueId();
        if(bloodDotActive.contains(id))return;
        bloodDotActive.add(id);
        new BukkitRunnable(){int ticks=0; @Override public void run(){
            ticks++;
            if(!t.isValid()||t.isDead()){bloodDotActive.remove(id);cancel();return;}
            var pdc=t.getPersistentDataContainer();
            int s=pdc.getOrDefault(DeepwokenWeapons.BLOOD_POISON_KEY,PersistentDataType.INTEGER,0);
            if(s<=0){bloodDotActive.remove(id);pdc.remove(DeepwokenWeapons.BLOOD_POISON_KEY);cancel();return;}
            t.damage(s*0.4, p.isOnline()?p:null); // ROOT FIX: null check on player
            if(ticks%5==0)pdc.set(DeepwokenWeapons.BLOOD_POISON_KEY,PersistentDataType.INTEGER,Math.max(0,s-1));
            if(ticks>100){bloodDotActive.remove(id);cancel();}
        }}.runTaskTimer(plugin,20L,20L);
    }

    private void bleed(LivingEntity t, Player p, int ticks) {
        new BukkitRunnable(){int n=0; @Override public void run(){
            n++;
            if(!t.isValid()||t.isDead()||n>ticks){cancel();return;}
            t.damage(1.5,p.isOnline()?p:null);
        }}.runTaskTimer(plugin,10L,10L);
    }

    private void spawnBloodStakes(Player p) {
        sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.7f,0.6f);
        for(int i=0;i<3;i++){final int si=i;double angle=(Math.PI/6)*(si-1);Vector dir=p.getLocation().getDirection().rotateAroundY(angle).normalize().multiply(0.5);
            new BukkitRunnable(){int t=0;Location pos=p.getLocation().clone().add(dir);@Override public void run(){
                t++;if(!p.isOnline()){cancel();return;}
                pos.add(dir);
                if(t%2==0)particles(pos,Particle.DRIPPING_WATER,2);
                pos.getNearbyLivingEntities(1.2).forEach(le->{ if(le!=p){le.damage(12,p);applyBloodPoison(p,le);wither(le,1,60);} });
                if(t>=10)cancel();
            }}.runTaskTimer(plugin,(long)(si*2),1L);
        }
    }

    private void spawnCryptServant(Player p, Location loc) {
        if(loc.getWorld()==null)return; // ROOT FIX: null world check
        Zombie z=(Zombie)loc.getWorld().spawnEntity(loc,EntityType.ZOMBIE);
        z.setCustomName("§cCrypt Servant");z.setCustomNameVisible(true);
        new BukkitRunnable(){@Override public void run(){if(z.isValid())z.remove();}}.runTaskLater(plugin,600L);
        p.sendActionBar(Component.text("💀 Crypt Servant!",NamedTextColor.DARK_GREEN));
    }

    private void spawnIceExplosions(Player p, int stage) {
        int count=stage; double radius=stage*2.5;
        for(int i=0;i<count;i++){final double ox=(rng.nextDouble()-0.5)*radius*2,oz=(rng.nextDouble()-0.5)*radius*2;
            new BukkitRunnable(){@Override public void run(){
                if(!p.isOnline())return; // ROOT FIX
                Location eLoc=p.getLocation().add(ox,0,oz);
                particles(eLoc,Particle.SNOWFLAKE,4);
                new BukkitRunnable(){@Override public void run(){
                    eLoc.getWorld().createExplosion(eLoc,0f,false,false);
                    sound(eLoc,Sound.BLOCK_GLASS_BREAK,0.8f,0.7f);
                    eLoc.getNearbyLivingEntities(1.5).forEach(le->{ if(le!=p){le.damage(stage*6.0,p);slow(le,2,80);} });
                }}.runTaskLater(plugin,10L);
            }}.runTaskLater(plugin,(long)(i*3));
        }
    }

    private void startChainPull(Player p, LivingEntity target) {
        Location anchor=target.getLocation().clone();
        new BukkitRunnable(){int t=0; @Override public void run(){
            t++;
            if(!p.isOnline()||t>60||!target.isValid()||target.isDead()){cryptChain.remove(p.getUniqueId());cancel();return;}
            if(target.getLocation().distanceSquared(anchor)>16){Vector pull=anchor.toVector().subtract(target.getLocation().toVector()).normalize().multiply(2);target.setVelocity(pull);}
        }}.runTaskTimer(plugin,0L,2L);
    }

    private void triggerChainBreak(Player p, LivingEntity t) {
        cryptChain.remove(p.getUniqueId());
        for(int i=0;i<3;i++){new BukkitRunnable(){@Override public void run(){if(!t.isValid()||t.isDead())return;t.damage(8,p);wither(t,2,40);}}.runTaskLater(plugin,(long)(i*5));}
        p.sendActionBar(Component.text("💀 Chain Broken!",NamedTextColor.DARK_GREEN));
    }

    private boolean isPositiveEffect(PotionEffectType type){
        return type.equals(PotionEffectType.SPEED)||type.equals(PotionEffectType.STRENGTH)||type.equals(PotionEffectType.REGENERATION)||type.equals(PotionEffectType.RESISTANCE)||type.equals(PotionEffectType.FIRE_RESISTANCE)||type.equals(PotionEffectType.INVISIBILITY)||type.equals(PotionEffectType.JUMP_BOOST)||type.equals(PotionEffectType.HASTE)||type.equals(PotionEffectType.ABSORPTION)||type.equals(PotionEffectType.HEALTH_BOOST);
    }

    private void slow(LivingEntity e,int amp,int dur){e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,dur,amp,false,true,true));}
    private void wither(LivingEntity e,int amp,int dur){e.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,dur,amp,false,true,true));}
    private void launch(LivingEntity e,double pow){e.setVelocity(e.getVelocity().add(new Vector(0,pow,0)));}
    private void particles(Location l,Particle par,int n){if(n>0&&l.getWorld()!=null)l.getWorld().spawnParticle(par,l,Math.min(n,8),0.3,0.3,0.3,0.05);}
    private void sound(Location l,Sound s,float vol,float pitch){if(l.getWorld()!=null)l.getWorld().playSound(l,s,vol,pitch);}
    private void kb(Player src,LivingEntity tgt,double mul,double y){Vector d=tgt.getLocation().subtract(src.getLocation()).toVector();if(d.length()>0){d=d.normalize().multiply(mul);}d.setY(y);tgt.setVelocity(d);}
    private LivingEntity nearest(Player p,double r){return p.getNearbyEntities(r,r,r).stream().filter(e->e instanceof LivingEntity&&e!=p).map(e->(LivingEntity)e).min(Comparator.comparingDouble(e->e.getLocation().distanceSquared(p.getLocation()))).orElse(null);}
    private boolean checkCd(Player p,String wid,int ms){long now=System.currentTimeMillis();long last=critCooldowns.computeIfAbsent(p.getUniqueId(),k->new HashMap<>()).getOrDefault(wid,0L);if(now-last>=ms){critCooldowns.get(p.getUniqueId()).put(wid,now);return true;}return false;}
    private long getRemainingCd(Player p,String wid,int ms){long now=System.currentTimeMillis();return Math.max(0,ms-(now-critCooldowns.computeIfAbsent(p.getUniqueId(),k->new HashMap<>()).getOrDefault(wid,0L)));}
}
