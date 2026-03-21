package com.deepwoken.weapons.listeners;

import com.deepwoken.weapons.DeepwokenWeapons;
import com.deepwoken.weapons.managers.WeaponManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
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
    private final Map<UUID, Map<String, Long>> critCooldowns = new HashMap<>();
    private final Map<UUID, Integer> bullets       = new HashMap<>();
    private final Map<UUID, Integer> hailStage     = new HashMap<>();
    private final Set<UUID>          darkRift      = new HashSet<>();
    private final Map<UUID, LivingEntity> cryptChain = new HashMap<>();
    private final Map<UUID, Integer> resonance     = new HashMap<>();
    private final Map<UUID, Integer> heat          = new HashMap<>();
    private final Set<UUID>          flameDisabled = new HashSet<>();
    private final Map<UUID, Integer> rhythm        = new HashMap<>();
    private final Map<UUID, Long>    lastRhythm    = new HashMap<>();
    private final Map<UUID, Integer> stormeyeHits  = new HashMap<>();
    private final Map<UUID, Long>    shockwaveCd   = new HashMap<>();
    private final Map<UUID, Long>    pyreFlameCd   = new HashMap<>();
    private final Map<UUID, Integer> zodiacSign    = new HashMap<>();
    private final Map<UUID, Integer> voidMeter     = new HashMap<>();
    private final Map<UUID, Integer> orbitCharge   = new HashMap<>();
    private final Map<UUID, Long>    lastMove      = new HashMap<>();
    private final Set<UUID>          bloodDotActive = new HashSet<>();
    // Boltcrusher hit tracking to prevent infinite loop
    private final Set<UUID>          boltcrusherHitting = new HashSet<>();

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

    // ─── Join/Quit ────────────────────────────────────────────────────────────
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        plugin.getBloodBarManager().showBar(e.getPlayer());
        UUID uuid = e.getPlayer().getUniqueId();
        bullets.put(uuid, 6);
        hailStage.put(uuid, 1);
        stormeyeHits.put(uuid, 0);
        zodiacSign.put(uuid, 0);
        voidMeter.put(uuid, 0);
        orbitCharge.put(uuid, 0);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        plugin.getBloodBarManager().hideBar(e.getPlayer());
        UUID uuid = e.getPlayer().getUniqueId();
        cryptChain.remove(uuid);
        bloodDotActive.remove(uuid);
        boltcrusherHitting.remove(uuid);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        String wid = WeaponManager.getWeaponId(p.getInventory().getItemInMainHand());
        if (!"orbit".equals(wid)) return;
        if (e.getFrom().distanceSquared(e.getTo()) < 0.01) return;
        lastMove.put(p.getUniqueId(), System.currentTimeMillis());
        int charge = Math.min(10, orbitCharge.getOrDefault(p.getUniqueId(), 0) + 1);
        orbitCharge.put(p.getUniqueId(), charge);
    }

    // ─── M1 ──────────────────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity t)) return;
        if (t.isDead()) return;
        // Skip if damage was blocked/absorbed to 0
        if (e.getFinalDamage() <= 0 && !e.isCancelled()) return;

        String wid = WeaponManager.getWeaponId(p.getInventory().getItemInMainHand());
        if (wid == null) return;
        plugin.getBloodBarManager().onWeaponHit(p);

        switch (wid) {
            case "kyrsewinter" -> {
                slow(t,2,80);
                // Only apply bleed if damage actually landed (not blocked)
                if (e.getFinalDamage() > 0) bleed(t,p,3);
                particles(t.getLocation(),Particle.SNOWFLAKE,6);
                sound(t.getLocation(),Sound.BLOCK_GLASS_BREAK,0.7f,1.5f);
            }
            case "stormseye"   -> m1Stormseye(p, t);
            case "hero_blade_fire"    -> { fireDamage(t,p,3,60); } // use fireDamage not setFireTicks
            case "hero_blade_ice"     -> slow(t,2,60);
            case "hero_blade_wind"    -> launch(t,0.8);
            case "hero_blade_thunder" -> {
                long nowT=System.currentTimeMillis();
                long lastT=critCooldowns.computeIfAbsent(p.getUniqueId(),k->new HashMap<>()).getOrDefault("thunder_m1",0L);
                if(nowT-lastT>=800){ critCooldowns.get(p.getUniqueId()).put("thunder_m1",nowT); t.getWorld().strikeLightningEffect(t.getLocation()); t.damage(3,p); particles(t.getLocation(),Particle.ELECTRIC_SPARK,5); }
            }
            case "boltcrusher"  -> m1Boltcrusher(p,t);
            case "hailbreaker"  -> { slow(t,2,60); particles(t.getLocation(),Particle.SNOWFLAKE,5); }
            case "deepspindle"  -> { wither(t,1,100); t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,60,0,false,true,true)); particles(t.getLocation(),Particle.ASH,6); }
            case "red_death"    -> {
                // Nerfed — fire + poison only, blood poison added less aggressively
                fireDamage(t,p,2,60);
                t.addPotionEffect(new PotionEffect(PotionEffectType.POISON,40,0,false,true,true));
                // Blood poison only every other hit
                long nowR=System.currentTimeMillis();
                long lastR=critCooldowns.computeIfAbsent(p.getUniqueId(),k->new HashMap<>()).getOrDefault("red_m1",0L);
                if(nowR-lastR>=600){ critCooldowns.get(p.getUniqueId()).put("red_m1",nowR); m1Bloodfouler(p,t); }
            }
            case "crypt_blade"  -> { wither(t,1,100); particles(t.getLocation(),Particle.ASH,5); if(cryptChain.containsKey(p.getUniqueId())&&cryptChain.get(p.getUniqueId()).equals(t)) triggerChainBreak(p,t); }
            case "soulthorn"    -> m1Soulthorn(p,t);
            case "bloodfouler"  -> m1Bloodfouler(p,t);
            case "yselys_pyre_keeper" -> m1PyreKeeper(p,t);
            case "gale_pale"    -> { kb(p,t,2.5,0.3); slow(t,1,40); particles(t.getLocation(),Particle.CLOUD,4); }
            case "cold_point"   -> { slow(t,3,100); particles(t.getLocation(),Particle.SNOWFLAKE,5); sound(t.getLocation(),Sound.BLOCK_GLASS_BREAK,0.8f,1.6f); }
            case "iron_requiem" -> m1IronRequiem(p,t,e);
            case "amethyst"     -> m1Amethyst(p,t,e);
            case "flamewall"    -> m1Flamewall(p,t,e);
            case "im_blue"      -> m1ImBlue(p,t,e);
            case "zodiac"       -> m1Zodiac(p,t,e);
            case "nullscapes"   -> m1Nullscapes(p,t,e);
            case "orbit"        -> m1Orbit(p,t,e);
        }
    }

    // ─── Kill ─────────────────────────────────────────────────────────────────
    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (!(e.getEntity().getKiller() instanceof Player p)) return;
        String wid = WeaponManager.getWeaponId(p.getInventory().getItemInMainHand());
        if (wid == null) return;
        switch (wid) {
            case "red_death" -> { p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+2)); particles(p.getLocation().add(0,1,0),Particle.HEART,4); p.sendActionBar(Component.text("❤ Lifesteal!",NamedTextColor.RED)); } // nerfed to 1 heart
            case "crypt_blade" -> {
                Zombie z=(Zombie)e.getEntity().getLocation().getWorld().spawnEntity(e.getEntity().getLocation(),EntityType.ZOMBIE);
                z.setCustomName("§cCrypt Servant"); z.setCustomNameVisible(true);
                new BukkitRunnable(){@Override public void run(){if(z.isValid())z.remove();}}.runTaskLater(plugin,600L);
                p.sendActionBar(Component.text("💀 Crypt Servant summoned!",NamedTextColor.DARK_GREEN));
            }
            case "amethyst" -> addResonance(p,2);
            case "nullscapes" -> { int v=Math.min(10,voidMeter.getOrDefault(p.getUniqueId(),0)+2); voidMeter.put(p.getUniqueId(),v); }
        }
    }

    // ─── Right Click (Crits) ──────────────────────────────────────────────────
    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        var act = e.getAction();
        if (act != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && act != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        String wid = WeaponManager.getWeaponId(p.getInventory().getItemInMainHand());
        if (wid == null) return;
        e.setCancelled(true);
        if (wid.equals("flamewall") && flameDisabled.contains(p.getUniqueId())) { p.sendActionBar(Component.text("🔥 Overheated! Wait...",NamedTextColor.RED)); return; }
        int cdMs = switch(wid) {
            case "red_death" -> 8000;
            case "boltcrusher","crypt_blade","stormseye","hero_blade_thunder","yselys_pyre_keeper","zodiac","nullscapes","orbit" -> 10000;
            default -> 9000;
        };
        if (!checkCd(p,wid,cdMs)) { p.sendActionBar(Component.text("⏱ "+getRemainingCd(p,wid,cdMs)/1000.0+"s",NamedTextColor.YELLOW)); return; }
        switch (wid) {
            case "railblade"          -> critRailblade(p);
            case "kyrsewinter"        -> critKyrsewinter(p);
            case "deepspindle"        -> critDeepspindle(p);
            case "boltcrusher"        -> critBoltcrusher(p);
            case "crypt_blade"        -> critCryptBlade(p);
            case "red_death"          -> critRedDeath(p);
            case "hailbreaker"        -> critHailbreaker(p);
            case "yselys_pyre_keeper" -> critPyreKeeper(p);
            case "soulthorn"          -> critSoulthorn(p);
            case "gale_pale"          -> critGalePale(p);
            case "cold_point"         -> critColdPoint(p);
            case "iron_requiem"       -> critIronRequiem(p);
            case "bloodfouler"        -> critBloodfouler(p);
            case "amethyst"           -> critAmethyst(p);
            case "flamewall"          -> critFlamewall(p);
            case "im_blue"            -> critImBlue(p);
            case "stormseye"          -> critStormseye(p);
            case "hero_blade_thunder" -> critHeroThunder(p);
            case "zodiac"             -> critZodiac(p);
            case "nullscapes"         -> critNullscapes(p);
            case "orbit"              -> critOrbit(p);
        }
    }

    @EventHandler
    public void onSwitch(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        p.removePotionEffect(PotionEffectType.SPEED);
        stormeyeHits.put(p.getUniqueId(), 0);
        cryptChain.remove(p.getUniqueId()); // clean up chain on weapon switch
        var it = p.getInventory().getItem(e.getNewSlot());
        String wid = WeaponManager.getWeaponId(it);
        if (wid != null && (wid.equals("railblade")||wid.equals("hero_blade_wind")))
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,Integer.MAX_VALUE,1,false,true,true));
        if (wid != null && wid.equals("yselys_pyre_keeper")) {
            var offhand = p.getInventory().getItemInOffHand();
            String offWid = WeaponManager.getWeaponId(offhand);
            if (offWid == null || !offWid.equals("verdant")) {
                ItemStack verdant = DeepwokenWeapons.getInstance().getWeaponManager().getWeapon("verdant");
                if (verdant != null) p.getInventory().setItemInOffHand(verdant);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  M1 IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    private void m1Stormseye(Player p, LivingEntity t) {
        UUID uuid = p.getUniqueId();
        // Rosen's Fakeout — safe teleport behind enemy
        if (!p.isOnGround() && t.getLocation().distanceSquared(p.getLocation()) <= 9) {
            Location safe = getSafeTeleportBehind(p, t, 2.0);
            if (safe != null) {
                p.teleport(safe);
                particles(p.getLocation(),Particle.ELECTRIC_SPARK,8);
                sound(p.getLocation(),Sound.ENTITY_ENDERMAN_TELEPORT,0.7f,1.5f);
                p.sendActionBar(Component.text("⚡ Rosen's Fakeout!",NamedTextColor.YELLOW));
            }
        }
        int hits = stormeyeHits.getOrDefault(uuid,0)+1;
        stormeyeHits.put(uuid,hits);
        particles(t.getLocation(),Particle.ELECTRIC_SPARK,5);
        sound(t.getLocation(),Sound.ENTITY_ARROW_HIT,0.7f,1.5f);
        if (hits>=3) {
            stormeyeHits.put(uuid,0);
            long now=System.currentTimeMillis();
            if (now-shockwaveCd.getOrDefault(uuid,0L)>=10000) {
                shockwaveCd.put(uuid,now);
                p.getLocation().getNearbyLivingEntities(4).stream().limit(5).forEach(le->{ if(le!=p){ le.damage(6,p); kb(p,le,3,0.8); slow(le,1,60); le.getWorld().strikeLightningEffect(le.getLocation()); } });
                p.getWorld().createExplosion(p.getLocation(),0f,false,false);
                p.sendActionBar(Component.text("⚡ Medallion Shockwave!",NamedTextColor.YELLOW));
            }
        }
    }

    private void m1PyreKeeper(Player p, LivingEntity t) {
        long now = System.currentTimeMillis();
        UUID uuid = p.getUniqueId();
        if (now - pyreFlameCd.getOrDefault(uuid,0L) >= 4000) {
            pyreFlameCd.put(uuid,now);
            fireDamage(t,p,3,80);
            t.getWorld().spawnParticle(Particle.FLAME,t.getLocation().add(0,1,0),8,0.3,0.5,0.3,0.05);
            t.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,t.getLocation().add(0,1,0),4,0.3,0.5,0.3,0.05);
            sound(t.getLocation(),Sound.ENTITY_BLAZE_SHOOT,0.6f,1.2f);
            p.sendActionBar(Component.text("🔥 Lifelord's Blaze!",NamedTextColor.GREEN));
        } else {
            fireDamage(t,p,1,40);
            particles(t.getLocation(),Particle.FLAME,3);
        }
    }

    private void m1Soulthorn(Player p, LivingEntity t) {
        var pdc=t.getPersistentDataContainer();
        int marks=pdc.getOrDefault(DeepwokenWeapons.SOUL_MARKS_KEY,PersistentDataType.INTEGER,0);
        if(marks<3){marks++;pdc.set(DeepwokenWeapons.SOUL_MARKS_KEY,PersistentDataType.INTEGER,marks);}
        particles(t.getLocation().add(0,1,0),marks==3?Particle.TOTEM_OF_UNDYING:Particle.END_ROD,Math.min(marks*5,8));
        p.sendActionBar(Component.text("✦".repeat(marks)+" Soul Mark: "+marks+"/3",NamedTextColor.LIGHT_PURPLE));
        if(marks==3) sound(t.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,0.5f,2f);
    }

    private void m1Bloodfouler(Player p, LivingEntity t) {
        var pdc=t.getPersistentDataContainer();
        int s=Math.min(10,pdc.getOrDefault(DeepwokenWeapons.BLOOD_POISON_KEY,PersistentDataType.INTEGER,0)+1);
        pdc.set(DeepwokenWeapons.BLOOD_POISON_KEY,PersistentDataType.INTEGER,s);
        t.addPotionEffect(new PotionEffect(PotionEffectType.POISON,40,0,false,true,true));
        particles(t.getLocation(),Particle.DRIPPING_WATER,4);
        startBloodDot(t,p);
    }

    private void m1Boltcrusher(Player p, LivingEntity t) {
        UUID uuid = p.getUniqueId();
        // Prevent infinite loop — if already processing a boltcrusher hit, skip AOE
        if (boltcrusherHitting.contains(uuid)) return;
        boltcrusherHitting.add(uuid);
        t.getNearbyEntities(4,4,4).stream().limit(3).forEach(en->{
            if(en instanceof LivingEntity le&&en!=p&&le!=t){
                le.getWorld().strikeLightningEffect(le.getLocation());
                le.damage(5,p);
            }
        });
        kb(p,t,2,0.5);
        particles(t.getLocation(),Particle.ELECTRIC_SPARK,6);
        // Remove flag after a tick
        new BukkitRunnable(){@Override public void run(){boltcrusherHitting.remove(uuid);}}.runTaskLater(plugin,1L);
    }

    private void m1IronRequiem(Player p, LivingEntity t, EntityDamageByEntityEvent e) {
        int b=bullets.getOrDefault(p.getUniqueId(),6);
        if(b>0){
            bullets.put(p.getUniqueId(),b-1);
            // Bonus ranged damage
            e.setDamage(e.getDamage()*1.5);
            p.sendActionBar(Component.text("🔫 Bullets: "+(b-1)+"/6",NamedTextColor.GRAY));
            particles(t.getLocation(),Particle.CRIT,5);
        } else {
            slow(t,0,40);
            e.setDamage(e.getDamage()*0.5); // reduced rod damage
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,60,1,false,true,true));
            p.sendActionBar(Component.text("⚡ Rod! No bullets — use crit to reload",NamedTextColor.RED));
        }
    }

    private void m1Amethyst(Player p, LivingEntity t, EntityDamageByEntityEvent e) {
        // Boost base damage
        e.setDamage(e.getDamage()+4);
        addResonance(p,1);
        particles(t.getLocation(),Particle.END_ROD,4);
    }

    private void m1Flamewall(Player p, LivingEntity t, EntityDamageByEntityEvent e) {
        if(flameDisabled.contains(p.getUniqueId())) return;
        int h=Math.min(10,heat.getOrDefault(p.getUniqueId(),0)+2); // builds 2 per hit now
        heat.put(p.getUniqueId(),h);
        // Damage scales with heat
        e.setDamage(e.getDamage()+(h*0.5));
        fireDamage(t,p,1,40+(h*6));
        t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,60,0,false,true,true));
        particles(t.getLocation(),Particle.FLAME,Math.min(h,6));
        p.sendActionBar(Component.text("🔥 Heat: "+h+"/10",h>=8?NamedTextColor.RED:NamedTextColor.GOLD));
    }

    private void m1ImBlue(Player p, LivingEntity t, EntityDamageByEntityEvent e) {
        long now=System.currentTimeMillis();
        long last=lastRhythm.getOrDefault(p.getUniqueId(),0L);
        int r=rhythm.getOrDefault(p.getUniqueId(),0);
        if(now-last<=3000){ r=Math.min(10,r+1); } // 3 second window now
        else { r=Math.max(0,r-2); } // lose 2 stacks instead of full reset
        rhythm.put(p.getUniqueId(),r); lastRhythm.put(p.getUniqueId(),now);
        // Damage scales with rhythm
        e.setDamage(e.getDamage()+(r*0.4));
        particles(t.getLocation(),Particle.DRIPPING_WATER,Math.min(r+1,6));
        p.sendActionBar(Component.text("🌊 Rhythm: "+r+"/10",r>=8?NamedTextColor.AQUA:NamedTextColor.BLUE));
        if(r>=5) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,30,0,false,true,true));
        if(r>=8) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,30,0,false,true,true));
    }

    private void m1Zodiac(Player p, LivingEntity t, EntityDamageByEntityEvent e) {
        int sign = zodiacSign.getOrDefault(p.getUniqueId(), 0);
        // Boost base damage for all signs
        e.setDamage(e.getDamage()+3);
        switch(sign) {
            case 0 -> { // Aries — fire damage (bypasses fire res via direct damage)
                fireDamage(t,p,2,60);
                particles(t.getLocation(), Particle.FLAME, 5);
                p.sendActionBar(Component.text("♈ Aries Active", NamedTextColor.RED));
            }
            case 1 -> { // Libra — resistance buff on self only, NO damage to self
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, true, true));
                particles(t.getLocation(), Particle.END_ROD, 4);
                p.sendActionBar(Component.text("♎ Libra Active", NamedTextColor.WHITE));
            }
            case 2 -> { // Scorpio — poison
                t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1, false, true, true));
                particles(t.getLocation(), Particle.TOTEM_OF_UNDYING, 5);
                p.sendActionBar(Component.text("♏ Scorpio Active", NamedTextColor.DARK_GREEN));
            }
        }
    }

    private void m1Nullscapes(Player p, LivingEntity t, EntityDamageByEntityEvent e) {
        // Boost base damage
        e.setDamage(e.getDamage()+3);
        t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, true, true));
        slow(t, 0, 60);
        particles(t.getLocation(), Particle.ASH, 5);
        int v = Math.min(10, voidMeter.getOrDefault(p.getUniqueId(), 0) + 1);
        voidMeter.put(p.getUniqueId(), v);
        p.sendActionBar(Component.text("🕳 Void: "+v+"/10", v>=8?NamedTextColor.DARK_GRAY:NamedTextColor.GRAY));
    }

    private void m1Orbit(Player p, LivingEntity t, EntityDamageByEntityEvent e) {
        // Boost base damage
        e.setDamage(e.getDamage()+3);
        Vector pull = p.getLocation().subtract(t.getLocation()).toVector().normalize().multiply(0.3);
        t.setVelocity(t.getVelocity().add(pull));
        particles(t.getLocation(), Particle.END_ROD, 4);
        long lastMoveTime = lastMove.getOrDefault(p.getUniqueId(), 0L);
        boolean momentum = System.currentTimeMillis() - lastMoveTime < 500;
        if (momentum) {
            int charge = Math.min(10, orbitCharge.getOrDefault(p.getUniqueId(), 0) + 1);
            orbitCharge.put(p.getUniqueId(), charge);
            e.setDamage(e.getDamage()+(charge*0.3)); // extra momentum damage
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 30, 0, false, true, true));
            p.sendActionBar(Component.text("🪐 Momentum: "+charge+"/10", NamedTextColor.BLUE));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CRIT IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    private void critStormseye(Player p) {
        p.sendActionBar(Component.text("⚡ Storm Circle!",NamedTextColor.YELLOW));
        LivingEntity target=nearest(p,15);
        if(target==null){p.sendActionBar(Component.text("No target in range.",NamedTextColor.GRAY));return;}
        p.setVelocity(new Vector(0,0.5,0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,40,4,false,true,true));
        sound(p.getLocation(),Sound.ENTITY_LIGHTNING_BOLT_THUNDER,0.5f,1.5f);
        for(int i=0;i<3;i++){final int si=i;
            new BukkitRunnable(){@Override public void run(){
                if(!target.isValid()||target.isDead()) return;
                double angle=(Math.PI*2/3)*si;
                // Safe teleport to orbit position
                Location orbitPos=target.getLocation().add(Math.cos(angle)*3,1.5,Math.sin(angle)*3);
                Location safe = getSafeTeleport(orbitPos);
                if(safe!=null) p.teleport(safe);
                particles(p.getLocation(),Particle.ELECTRIC_SPARK,6);
                sound(p.getLocation(),Sound.ENTITY_ARROW_SHOOT,0.8f,1.8f);
                target.damage(si==2?14:10,p);
                if(si==2){slow(target,3,100);target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,80,1,false,true,true));target.getWorld().strikeLightningEffect(target.getLocation());p.sendActionBar(Component.text("⚡ SHOCKED!",NamedTextColor.YELLOW));}
                particles(target.getLocation(),Particle.ELECTRIC_SPARK,6);
            }}.runTaskLater(plugin,(long)(si*10+5));
        }
        new BukkitRunnable(){@Override public void run(){p.removePotionEffect(PotionEffectType.RESISTANCE);}}.runTaskLater(plugin,40L);
    }

    private void critHeroThunder(Player p) {
        p.sendActionBar(Component.text("⚡ Thunder Slam!",NamedTextColor.YELLOW));
        particles(p.getLocation(),Particle.ELECTRIC_SPARK,8);
        sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.8f,0.4f);
        new BukkitRunnable(){@Override public void run(){
            Vector dir=p.getLocation().getDirection().normalize();
            for(int i=1;i<=6;i++){final int fi=i;
                new BukkitRunnable(){@Override public void run(){
                    Location lineLoc=p.getLocation().add(dir.clone().multiply(fi));
                    lineLoc.getWorld().strikeLightningEffect(lineLoc);
                    lineLoc.getNearbyLivingEntities(1.5).forEach(le->{ if(le!=p){
                        le.damage(16,p); kb(p,le,2,0.8); launch(le,1.0); slow(le,1,60);
                    }});
                }}.runTaskLater(plugin,(long)(fi*3));
            }
        }}.runTaskLater(plugin,8L);
    }

    private void critZodiac(Player p) {
        int sign = zodiacSign.getOrDefault(p.getUniqueId(), 0);
        switch(sign) {
            case 0 -> { // Aries Impact
                p.sendActionBar(Component.text("♈ ARIES IMPACT!",NamedTextColor.RED));
                Vector slam=p.getLocation().getDirection().normalize().multiply(2.5);slam.setY(0.2);p.setVelocity(slam);
                particles(p.getLocation(),Particle.FLAME,8);
                sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.6f,1.2f);
                // Only hit once after delay — no loop
                new BukkitRunnable(){@Override public void run(){
                    p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){
                        le.damage(18,p); fireDamage(le,p,2,80); kb(p,le,3,0.8);
                        particles(le.getLocation(),Particle.FLAME,6);
                    }});
                    sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_CRIT,1f,0.7f);
                }}.runTaskLater(plugin,6L);
                zodiacSign.put(p.getUniqueId(), 1);
                new BukkitRunnable(){@Override public void run(){p.sendActionBar(Component.text("♈ → ♎ Libra next!",NamedTextColor.WHITE));}}.runTaskLater(plugin,3L);
            }
            case 1 -> { // Libra Balance — pure defensive, no self damage whatsoever
                p.sendActionBar(Component.text("♎ LIBRA BALANCE!",NamedTextColor.WHITE));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,100,2,false,true,true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,60,1,false,true,true));
                particles(p.getLocation(),Particle.END_ROD,8);
                sound(p.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,0.7f,1.2f);
                // Push enemies away (no damage from this)
                p.getLocation().getNearbyLivingEntities(5).stream().limit(6).forEach(le->{
                    if(le!=p){ kb(p,le,2.5,0.5); particles(le.getLocation(),Particle.END_ROD,4); }
                });
                zodiacSign.put(p.getUniqueId(), 2);
                new BukkitRunnable(){@Override public void run(){p.sendActionBar(Component.text("♎ → ♏ Scorpio next!",NamedTextColor.DARK_GREEN));}}.runTaskLater(plugin,3L);
            }
            case 2 -> { // Scorpio Sting
                LivingEntity target=nearest(p,10);
                if(target==null){p.sendActionBar(Component.text("No target in range.",NamedTextColor.GRAY));return;}
                p.sendActionBar(Component.text("♏ SCORPIO STING!",NamedTextColor.DARK_GREEN));
                // Safe teleport behind target
                Location stingPos = getSafeTeleportBehind(p, target, 2.0);
                if(stingPos!=null) p.teleport(stingPos);
                particles(p.getLocation(),Particle.TOTEM_OF_UNDYING,8);
                sound(p.getLocation(),Sound.ENTITY_ENDERMAN_TELEPORT,0.7f,0.8f);
                target.damage(20,p);
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON,200,2,false,true,true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,100,1,false,true,true));
                particles(target.getLocation(),Particle.TOTEM_OF_UNDYING,8);
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,60,1,false,true,true));
                zodiacSign.put(p.getUniqueId(), 0);
                new BukkitRunnable(){@Override public void run(){p.sendActionBar(Component.text("♏ → ♈ Aries next!",NamedTextColor.RED));}}.runTaskLater(plugin,3L);
            }
        }
    }

    private void critNullscapes(Player p) {
        int v=voidMeter.getOrDefault(p.getUniqueId(),0);
        if(p.isSneaking()){
            if(v<10){p.sendActionBar(Component.text("🕳 Need full Void! ("+v+"/10)",NamedTextColor.DARK_GRAY));return;}
            p.sendActionBar(Component.text("🕳 ABSOLUTE ZERO!",NamedTextColor.DARK_GRAY));
            particles(p.getLocation(),Particle.SQUID_INK,8);
            sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.8f,0.3f);
            p.getLocation().getNearbyLivingEntities(6).stream().limit(8).forEach(le->{ if(le!=p){
                le.damage(18,p); slow(le,6,100); le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,100,2,false,true,true)); le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,60,0,false,true,true));
                // Don't zero velocity — just heavy slow instead
                particles(le.getLocation(),Particle.SQUID_INK,6);
            }});
            voidMeter.put(p.getUniqueId(),0);
        } else if(p.isSprinting()){
            p.sendActionBar(Component.text("🕳 Rift Break!",NamedTextColor.DARK_GRAY));
            particles(p.getLocation(),Particle.ASH,8);
            sound(p.getLocation(),Sound.ENTITY_WITHER_AMBIENT,0.7f,0.5f);
            LivingEntity nearestTarget=nearest(p,15);
            Location center=nearestTarget!=null?nearestTarget.getLocation():p.getLocation().add(p.getLocation().getDirection().multiply(5));
            p.getLocation().getNearbyLivingEntities(12).stream().limit(8).forEach(le->{ if(le!=p){
                Vector pull=center.toVector().subtract(le.getLocation().toVector()).normalize().multiply(2.5);
                le.setVelocity(pull); le.damage(10,p);
            }});
            new BukkitRunnable(){@Override public void run(){
                center.getWorld().createExplosion(center,0f,false,false);
                center.getNearbyLivingEntities(3).forEach(le->{ if(le!=p){le.damage(12,p);} });
                particles(center,Particle.ASH,8);
            }}.runTaskLater(plugin,15L);
            voidMeter.put(p.getUniqueId(),Math.max(0,v-3));
        } else {
            p.sendActionBar(Component.text("🕳 Erase Pulse!",NamedTextColor.DARK_GRAY));
            particles(p.getLocation(),Particle.ASH,8);
            sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.6f,1.5f);
            for(int i=1;i<=4;i++){final int ri=i;new BukkitRunnable(){@Override public void run(){
                p.getLocation().getNearbyLivingEntities(ri*2.0).stream().limit(6).forEach(le->{ if(le!=p){
                    le.damage(10,p);
                    le.getActivePotionEffects().forEach(eff->{ if(isPositiveEffect(eff.getType())) le.removePotionEffect(eff.getType()); });
                    slow(le,0,40);
                }});
                sound(p.getLocation(),Sound.ENTITY_WITHER_AMBIENT,0.4f,1.5f);
            }}.runTaskLater(plugin,(long)(ri*4));}
            voidMeter.put(p.getUniqueId(),Math.max(0,v-2));
        }
    }

    private void critOrbit(Player p) {
        int charge=orbitCharge.getOrDefault(p.getUniqueId(),0);
        if(p.isSneaking()&&charge>=8){
            p.sendActionBar(Component.text("🪐 EVENT HORIZON!",NamedTextColor.BLUE));
            Location center=p.getLocation().add(p.getLocation().getDirection().multiply(4));
            particles(center,Particle.END_ROD,8);
            sound(center,Sound.ENTITY_WITHER_SHOOT,0.8f,0.3f);
            new BukkitRunnable(){int t=0;@Override public void run(){t++;
                if(!p.isOnline()||t>80){cancel();return;}
                if(t%2==0){center.getNearbyLivingEntities(8).stream().limit(6).forEach(le->{ if(le!=p){
                    Vector pull=center.toVector().subtract(le.getLocation().toVector()).normalize().multiply(1.5);
                    le.setVelocity(pull); le.damage(2,p);
                }});}
                sound(center,Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,0.3f,0.5f);
            }}.runTaskTimer(plugin,0L,1L);
            orbitCharge.put(p.getUniqueId(),0);
        } else if(p.isSprinting()){
            p.sendActionBar(Component.text("🪐 Satellite Barrage!",NamedTextColor.BLUE));
            int shots=Math.max(2,Math.min(5,2+(charge/2)));
            for(int i=0;i<shots;i++){final int si=i;
                new BukkitRunnable(){@Override public void run(){
                    LivingEntity target=nearest(p,20);
                    if(target==null) return;
                    // Fire arrow toward target
                    Arrow arrow=p.launchProjectile(Arrow.class,target.getLocation().add(0,1,0).subtract(p.getLocation().add(0,1,0)).toVector().normalize().multiply(3));
                    arrow.setShooter(p);
                    arrow.setDamage(8+(charge*0.5));
                    particles(p.getLocation(),Particle.END_ROD,4);
                    sound(p.getLocation(),Sound.ENTITY_ARROW_SHOOT,0.6f,1.8f);
                }}.runTaskLater(plugin,(long)(si*5));
            }
            orbitCharge.put(p.getUniqueId(),Math.max(0,charge-3));
        } else {
            p.sendActionBar(Component.text("🪐 Orbital Slam!",NamedTextColor.BLUE));
            p.setVelocity(new Vector(0,-3.5,0));
            particles(p.getLocation(),Particle.END_ROD,8);
            sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.6f,0.5f);
            new BukkitRunnable(){int t=0;@Override public void run(){t++;
                if((p.isOnGround()&&t>=3)||t>40){
                    p.getLocation().getNearbyLivingEntities(7).stream().limit(8).forEach(le->{ if(le!=p){
                        Vector pull=p.getLocation().subtract(le.getLocation()).toVector().normalize().multiply(2.5);
                        pull.setY(0.5); le.setVelocity(pull); le.damage(16,p); slow(le,1,60);
                        particles(le.getLocation(),Particle.END_ROD,4);
                    }});
                    p.getWorld().createExplosion(p.getLocation(),0f,false,false);
                    particles(p.getLocation(),Particle.END_ROD,8);
                    sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,1f,0.4f);
                    cancel();
                }
            }}.runTaskTimer(plugin,0L,1L);
            orbitCharge.put(p.getUniqueId(),Math.max(0,charge-3));
        }
    }

    private void critRailblade(Player p) {
        if(!p.isOnGround()){
            p.sendActionBar(Component.text("💨 Aerial Downslash!",NamedTextColor.GRAY));
            p.setVelocity(new Vector(0,0.4,0));particles(p.getLocation(),Particle.CRIT,6);sound(p.getLocation(),Sound.ENTITY_PHANTOM_FLAP,0.8f,2f);
            new BukkitRunnable(){int tick=0;@Override public void run(){tick++;
                if(tick==8){Vector slash=p.getLocation().getDirection().normalize().multiply(0.8);slash.setY(-2.5);p.setVelocity(slash);}
                if(tick>=8){p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(18,p);fireDamage(le,p,2,100);kb(p,le,1.5,0.3);slow(le,1,40);} });}
                if(tick>=20)cancel();
            }}.runTaskTimer(plugin,0L,1L);
        } else {
            p.sendActionBar(Component.text("💨 Railblade — Windup!",NamedTextColor.GRAY));
            particles(p.getLocation(),Particle.CRIT,5);sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.8f,0.6f);
            new BukkitRunnable(){@Override public void run(){p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(10,p);kb(p,le,2.5,0.5);slow(le,2,30);} });}}.runTaskLater(plugin,8L);
            new BukkitRunnable(){@Override public void run(){p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(18,p);fireDamage(le,p,3,120);particles(le.getLocation(),Particle.FLAME,5);sound(le.getLocation(),Sound.ENTITY_BLAZE_SHOOT,0.8f,1.2f);} });p.sendActionBar(Component.text("💨🔥 Flaming Blow!",NamedTextColor.GOLD));}}.runTaskLater(plugin,18L);
        }
    }

    private void critKyrsewinter(Player p) {
        if(p.isSprinting()){
            p.sendActionBar(Component.text("❄ Ice Leap!",NamedTextColor.AQUA));
            particles(p.getLocation(),Particle.SNOWFLAKE,6);sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.8f,0.5f);
            Vector leap=p.getLocation().getDirection().normalize().multiply(2.5);leap.setY(0.6);p.setVelocity(leap);
            new BukkitRunnable(){int tick=0;boolean hit=false;@Override public void run(){tick++;
                if(!hit){p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(14,p);kb(p,le,1.5,0.4);bleed(le,p,3);particles(le.getLocation(),Particle.SNOWFLAKE,5);hit=true;} });}
                if(tick==22){p.getLocation().getNearbyLivingEntities(4).stream().limit(5).forEach(le->{ if(le!=p){le.damage(12,p);slow(le,3,100);kb(p,le,2,0.3);} });particles(p.getLocation(),Particle.SNOWFLAKE,6);sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.6f,1.8f);}
                if(tick>=25)cancel();
            }}.runTaskTimer(plugin,0L,1L);
        } else {
            p.sendActionBar(Component.text("❄ Ice Poke!",NamedTextColor.AQUA));
            particles(p.getLocation(),Particle.SNOWFLAKE,5);sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.8f,0.7f);
            new BukkitRunnable(){@Override public void run(){
                p.getNearbyEntities(3.5,2,3.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){
                    le.damage(14,p);kb(p,le,2.5,0.5);bleed(le,p,3);particles(le.getLocation(),Particle.SNOWFLAKE,5);
                    Location bl=le.getLocation().clone();
                    new BukkitRunnable(){@Override public void run(){
                        bl.getWorld().createExplosion(bl,0f,false,false);
                        bl.getNearbyLivingEntities(3).stream().limit(5).forEach(e2->{ if(e2!=p){e2.damage(12,p);slow(e2,3,80);e2.setVelocity(new Vector(0,2,0));} });
                        particles(bl,Particle.SNOWFLAKE,6);sound(bl,Sound.ENTITY_GENERIC_EXPLODE,0.7f,1.6f);
                    }}.runTaskLater(plugin,20L);
                }});
            }}.runTaskLater(plugin,10L);
        }
    }

    private void critDeepspindle(Player p) {
        if(p.isSprinting()){
            if(darkRift.contains(p.getUniqueId())){p.sendActionBar(Component.text("☠ Dark Rift cooldown!",NamedTextColor.DARK_PURPLE));return;}
            p.sendActionBar(Component.text("☠ Dark Rift!",NamedTextColor.DARK_PURPLE));
            p.damage(4);particles(p.getLocation(),Particle.ASH,6);sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.8f,0.5f);
            LivingEntity target=nearest(p,20);
            if(target!=null){for(int i=0;i<2;i++){final int bi=i;new BukkitRunnable(){@Override public void run(){if(!target.isValid()||target.isDead())return;particles(target.getLocation(),Particle.ASH,5);target.damage(16,p);wither(target,2,80);sound(target.getLocation(),Sound.ENTITY_WITHER_AMBIENT,0.6f,1.5f);}}.runTaskLater(plugin,(long)(bi*8+5));}}
            darkRift.add(p.getUniqueId());new BukkitRunnable(){@Override public void run(){darkRift.remove(p.getUniqueId());}}.runTaskLater(plugin,400L);
        } else {
            LivingEntity target=nearest(p,5);if(target==null){p.sendActionBar(Component.text("No target in range.",NamedTextColor.GRAY));return;}
            p.sendActionBar(Component.text("☠ Void Thrust!",NamedTextColor.DARK_PURPLE));
            particles(p.getLocation(),Particle.ASH,6);sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.7f,1.2f);
            new BukkitRunnable(){@Override public void run(){if(!target.isValid()||target.isDead())return;
                target.damage(14,p);kb(p,target,1,0.2);particles(target.getLocation().add(0,1,0),Particle.TOTEM_OF_UNDYING,6);
                for(int i=0;i<7;i++){final int bi=i;new BukkitRunnable(){@Override public void run(){if(!target.isValid()||target.isDead())return;target.damage(5,p);wither(target,1,40);particles(target.getLocation(),Particle.ASH,3);}}.runTaskLater(plugin,(long)(bi*8+5));}
            }}.runTaskLater(plugin,8L);
        }
    }

    private void critBoltcrusher(Player p) {
        if(p.isSprinting()){
            p.sendActionBar(Component.text("⚡ Ground Slam!",NamedTextColor.YELLOW));
            particles(p.getLocation(),Particle.ELECTRIC_SPARK,6);
            new BukkitRunnable(){@Override public void run(){
                p.getLocation().getNearbyLivingEntities(6).stream().limit(6).forEach(le->{ if(le!=p){le.getWorld().strikeLightningEffect(le.getLocation());le.damage(16,p);slow(le,2,80);kb(p,le,2,0.3);} });
                p.getWorld().createExplosion(p.getLocation(),0f,false,false);
                p.sendActionBar(Component.text("⚡ SAPPED!",NamedTextColor.YELLOW));
            }}.runTaskLater(plugin,12L);
        } else {
            p.sendActionBar(Component.text("⚡ Clobber!",NamedTextColor.YELLOW));
            particles(p.getLocation(),Particle.ELECTRIC_SPARK,6);
            new BukkitRunnable(){@Override public void run(){
                p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(20,p);slow(le,2,100);kb(p,le,3,0.6);le.getWorld().strikeLightningEffect(le.getLocation());} });
                p.sendActionBar(Component.text("⚡ SAPPED!",NamedTextColor.YELLOW));
            }}.runTaskLater(plugin,14L);
        }
    }

    private void critCryptBlade(Player p) {
        if(p.isSprinting()){
            p.sendActionBar(Component.text("💀 Cleave!",NamedTextColor.DARK_GREEN));
            for(int i=0;i<2;i++){new BukkitRunnable(){@Override public void run(){p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(8,p);particles(le.getLocation(),Particle.ASH,4);} });}}.runTaskLater(plugin,(long)(i*5));}
            new BukkitRunnable(){@Override public void run(){p.getNearbyEntities(3.5,3.5,3.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(16,p);kb(p,le,3,0.8);wither(le,1,80);slow(le,2,60);} });}}.runTaskLater(plugin,14L);
        } else {
            p.sendActionBar(Component.text("💀 Darkness Plunge!",NamedTextColor.DARK_GREEN));
            particles(p.getLocation(),Particle.ASH,6);
            new BukkitRunnable(){@Override public void run(){
                p.getLocation().getNearbyLivingEntities(5).stream().limit(6).forEach(le->{ if(le!=p){
                    le.damage(12,p);wither(le,2,100);le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,60,0,false,true,true));
                    cryptChain.put(p.getUniqueId(),le);startChainPull(p,le);
                    p.sendActionBar(Component.text("💀 Chained!",NamedTextColor.DARK_GREEN));
                }});
            }}.runTaskLater(plugin,10L);
        }
    }

    private void critRedDeath(Player p) {
        if(p.isSprinting()){
            p.sendActionBar(Component.text("🩸 Blood Thrust!",NamedTextColor.RED));
            Vector move=p.getLocation().getDirection().normalize().multiply(0.4);
            for(int i=0;i<4;i++){new BukkitRunnable(){@Override public void run(){p.setVelocity(move);p.getNearbyEntities(2,2,2).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(6,p);m1Bloodfouler(p,le);} });}}.runTaskLater(plugin,(long)(i*4));}
            new BukkitRunnable(){@Override public void run(){p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(10,p);wither(le,1,60);} });spawnBloodStakes(p);}}.runTaskLater(plugin,22L);
        } else {
            p.sendActionBar(Component.text("🩸 Blood Stakes!",NamedTextColor.RED));
            spawnBloodStakes(p);
        }
    }

    private void spawnBloodStakes(Player p) {
        sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.7f,0.6f);
        for(int i=0;i<3;i++){final int si=i;double angle=(Math.PI/6)*(si-1);
            Vector dir=p.getLocation().getDirection().rotateAroundY(angle).normalize().multiply(0.5);
            new BukkitRunnable(){int t=0;Location pos=p.getLocation().clone().add(dir);@Override public void run(){
                t++;pos.add(dir);
                if(t%2==0)particles(pos,Particle.DRIPPING_WATER,2);
                pos.getNearbyLivingEntities(1.2).forEach(le->{ if(le!=p){le.damage(12,p);m1Bloodfouler(p,le);wither(le,1,60);} });
                if(t>=10)cancel();
            }}.runTaskTimer(plugin,(long)(si*2),1L);
        }
    }

    private void critHailbreaker(Player p) {
        int stage=hailStage.getOrDefault(p.getUniqueId(),1);
        switch(stage){
            case 1->{p.sendActionBar(Component.text("❄ Stage 1!",NamedTextColor.AQUA));new BukkitRunnable(){@Override public void run(){p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(14,p);slow(le,2,80);} });spawnIceExplosions(p,1);hailStage.put(p.getUniqueId(),2);p.sendActionBar(Component.text("❄ Stage 2 unlocked!",NamedTextColor.AQUA));}}.runTaskLater(plugin,12L);}
            case 2->{p.sendActionBar(Component.text("❄ Stage 2!",NamedTextColor.AQUA));new BukkitRunnable(){@Override public void run(){p.getNearbyEntities(3.5,3.5,3.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(18,p);slow(le,4,120);} });spawnIceExplosions(p,2);hailStage.put(p.getUniqueId(),3);p.sendActionBar(Component.text("❄ Stage 3 unlocked!",NamedTextColor.WHITE));}}.runTaskLater(plugin,10L);}
            case 3->{p.sendActionBar(Component.text("❄ Stage 3!",NamedTextColor.WHITE));Vector dash=p.getLocation().getDirection().normalize().multiply(2.5);dash.setY(0.5);p.setVelocity(dash);new BukkitRunnable(){int t=0;@Override public void run(){t++;p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(22,p);launch(le,2.0);slow(le,4,160);} });if(t>=10){spawnIceExplosions(p,3);hailStage.put(p.getUniqueId(),1);p.sendActionBar(Component.text("❄ Back to Stage 1",NamedTextColor.GRAY));cancel();}}}.runTaskTimer(plugin,5L,2L);}
        }
    }

    private void spawnIceExplosions(Player p, int stage) {
        int count=stage;double radius=stage*2.5;Random rng=new Random();
        for(int i=0;i<count;i++){final double ox=(rng.nextDouble()-0.5)*radius*2,oz=(rng.nextDouble()-0.5)*radius*2;
            new BukkitRunnable(){@Override public void run(){Location eLoc=p.getLocation().add(ox,0,oz);particles(eLoc,Particle.SNOWFLAKE,5);
                new BukkitRunnable(){@Override public void run(){eLoc.getWorld().createExplosion(eLoc,0f,false,false);particles(eLoc,Particle.SNOWFLAKE,5);sound(eLoc,Sound.BLOCK_GLASS_BREAK,0.8f,0.7f);eLoc.getNearbyLivingEntities(1.5).forEach(le->{ if(le!=p){le.damage(stage*6.0,p);slow(le,2,80);} });}}.runTaskLater(plugin,10L);
            }}.runTaskLater(plugin,(long)(i*3));
        }
    }

    private void critSoulthorn(Player p) {
        LivingEntity target=nearest(p,20);if(target==null){p.sendActionBar(Component.text("No target in range.",NamedTextColor.GRAY));return;}
        var pdc=target.getPersistentDataContainer();int marks=pdc.getOrDefault(DeepwokenWeapons.SOUL_MARKS_KEY,PersistentDataType.INTEGER,0);
        if(marks==0){particles(p.getLocation(),Particle.TOTEM_OF_UNDYING,6);target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,60,0,false,true,true));target.damage(8,p);p.sendActionBar(Component.text("✦ Glowing slash",NamedTextColor.LIGHT_PURPLE));}
        else if(marks<=2){p.sendActionBar(Component.text("✦✦ Reel & Detonate!",NamedTextColor.LIGHT_PURPLE));p.setVelocity(new Vector(0,1.5,0));final int fm=marks;
            new BukkitRunnable(){@Override public void run(){if(!target.isValid()||target.isDead())return;target.setVelocity(p.getLocation().subtract(target.getLocation()).toVector().normalize().multiply(3));target.damage(16.0*fm,p);pdc.remove(DeepwokenWeapons.SOUL_MARKS_KEY);particles(target.getLocation(),Particle.TOTEM_OF_UNDYING,8);sound(target.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.6f,1.5f);}}.runTaskLater(plugin,10L);}
        else{p.sendActionBar(Component.text("✦✦✦ TRUE HYPERARMOR!",NamedTextColor.GOLD));
            // Safe teleport above target
            Location above = getSafeTeleport(target.getLocation().add(0,3,0));
            if(above!=null) p.teleport(above);
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,40,4,false,true,true));
            for(int i=0;i<4;i++){new BukkitRunnable(){@Override public void run(){if(!target.isValid()||target.isDead())return;target.damage(14,p);p.setVelocity(new Vector(0,-1.5,0));particles(target.getLocation(),Particle.SWEEP_ATTACK,4);sound(target.getLocation(),Sound.ENTITY_PLAYER_ATTACK_CRIT,0.8f,1.2f);}}.runTaskLater(plugin,(long)(i*6+5));}
            pdc.remove(DeepwokenWeapons.SOUL_MARKS_KEY);}
    }

    private void critGalePale(Player p) {
        if(p.isSprinting()){p.sendActionBar(Component.text("🌬 Rush Sweep!",NamedTextColor.WHITE));p.setVelocity(p.getLocation().getDirection().normalize().multiply(3));
            new BukkitRunnable(){int t=0;@Override public void run(){t++;p.getNearbyEntities(2.5,2.5,2.5).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){le.damage(14,p);slow(le,0,60);le.addPotionEffect(new PotionEffect(PotionEffectType.POISON,80,0,false,true,true));} });if(t>=6)cancel();}}.runTaskTimer(plugin,0L,2L);}
        else{p.sendActionBar(Component.text("🌬 360° Sweep!",NamedTextColor.WHITE));
            p.getLocation().getNearbyLivingEntities(6).stream().limit(8).forEach(e->{ if(e!=p){e.damage(18,p);kb(p,e,2.5,0.5);slow(e,1,60);e.addPotionEffect(new PotionEffect(PotionEffectType.POISON,80,0,false,true,true));} });
            particles(p.getLocation(),Particle.CLOUD,6);sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.7f,1.6f);}
    }

    private void critColdPoint(Player p) {
        LivingEntity target=nearest(p,10);if(target==null){p.sendActionBar(Component.text("No target in range.",NamedTextColor.GRAY));return;}
        p.sendActionBar(Component.text("❄ Pierce Dash!",NamedTextColor.AQUA));
        particles(p.getLocation(),Particle.SNOWFLAKE,6);
        // Safe teleport through target
        Location safe = getSafeTeleportBehind(p, target, 2.0);
        if(safe!=null) p.teleport(safe);
        target.damage(20,p);slow(target,3,120);target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,40,0,false,true,true));
        particles(target.getLocation(),Particle.SNOWFLAKE,6);sound(target.getLocation(),Sound.BLOCK_GLASS_BREAK,1f,1.8f);
    }

    private void critIronRequiem(Player p) {
        int b=bullets.getOrDefault(p.getUniqueId(),0);
        if(p.isSprinting()){
            if(b<=0){p.sendActionBar(Component.text("🔫 No bullets! Use stand crit to reload.",NamedTextColor.RED));return;}
            p.sendActionBar(Component.text("🔫 Spinning Fire! ("+b+" bullets)",NamedTextColor.GRAY));
            int shots=Math.min(4,b);
            for(int i=0;i<shots;i++){final int si=i;new BukkitRunnable(){@Override public void run(){
                LivingEntity target=nearest(p,20);
                Vector dir=target!=null?target.getLocation().add(0,1,0).subtract(p.getLocation().add(0,1,0)).toVector().normalize().multiply(3):p.getLocation().getDirection().multiply(3);
                Arrow arrow=p.launchProjectile(Arrow.class,dir);
                arrow.setShooter(p); arrow.setDamage(10);
                particles(p.getLocation(),Particle.CRIT,4);sound(p.getLocation(),Sound.ENTITY_ARROW_SHOOT,0.8f,1.5f);
            }}.runTaskLater(plugin,(long)(si*4));}
            bullets.put(p.getUniqueId(),b-shots);
            p.sendActionBar(Component.text("🔫 Bullets: "+(b-shots)+"/6",NamedTextColor.GRAY));
        } else {
            // Explosive shot — reloads 1 bullet
            p.sendActionBar(Component.text("🔫 Explosive Shot! Reloading...",NamedTextColor.GRAY));
            LivingEntity target=nearest(p,20);
            if(target!=null){
                Vector dir=target.getLocation().add(0,1,0).subtract(p.getLocation().add(0,1,0)).toVector().normalize().multiply(3);
                Arrow arrow=p.launchProjectile(Arrow.class,dir);
                arrow.setShooter(p); arrow.setDamage(16);
                // Explosive on hit via task
                new BukkitRunnable(){int t=0;@Override public void run(){t++;
                    if(!arrow.isValid()||t>40){cancel();return;}
                    arrow.getNearbyEntities(1,1,1).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){
                        le.damage(16,p);arrow.getWorld().createExplosion(arrow.getLocation(),0f,false,false);particles(arrow.getLocation(),Particle.EXPLOSION_EMITTER,3);arrow.remove();cancel();
                    }});
                }}.runTaskTimer(plugin,0L,1L);
                particles(p.getLocation(),Particle.CRIT,4);sound(p.getLocation(),Sound.ENTITY_ARROW_SHOOT,0.8f,1.2f);
            }
            bullets.put(p.getUniqueId(),Math.min(6,b+1));
            p.sendActionBar(Component.text("🔫 Reloaded! Bullets: "+Math.min(6,b+1)+"/6",NamedTextColor.GRAY));
        }
    }

    private void critBloodfouler(Player p) {
        if(p.isSprinting()){p.sendActionBar(Component.text("🩸 Rush Sweep!",NamedTextColor.DARK_RED));p.setVelocity(p.getLocation().getDirection().normalize().multiply(3));
            new BukkitRunnable(){int t=0;@Override public void run(){t++;p.getNearbyEntities(2.5,2.5,2.5).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){le.damage(12,p);m1Bloodfouler(p,le);} });if(t>=5)cancel();}}.runTaskTimer(plugin,0L,2L);}
        else{p.sendActionBar(Component.text("🩸 Blood Infect!",NamedTextColor.DARK_RED));
            p.getLocation().getNearbyLivingEntities(6).stream().limit(8).forEach(e->{ if(e!=p){e.damage(12,p);m1Bloodfouler(p,e);} });
            particles(p.getLocation(),Particle.DRIPPING_LAVA,5);sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.6f,0.8f);}
    }

    private void critAmethyst(Player p) {
        int res=resonance.getOrDefault(p.getUniqueId(),0);
        if(p.isSneaking()){
            if(res<8){p.sendActionBar(Component.text("💎 Need more Resonance! ("+res+"/8)",NamedTextColor.LIGHT_PURPLE));return;}
            LivingEntity target=nearest(p,5);if(target==null){p.sendActionBar(Component.text("No target in range.",NamedTextColor.GRAY));return;}
            p.sendActionBar(Component.text("💎 Echo Fracture!",NamedTextColor.LIGHT_PURPLE));
            target.damage(16,p);particles(target.getLocation(),Particle.END_ROD,6);
            for(int i=1;i<=2;i++){final int ei=i;new BukkitRunnable(){@Override public void run(){if(!target.isValid()||target.isDead())return;target.damage(12,p);particles(target.getLocation(),Particle.TOTEM_OF_UNDYING,6);sound(target.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1f,1.2f);}}.runTaskLater(plugin,(long)(ei*25));}
            resonance.put(p.getUniqueId(),0);
        } else if(p.isSprinting()){
            p.sendActionBar(Component.text("💎 Prism Pierce!",NamedTextColor.LIGHT_PURPLE));
            Vector lunge=p.getLocation().getDirection().normalize().multiply(3);lunge.setY(0.1);p.setVelocity(lunge);
            particles(p.getLocation(),Particle.TOTEM_OF_UNDYING,6);sound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1f,0.8f);
            new BukkitRunnable(){int t=0;boolean hit=false;@Override public void run(){t++;
                if(!hit){p.getNearbyEntities(2,2,2).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){le.damage(18,p);particles(le.getLocation(),Particle.END_ROD,5);sound(le.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,0.8f,1.5f);hit=true;} });}
                if(t>=12)cancel();
            }}.runTaskTimer(plugin,0L,1L);
        } else {
            if(res<10){p.sendActionBar(Component.text("💎 Need full Resonance! ("+res+"/10)",NamedTextColor.LIGHT_PURPLE));return;}
            p.sendActionBar(Component.text("💎 SHATTERSTRIKE!",NamedTextColor.GOLD));
            particles(p.getLocation(),Particle.TOTEM_OF_UNDYING,8);sound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1f,0.5f);
            Vector dir=p.getLocation().getDirection().normalize();
            for(int i=0;i<5;i++){double angle=(Math.PI/8)*(i-2);Vector shard=dir.clone().rotateAroundY(angle).multiply(0.6);final Vector fs=shard;
                new BukkitRunnable(){int t=0;boolean hit=false;Location pos=p.getLocation().clone().add(fs);@Override public void run(){
                    t++;pos.add(fs);particles(pos,Particle.END_ROD,2);
                    if(!hit){pos.getNearbyLivingEntities(1.2).forEach(le->{ if(le!=p){le.damage(20,p);slow(le,1,40);particles(le.getLocation(),Particle.TOTEM_OF_UNDYING,6);hit=true;} });}
                    if(t>=8)cancel();
                }}.runTaskTimer(plugin,(long)(i*2),1L);
            }
            resonance.put(p.getUniqueId(),0);
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,80,0,false,true,true));
        }
    }

    private void critFlamewall(Player p) {
        int h=heat.getOrDefault(p.getUniqueId(),0);
        if(p.isSneaking()&&h>=8){ // lowered from 10 to 8
            p.sendActionBar(Component.text("🔥 SOLAR ERUPTION!",NamedTextColor.GOLD));
            particles(p.getLocation(),Particle.FLAME,8);sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,1f,0.4f);
            p.getLocation().getNearbyLivingEntities(10).stream().limit(10).forEach(le->{ if(le!=p){le.damage(28,p);fireDamage(le,p,3,200);kb(p,le,3,1);} }); // radius 10, more damage
            p.getWorld().createExplosion(p.getLocation(),0f,false,false);
            heat.put(p.getUniqueId(),0);flameDisabled.add(p.getUniqueId());
            new BukkitRunnable(){@Override public void run(){flameDisabled.remove(p.getUniqueId());p.sendActionBar(Component.text("🔥 Flamewall recharged!",NamedTextColor.GOLD));}}.runTaskLater(plugin,100L);
        } else if(p.isSprinting()){
            p.sendActionBar(Component.text("🔥 Blazing Surge!",NamedTextColor.GOLD));
            fireDamage(p,p,0,60); p.setFireTicks(60); // set player on fire cosmetically
            Vector dash=p.getLocation().getDirection().normalize().multiply(3);dash.setY(0.2);p.setVelocity(dash);
            new BukkitRunnable(){int t=0;@Override public void run(){t++;
                p.getNearbyEntities(3,3,3).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){le.damage(12,p);fireDamage(le,p,2,120);kb(p,le,1.5,0.3);le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,80,0,false,true,true));} });
                particles(p.getLocation(),Particle.FLAME,4);
                if(t>=15)cancel();
            }}.runTaskTimer(plugin,0L,1L);
        } else {
            if(h<5){p.sendActionBar(Component.text("🔥 Need more heat! ("+h+"/5)",NamedTextColor.GOLD));return;} // lowered to 5
            p.sendActionBar(Component.text("🔥 Inferno Barrier!",NamedTextColor.GOLD));
            sound(p.getLocation(),Sound.ENTITY_BLAZE_SHOOT,1f,0.5f);
            Vector right=p.getLocation().getDirection().rotateAroundY(Math.PI/2).normalize();
            for(int col=-3;col<=3;col++){for(int row=0;row<=3;row++){
                final int fc=col,fr=row;
                Location wallLoc=p.getLocation().add(p.getLocation().getDirection().normalize().multiply(2)).add(right.clone().multiply(fc)).add(0,fr,0);
                new BukkitRunnable(){@Override public void run(){
                    particles(wallLoc,Particle.FLAME,3);
                    wallLoc.getNearbyLivingEntities(1).forEach(le->{ if(le!=p){le.damage(3,p);fireDamage(le,p,1,80);le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,60,0,false,true,true));} });
                }}.runTaskLater(plugin,(long)((Math.abs(fc)+fr)*2));
            }}
            new BukkitRunnable(){int t=0;@Override public void run(){t++;if(t>60){cancel();return;}
                Vector right2=p.getLocation().getDirection().rotateAroundY(Math.PI/2).normalize();
                for(int c=-3;c<=3;c++){for(int r=0;r<=3;r++){
                    Location wl=p.getLocation().add(p.getLocation().getDirection().normalize().multiply(2)).add(right2.clone().multiply(c)).add(0,r,0);
                    particles(wl,Particle.FLAME,1);
                    wl.getNearbyLivingEntities(1).forEach(le->{ if(le!=p){le.damage(1,p);fireDamage(le,p,0,40);} });
                }}
            }}.runTaskTimer(plugin,10L,5L);
        }
    }

    private void critImBlue(Player p) {
        int r=rhythm.getOrDefault(p.getUniqueId(),0);
        if(p.isSneaking()){
            if(r<8){p.sendActionBar(Component.text("🌊 Need 8+ Rhythm! ("+r+"/10)",NamedTextColor.AQUA));return;} // lowered from 10 to 8
            p.sendActionBar(Component.text("🌊 Deep Echo!",NamedTextColor.AQUA));
            for(int i=0;i<3;i++){final int wi=i;new BukkitRunnable(){@Override public void run(){
                p.getLocation().getNearbyLivingEntities((wi+1)*4.0).stream().limit(6).forEach(le->{ if(le!=p){le.damage(12+(wi*4.0),p);kb(p,le,1.5,0.2);slow(le,1,40);} }); // more damage
                particles(p.getLocation(),Particle.DRIPPING_WATER,6);
                sound(p.getLocation(),Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,0.8f,0.8f+(wi*0.2f));
            }}.runTaskLater(plugin,(long)(wi*15));}
            rhythm.put(p.getUniqueId(),0);
        } else if(p.isSprinting()){
            p.sendActionBar(Component.text("🌊 Sonic Drift!",NamedTextColor.AQUA));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,10,4,false,true,true));
            Vector dash=p.getLocation().getDirection().normalize().multiply(3.5);dash.setY(0.15);p.setVelocity(dash);
            particles(p.getLocation(),Particle.DRIPPING_WATER,6);
            new BukkitRunnable(){int t=0;boolean hit=false;@Override public void run(){t++;
                if(!hit){p.getNearbyEntities(2,2,2).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){le.damage(14+(r*0.5),p);kb(p,le,1.5,0.3);hit=true;} });}
                if(t>=12)cancel();
            }}.runTaskTimer(plugin,0L,1L);
        } else {
            // Tidal Pulse now works at 5+ rhythm
            if(r<5){p.sendActionBar(Component.text("🌊 Need 5+ Rhythm! ("+r+"/10)",NamedTextColor.AQUA));return;}
            p.sendActionBar(Component.text("🌊 Tidal Pulse! (x"+r+")",NamedTextColor.AQUA));
            double dmgMult=1.0+(r*0.2);
            p.getLocation().getNearbyLivingEntities(7).stream().limit(8).forEach(le->{ if(le!=p){le.damage(14*dmgMult,p);kb(p,le,2.5,0.5);slow(le,1,60);} });
            for(int i=1;i<=4;i++){final int ri=i;new BukkitRunnable(){@Override public void run(){
                double wrad=ri*2.0;int pts=(int)(wrad*6);
                for(int j=0;j<pts;j++){double a=2*Math.PI*j/pts;Location wl=p.getLocation().add(Math.cos(a)*wrad,0.3,Math.sin(a)*wrad);particles(wl,Particle.DRIPPING_WATER,2);}
                sound(p.getLocation(),Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,0.6f,1.2f-(ri*0.1f));
            }}.runTaskLater(plugin,(long)(ri*4));}
        }
    }

    private void critPyreKeeper(Player p) {
        if (!p.isOnGround() && !p.isSprinting() && !p.isSneaking()) {
            p.sendActionBar(Component.text("🔥 Aerial Downchop!",NamedTextColor.GOLD));
            p.setVelocity(new Vector(0,0.5,0));particles(p.getLocation(),Particle.FLAME,6);
            new BukkitRunnable(){int t=0;@Override public void run(){t++;
                if(t==8){p.setVelocity(new Vector(0,-3,0));}
                if(t>=8){p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(14,p);fireDamage(le,p,2,80);kb(p,le,2,0.5);} });}
                if(p.isOnGround()&&t>=8){p.getLocation().getNearbyLivingEntities(4).forEach(le->{ if(le!=p){le.damage(10,p);fireDamage(le,p,2,100);kb(p,le,2.5,1.0);} });p.getWorld().createExplosion(p.getLocation(),0f,false,false);cancel();}
                if(t>=40)cancel();
            }}.runTaskTimer(plugin,0L,1L);
        } else if (p.isSprinting()&&p.isSneaking()) {
            p.sendActionBar(Component.text("🔥 Flip Slice!",NamedTextColor.GOLD));
            Vector dir=p.getLocation().getDirection().normalize().multiply(0.6);
            new BukkitRunnable(){int t=0;@Override public void run(){t++;p.setVelocity(new Vector(dir.getX(),0.3,dir.getZ()));p.getNearbyEntities(2,2,2).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(5,p);fireDamage(le,p,1,40);} });if(t>=12)cancel();}}.runTaskTimer(plugin,0L,1L);
        } else if (p.isSprinting()) {
            p.sendActionBar(Component.text("🔥 Rush Split!",NamedTextColor.GOLD));
            Vector lunge=p.getLocation().getDirection().normalize().multiply(3);lunge.setY(0.3);p.setVelocity(lunge);
            new BukkitRunnable(){int t=0;boolean hit=false;@Override public void run(){t++;
                if(!hit){p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(12,p);fireDamage(le,p,2,80);kb(p,le,2,0.5);hit=true;new BukkitRunnable(){@Override public void run(){le.damage(10,p);fireDamage(le,p,1,60);Vector split=p.getLocation().subtract(le.getLocation()).toVector().normalize().multiply(-2);split.setY(0.3);le.setVelocity(split);}}.runTaskLater(plugin,8L);} });}
                if(t>=8)cancel();
            }}.runTaskTimer(plugin,0L,2L);
        } else if (p.isSneaking()) {
            p.sendActionBar(Component.text("🔥 Swift Flip Slash!",NamedTextColor.GOLD));
            p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(10,p);fireDamage(le,p,1,60);} });
            new BukkitRunnable(){@Override public void run(){p.setVelocity(new Vector(0,0.8,0));new BukkitRunnable(){@Override public void run(){p.getNearbyEntities(3.5,3.5,3.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(8,p);fireDamage(le,p,1,40);} });p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,60,1,false,true,true));}}.runTaskLater(plugin,8L);}}.runTaskLater(plugin,5L);
        } else {
            p.sendActionBar(Component.text("🔥 Triple Slash!",NamedTextColor.GOLD));
            new BukkitRunnable(){@Override public void run(){p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(10,p);fireDamage(le,p,1,60);} });}}.runTaskLater(plugin,8L);
            new BukkitRunnable(){@Override public void run(){p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(8,p);fireDamage(le,p,1,40);} });}}.runTaskLater(plugin,16L);
            new BukkitRunnable(){@Override public void run(){p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){le.damage(8,p);fireDamage(le,p,1,40);} });p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,80,1,false,true,true));}}.runTaskLater(plugin,22L);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PASSIVE TASK
    // ═══════════════════════════════════════════════════════════════════════════
    private void startPassiveTask() {
        new BukkitRunnable(){@Override public void run(){
            for(Player p:Bukkit.getOnlinePlayers()){
                String wid=WeaponManager.getWeaponId(p.getInventory().getItemInMainHand());
                if(wid==null) continue;
                if((wid.equals("railblade")||wid.equals("hero_blade_wind"))&&!p.hasPotionEffect(PotionEffectType.SPEED))
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,40,1,false,true,true));
                if(wid.equals("im_blue")&&rhythm.getOrDefault(p.getUniqueId(),0)>=5){
                    if(!p.hasPotionEffect(PotionEffectType.SPEED)) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,40,0,false,true,true));
                    if(!p.hasPotionEffect(PotionEffectType.REGENERATION)) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,40,0,false,true,true));
                }
                if(wid.equals("nullscapes")){
                    p.getNearbyEntities(4,4,4).stream().limit(4).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){
                        if(!le.hasPotionEffect(PotionEffectType.WEAKNESS)) le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,30,0,false,true,false));
                        if(!le.hasPotionEffect(PotionEffectType.SLOWNESS)) le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,30,0,false,true,false));
                    }});
                }
                if(wid.equals("flamewall")){int h=heat.getOrDefault(p.getUniqueId(),0);if(h>0)heat.put(p.getUniqueId(),h-1);}
                if(wid.equals("orbit")){
                    long lastMoveTime=lastMove.getOrDefault(p.getUniqueId(),0L);
                    if(System.currentTimeMillis()-lastMoveTime>2000){int charge=Math.max(0,orbitCharge.getOrDefault(p.getUniqueId(),0)-1);orbitCharge.put(p.getUniqueId(),charge);}
                }
            }
        }}.runTaskTimer(plugin,0L,20L);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Deals fire damage that bypasses fire resistance by using direct damage */
    private void fireDamage(LivingEntity target, Player attacker, double extraDmg, int fireTicks) {
        if(extraDmg>0) target.damage(extraDmg, attacker);
        target.setFireTicks(fireTicks);
    }

    /** Finds a safe teleport location — checks that destination blocks are air */
    private Location getSafeTeleport(Location loc) {
        Location check = loc.clone();
        // Try the exact location first
        if(isSafe(check)) return check;
        // Try slightly above
        check.add(0,0.5,0);
        if(isSafe(check)) return check;
        // Try 1 block up
        check.add(0,0.5,0);
        if(isSafe(check)) return check;
        return null; // no safe spot found
    }

    /** Finds a safe location behind a target */
    private Location getSafeTeleportBehind(Player p, LivingEntity target, double dist) {
        Vector dir = target.getLocation().getDirection().normalize().multiply(dist);
        Location behind = target.getLocation().subtract(dir);
        behind.setDirection(target.getLocation().subtract(behind).toVector());
        return getSafeTeleport(behind);
    }

    private boolean isSafe(Location loc) {
        var block = loc.getBlock();
        var above = loc.clone().add(0,1,0).getBlock();
        return block.isPassable() && above.isPassable();
    }

    private void addResonance(Player p, int amt) {
        int r=Math.min(10,resonance.getOrDefault(p.getUniqueId(),0)+amt);
        resonance.put(p.getUniqueId(),r);
        if(r>=10) p.sendActionBar(Component.text("💎 FULL RESONANCE — Shatterstrike ready!",NamedTextColor.GOLD));
        else p.sendActionBar(Component.text("💎 Resonance: "+r+"/10",NamedTextColor.LIGHT_PURPLE));
    }

    private void bleed(LivingEntity t, Player p, int ticks){
        new BukkitRunnable(){int n=0;@Override public void run(){
            n++;
            if(!t.isValid()||t.isDead()||n>ticks){cancel();return;}
            // Check entity is not blocking before applying bleed damage
            t.damage(1.5,p);
            particles(t.getLocation(),Particle.DRIPPING_LAVA,2);
        }}.runTaskTimer(plugin,10L,10L);
    }

    private void startBloodDot(LivingEntity t, Player p){
        UUID entityId=t.getUniqueId();
        if(bloodDotActive.contains(entityId)) return;
        bloodDotActive.add(entityId);
        new BukkitRunnable(){int ticks=0;@Override public void run(){
            ticks++;
            if(!t.isValid()||t.isDead()){bloodDotActive.remove(entityId);cancel();return;}
            var pdc=t.getPersistentDataContainer();
            int s=pdc.getOrDefault(DeepwokenWeapons.BLOOD_POISON_KEY,PersistentDataType.INTEGER,0);
            if(s<=0){bloodDotActive.remove(entityId);pdc.remove(DeepwokenWeapons.BLOOD_POISON_KEY);cancel();return;}
            t.damage(s*0.4,p); // slightly nerfed from 0.5
            particles(t.getLocation().add(0,1,0),Particle.DRIPPING_WATER,2);
            if(ticks%5==0) pdc.set(DeepwokenWeapons.BLOOD_POISON_KEY,PersistentDataType.INTEGER,Math.max(0,s-1));
            if(ticks>100){bloodDotActive.remove(entityId);cancel();}
        }}.runTaskTimer(plugin,20L,20L);
    }

    private void startChainPull(Player p, LivingEntity target){
        Location anchor=target.getLocation().clone();
        new BukkitRunnable(){int t=0;@Override public void run(){
            t++;
            if(!p.isOnline()||t>60||!target.isValid()||target.isDead()){cryptChain.remove(p.getUniqueId());cancel();return;}
            if(target.getLocation().distanceSquared(anchor)>16){
                Vector pull=anchor.toVector().subtract(target.getLocation().toVector()).normalize().multiply(2);
                target.setVelocity(pull);
            }
        }}.runTaskTimer(plugin,0L,2L);
    }

    private void triggerChainBreak(Player p, LivingEntity t){
        cryptChain.remove(p.getUniqueId());
        for(int i=0;i<3;i++){new BukkitRunnable(){@Override public void run(){if(!t.isValid()||t.isDead())return;t.damage(8,p);wither(t,2,40);}}.runTaskLater(plugin,(long)(i*5));}
        p.sendActionBar(Component.text("💀 Chain Broken!",NamedTextColor.DARK_GREEN));
    }

    private boolean isPositiveEffect(PotionEffectType type){
        return type.equals(PotionEffectType.SPEED)||type.equals(PotionEffectType.STRENGTH)||
               type.equals(PotionEffectType.REGENERATION)||type.equals(PotionEffectType.RESISTANCE)||
               type.equals(PotionEffectType.FIRE_RESISTANCE)||type.equals(PotionEffectType.INVISIBILITY)||
               type.equals(PotionEffectType.JUMP_BOOST)||type.equals(PotionEffectType.HASTE)||
               type.equals(PotionEffectType.ABSORPTION)||type.equals(PotionEffectType.HEALTH_BOOST);
    }

    private void slow(LivingEntity e, int amp, int dur){ e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,dur,amp,false,true,true)); }
    private void wither(LivingEntity e, int amp, int dur){ e.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,dur,amp,false,true,true)); }
    private void launch(LivingEntity e, double pow){ e.setVelocity(e.getVelocity().add(new Vector(0,pow,0))); }
    private void particles(Location l, Particle par, int n){ if(n>0) l.getWorld().spawnParticle(par,l,Math.min(n,8),0.3,0.3,0.3,0.05); }
    private void sound(Location l, Sound s, float vol, float pitch){ l.getWorld().playSound(l,s,vol,pitch); }
    private void kb(Player src, LivingEntity tgt, double mul, double y){ Vector d=tgt.getLocation().subtract(src.getLocation()).toVector().normalize().multiply(mul);d.setY(y);tgt.setVelocity(d); }
    private LivingEntity nearest(Player p, double r){ return p.getNearbyEntities(r,r,r).stream().filter(e->e instanceof LivingEntity&&e!=p).map(e->(LivingEntity)e).min(Comparator.comparingDouble(e->e.getLocation().distanceSquared(p.getLocation()))).orElse(null); }
    private boolean checkCd(Player p, String wid, int ms){ long now=System.currentTimeMillis();long last=critCooldowns.computeIfAbsent(p.getUniqueId(),k->new HashMap<>()).getOrDefault(wid,0L);if(now-last>=ms){critCooldowns.get(p.getUniqueId()).put(wid,now);return true;}return false; }
    private long getRemainingCd(Player p, String wid, int ms){ long now=System.currentTimeMillis();return Math.max(0,ms-(now-critCooldowns.computeIfAbsent(p.getUniqueId(),k->new HashMap<>()).getOrDefault(wid,0L))); }
}
