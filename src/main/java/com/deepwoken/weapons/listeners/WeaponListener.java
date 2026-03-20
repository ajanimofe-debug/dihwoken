package com.deepwoken.weapons.listeners;

import com.deepwoken.weapons.DeepwokenWeapons;
import com.deepwoken.weapons.managers.WeaponManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
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
    private final Map<UUID, Integer> bullets      = new HashMap<>();
    private final Map<UUID, Integer> hailStage    = new HashMap<>();
    private final Set<UUID>          darkRift     = new HashSet<>();
    private final Map<UUID, LivingEntity> cryptChain = new HashMap<>();

    // Amethyst resonance (0-10)
    private final Map<UUID, Integer> resonance    = new HashMap<>();
    // Flamewall heat (0-10), disabled flag
    private final Map<UUID, Integer> heat         = new HashMap<>();
    private final Set<UUID>          flameDisabled= new HashSet<>();
    // Im Blue rhythm (0-10), last hit time
    private final Map<UUID, Integer> rhythm       = new HashMap<>();
    private final Map<UUID, Long>    lastRhythm   = new HashMap<>();

    public WeaponListener(DeepwokenWeapons plugin) {
        this.plugin = plugin;
        startPassiveTask();
    }

    // ─── Join/Quit ────────────────────────────────────────────────────────────
    @EventHandler public void onJoin(PlayerJoinEvent e) { plugin.getBloodBarManager().showBar(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { plugin.getBloodBarManager().hideBar(e.getPlayer()); }

    // ─── M1 ──────────────────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity()  instanceof LivingEntity t)) return;
        String wid = WeaponManager.getWeaponId(p.getInventory().getItemInMainHand());
        if (wid == null) return;

        plugin.getBloodBarManager().onWeaponHit(p);

        switch (wid) {
            case "kyrsewinter" -> { slow(t,2,80); bleed(t,p,3); particles(t.getLocation(),Particle.SNOWFLAKE,20); sound(t.getLocation(),Sound.BLOCK_GLASS_BREAK,0.7f,1.5f); }
            case "stormseye"   -> { t.getWorld().strikeLightningEffect(t.getLocation()); t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,60,0,false,true,true)); }
            case "hero_blade_fire"    -> { t.setFireTicks(100); t.damage(1,p); }
            case "hero_blade_ice"     -> slow(t,2,60);
            case "hero_blade_wind"    -> launch(t,0.8);
            case "hero_blade_thunder" -> t.getWorld().strikeLightningEffect(t.getLocation());
            case "boltcrusher"  -> m1Boltcrusher(p,t);
            case "hailbreaker"  -> { slow(t,2,60); particles(t.getLocation(),Particle.SNOWFLAKE,10); }
            case "deepspindle"  -> { wither(t,1,100); t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,60,0,false,true,true)); particles(t.getLocation(),Particle.ASH,20); }
            case "red_death"    -> { t.setFireTicks(80); t.addPotionEffect(new PotionEffect(PotionEffectType.POISON,60,0,false,true,true)); m1Bloodfouler(p,t); }
            case "crypt_blade"  -> { wither(t,1,100); particles(t.getLocation(),Particle.ASH,10); if(cryptChain.containsKey(p.getUniqueId())&&cryptChain.get(p.getUniqueId()).equals(t)) triggerChainBreak(p,t); }
            case "soulthorn"    -> m1Soulthorn(p,t);
            case "bloodfouler"  -> m1Bloodfouler(p,t);
            case "yselys_pyre_keeper" -> { t.setFireTicks(120); t.damage(2,p); particles(t.getLocation(),Particle.FLAME,10); }
            case "gale_pale"    -> { kb(p,t,2.5,0.3); slow(t,1,40); particles(t.getLocation(),Particle.CLOUD,12); }
            case "cold_point"   -> { slow(t,3,100); particles(t.getLocation(),Particle.SNOWFLAKE,15); sound(t.getLocation(),Sound.BLOCK_GLASS_BREAK,0.8f,1.6f); }
            case "iron_requiem" -> m1IronRequiem(p,t,e);
            case "amethyst"     -> m1Amethyst(p,t);
            case "flamewall"    -> m1Flamewall(p,t);
            case "im_blue"      -> m1ImBlue(p,t);
            case "railblade"    -> {} // just normal damage
        }
    }

    // ─── Kill ─────────────────────────────────────────────────────────────────
    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (!(e.getEntity().getKiller() instanceof Player p)) return;
        String wid = WeaponManager.getWeaponId(p.getInventory().getItemInMainHand());
        if (wid == null) return;
        switch (wid) {
            case "red_death" -> { p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+4)); particles(p.getLocation().add(0,1,0),Particle.HEART,6); p.sendActionBar(Component.text("❤ Lifesteal!",NamedTextColor.RED)); }
            case "crypt_blade" -> {
                Zombie z=(Zombie)e.getEntity().getLocation().getWorld().spawnEntity(e.getEntity().getLocation(),EntityType.ZOMBIE);
                z.setCustomName("§cCrypt Servant"); z.setCustomNameVisible(true);
                new BukkitRunnable(){@Override public void run(){if(z.isValid())z.remove();}}.runTaskLater(plugin,600L);
                p.sendActionBar(Component.text("💀 Crypt Servant summoned!",NamedTextColor.DARK_GREEN));
            }
            case "amethyst" -> { addResonance(p,2); p.sendActionBar(Component.text("💎 Kill resonance!",NamedTextColor.LIGHT_PURPLE)); }
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

        // Flamewall disabled check
        if (wid.equals("flamewall") && flameDisabled.contains(p.getUniqueId())) {
            p.sendActionBar(Component.text("🔥 Flamewall overheated! Wait...",NamedTextColor.RED)); return;
        }

        int cdMs = switch(wid) { case "red_death" -> 8000; case "boltcrusher","crypt_blade" -> 10000; default -> 9000; };
        if (!checkCd(p,wid,cdMs)) { p.sendActionBar(Component.text("⏱ "+getRemainingCd(p,wid)/1000.0+"s",NamedTextColor.YELLOW)); return; }

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
        }
    }

    // ─── Item switch passives ─────────────────────────────────────────────────
    @EventHandler
    public void onSwitch(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        p.removePotionEffect(PotionEffectType.SPEED);
        var it = p.getInventory().getItem(e.getNewSlot());
        String wid = WeaponManager.getWeaponId(it);
        if (wid != null && (wid.equals("railblade")||wid.equals("hero_blade_wind")))
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,Integer.MAX_VALUE,1,false,true,true));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  M1 IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    private void m1Soulthorn(Player p, LivingEntity t) {
        var pdc = t.getPersistentDataContainer();
        int marks = pdc.getOrDefault(DeepwokenWeapons.SOUL_MARKS_KEY,PersistentDataType.INTEGER,0);
        if (marks<3) { marks++; pdc.set(DeepwokenWeapons.SOUL_MARKS_KEY,PersistentDataType.INTEGER,marks); }
        particles(t.getLocation().add(0,1,0), marks==3?Particle.TOTEM_OF_UNDYING:Particle.END_ROD, marks*7);
        p.sendActionBar(Component.text("✦".repeat(marks)+" Soul Mark: "+marks+"/3",NamedTextColor.LIGHT_PURPLE));
        if (marks==3) sound(t.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,0.5f,2f);
    }

    private void m1Bloodfouler(Player p, LivingEntity t) {
        var pdc = t.getPersistentDataContainer();
        int s = Math.min(10,pdc.getOrDefault(DeepwokenWeapons.BLOOD_POISON_KEY,PersistentDataType.INTEGER,0)+1);
        pdc.set(DeepwokenWeapons.BLOOD_POISON_KEY,PersistentDataType.INTEGER,s);
        t.addPotionEffect(new PotionEffect(PotionEffectType.POISON,40,0,false,true,true));
        particles(t.getLocation(),Particle.DRIPPING_WATER,8);
        startBloodDot(t,p);
    }

    private void m1Boltcrusher(Player p, LivingEntity t) {
        t.getNearbyEntities(5,5,5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p&&le!=t){ le.getWorld().strikeLightningEffect(le.getLocation()); le.damage(4,p); particles(le.getLocation(),Particle.ELECTRIC_SPARK,10); } });
        kb(p,t,2,0.5); particles(t.getLocation(),Particle.ELECTRIC_SPARK,20);
    }

    private void m1IronRequiem(Player p, LivingEntity t, EntityDamageByEntityEvent e) {
        int b=bullets.getOrDefault(p.getUniqueId(),6);
        if (b>0) { bullets.put(p.getUniqueId(),b-1); p.sendActionBar(Component.text("🔫 Bullets: "+(b-1)+"/6",NamedTextColor.GRAY)); particles(t.getLocation(),Particle.CRIT,8); }
        else { slow(t,0,40); e.setDamage(e.getDamage()*0.7); p.sendActionBar(Component.text("⚡ Rod! No bullets",NamedTextColor.RED)); }
    }

    private void m1Amethyst(Player p, LivingEntity t) {
        addResonance(p,1);
        particles(t.getLocation(),Particle.END_ROD,5);
    }

    private void m1Flamewall(Player p, LivingEntity t) {
        if (flameDisabled.contains(p.getUniqueId())) return;
        int h = Math.min(10, heat.getOrDefault(p.getUniqueId(),0)+1);
        heat.put(p.getUniqueId(),h);
        t.setFireTicks(40+(h*8));
        t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,60,0,false,true,true));
        particles(t.getLocation(),Particle.FLAME,h);
        p.sendActionBar(Component.text("🔥 Heat: "+h+"/10",h>=8?NamedTextColor.RED:NamedTextColor.GOLD));
        if (h>=10) {
            p.sendActionBar(Component.text("🔥 OVERHEATED! Use Shift+Crit for Solar Eruption!",NamedTextColor.RED));
        }
    }

    private void m1ImBlue(Player p, LivingEntity t) {
        long now = System.currentTimeMillis();
        long last = lastRhythm.getOrDefault(p.getUniqueId(),0L);
        int r = rhythm.getOrDefault(p.getUniqueId(),0);
        if (now-last<=2000) { r = Math.min(10,r+1); } else { r = 0; } // reset if too slow
        rhythm.put(p.getUniqueId(),r);
        lastRhythm.put(p.getUniqueId(),now);
        particles(t.getLocation(),Particle.DRIPPING_WATER,r);
        p.sendActionBar(Component.text("🌊 Rhythm: "+r+"/10",r>=8?NamedTextColor.AQUA:NamedTextColor.BLUE));
        // Speed passive from rhythm
        if (r>=5) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,30,0,false,true,true));
        if (r>=8) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,30,0,false,true,true));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CRIT IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    // ── Railblade ─────────────────────────────────────────────────────────────
    private void critRailblade(Player p) {
        if (!p.isOnGround()) {
            // Air: hover → diagonal downslash with burn
            p.sendActionBar(Component.text("💨 Aerial Downslash!",NamedTextColor.GRAY));
            p.setVelocity(new Vector(0,0.4,0));
            particles(p.getLocation(),Particle.CRIT,15);
            sound(p.getLocation(),Sound.ENTITY_PHANTOM_FLAP,0.8f,2f);
            new BukkitRunnable(){ int tick=0; @Override public void run(){
                tick++;
                if(tick==8){ Vector slash=p.getLocation().getDirection().normalize().multiply(0.8); slash.setY(-2.5); p.setVelocity(slash); }
                if(tick>=8){ p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){ le.damage(14,p); le.setFireTicks(100); kb(p,le,1.5,0.3); slow(le,1,40); particles(le.getLocation(),Particle.FLAME,20); sound(le.getLocation(),Sound.ENTITY_PLAYER_ATTACK_CRIT,1f,0.8f); } }); }
                if(tick>=20) cancel();
            }}.runTaskTimer(plugin,0L,1L);
        } else {
            // Ground: windup → knockdown → flaming blow
            p.sendActionBar(Component.text("💨 Railblade — Windup!",NamedTextColor.GRAY));
            particles(p.getLocation(),Particle.CRIT,10);
            sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.8f,0.6f);
            // Phase 1: knockdown only, no fire
            new BukkitRunnable(){@Override public void run(){
                p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){ le.damage(6,p); kb(p,le,2.5,0.5); slow(le,2,30); particles(le.getLocation(),Particle.SWEEP_ATTACK,5); sound(le.getLocation(),Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK,1f,1f); } });
            }}.runTaskLater(plugin,8L);
            // Phase 2: flaming blow
            new BukkitRunnable(){@Override public void run(){
                p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){ le.damage(12,p); le.setFireTicks(120); particles(le.getLocation(),Particle.FLAME,25); particles(le.getLocation(),Particle.LAVA,8); sound(le.getLocation(),Sound.ENTITY_BLAZE_SHOOT,0.8f,1.2f); } });
                p.sendActionBar(Component.text("💨🔥 Flaming Blow!",NamedTextColor.GOLD));
            }}.runTaskLater(plugin,18L);
        }
    }

    // ── Kyrsewinter ───────────────────────────────────────────────────────────
    private void critKyrsewinter(Player p) {
        if (p.isSprinting()) {
            // Running: telegraph → leap → explosive ice blast
            p.sendActionBar(Component.text("❄ Ice Leap!",NamedTextColor.AQUA));
            particles(p.getLocation(),Particle.SNOWFLAKE,20);
            sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.8f,0.5f);
            Vector leap=p.getLocation().getDirection().normalize().multiply(2.5); leap.setY(0.6); p.setVelocity(leap);
            new BukkitRunnable(){ int tick=0; @Override public void run(){
                tick++;
                p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){ le.damage(10,p); kb(p,le,1.5,0.4); bleed(le,p,3); particles(le.getLocation(),Particle.SNOWFLAKE,15); sound(le.getLocation(),Sound.BLOCK_GLASS_BREAK,1f,0.8f); } });
                if(tick==22){ // Ice blast
                    p.getLocation().getNearbyLivingEntities(4).forEach(le->{ if(le!=p){ le.damage(8,p); slow(le,3,100); kb(p,le,2,0.3); particles(le.getLocation(),Particle.SNOWFLAKE,25); } });
                    particles(p.getLocation(),Particle.SNOWFLAKE,40); sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.6f,1.8f);
                }
                if(tick>=25) cancel();
            }}.runTaskTimer(plugin,0L,1L);
        } else {
            // Standing: poke → ragdoll → ice blast under feet
            p.sendActionBar(Component.text("❄ Ice Poke!",NamedTextColor.AQUA));
            particles(p.getLocation(),Particle.SNOWFLAKE,10);
            sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.8f,0.7f);
            new BukkitRunnable(){@Override public void run(){
                p.getNearbyEntities(3.5,2,3.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){
                    le.damage(10,p); kb(p,le,2.5,0.5); bleed(le,p,3); particles(le.getLocation(),Particle.SNOWFLAKE,20); sound(le.getLocation(),Sound.BLOCK_GLASS_BREAK,0.8f,1.2f);
                    Location bl=le.getLocation().clone();
                    new BukkitRunnable(){@Override public void run(){
                        bl.getWorld().createExplosion(bl,0f,false,false);
                        bl.getNearbyLivingEntities(3).forEach(e2->{ if(e2!=p){ e2.damage(8,p); slow(e2,3,80); e2.setVelocity(new Vector(0,2,0)); particles(e2.getLocation(),Particle.SNOWFLAKE,30); } });
                        particles(bl,Particle.SNOWFLAKE,50); sound(bl,Sound.ENTITY_GENERIC_EXPLODE,0.7f,1.6f);
                    }}.runTaskLater(plugin,20L);
                }});
            }}.runTaskLater(plugin,10L);
        }
    }

    // ── Deepspindle ───────────────────────────────────────────────────────────
    private void critDeepspindle(Player p) {
        if (p.isSprinting()) {
            if (darkRift.contains(p.getUniqueId())) { p.sendActionBar(Component.text("☠ Dark Rift cooldown! (20s)",NamedTextColor.DARK_PURPLE)); return; }
            p.sendActionBar(Component.text("☠ Dark Rift!",NamedTextColor.DARK_PURPLE));
            p.damage(4); particles(p.getLocation(),Particle.ASH,20); sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.8f,0.5f);
            LivingEntity target=nearest(p,20);
            if (target!=null) {
                for(int i=0;i<2;i++){final int bi=i; new BukkitRunnable(){@Override public void run(){
                    particles(target.getLocation(),Particle.ASH,15); particles(target.getLocation(),Particle.TOTEM_OF_UNDYING,10);
                    target.damage(10,p); wither(target,2,80); sound(target.getLocation(),Sound.ENTITY_WITHER_AMBIENT,0.6f,1.5f);
                }}.runTaskLater(plugin,(long)(bi*8+5));}
            }
            darkRift.add(p.getUniqueId());
            new BukkitRunnable(){@Override public void run(){darkRift.remove(p.getUniqueId());}}.runTaskLater(plugin,400L);
        } else {
            LivingEntity target=nearest(p,5);
            if(target==null){p.sendActionBar(Component.text("No target in range.",NamedTextColor.GRAY));return;}
            p.sendActionBar(Component.text("☠ Void Thrust!",NamedTextColor.DARK_PURPLE));
            particles(p.getLocation(),Particle.ASH,10); sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.7f,1.2f);
            new BukkitRunnable(){@Override public void run(){
                target.damage(8,p); kb(p,target,1,0.2);
                particles(target.getLocation().add(0,1,0),Particle.TOTEM_OF_UNDYING,20);
                sound(target.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,0.5f,0.5f);
                for(int i=0;i<7;i++){final int bi=i; new BukkitRunnable(){@Override public void run(){
                    target.damage(3,p); wither(target,1,40); particles(target.getLocation(),Particle.ASH,8); particles(target.getLocation(),Particle.TOTEM_OF_UNDYING,5); sound(target.getLocation(),Sound.ENTITY_WITHER_AMBIENT,0.3f,1.8f);
                }}.runTaskLater(plugin,(long)(bi*5+5));}
            }}.runTaskLater(plugin,8L);
        }
    }

    // ── Boltcrusher ───────────────────────────────────────────────────────────
    private void critBoltcrusher(Player p) {
        if (p.isSprinting()) {
            p.sendActionBar(Component.text("⚡ Ground Slam!",NamedTextColor.YELLOW));
            sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.8f,0.4f); particles(p.getLocation(),Particle.ELECTRIC_SPARK,15);
            new BukkitRunnable(){@Override public void run(){
                p.getLocation().getNearbyLivingEntities(6).forEach(le->{ if(le!=p){ le.getWorld().strikeLightningEffect(le.getLocation()); le.damage(12,p); slow(le,2,80); kb(p,le,2,0.3); particles(le.getLocation(),Particle.ELECTRIC_SPARK,20); } });
                p.getWorld().createExplosion(p.getLocation(),0f,false,false); sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,1f,0.6f); particles(p.getLocation(),Particle.ELECTRIC_SPARK,40);
                p.sendActionBar(Component.text("⚡ SAPPED!",NamedTextColor.YELLOW));
            }}.runTaskLater(plugin,12L);
        } else {
            p.sendActionBar(Component.text("⚡ Clobber!",NamedTextColor.YELLOW));
            particles(p.getLocation(),Particle.ELECTRIC_SPARK,10); sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.8f,0.5f);
            new BukkitRunnable(){@Override public void run(){
                p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){ le.damage(14,p); slow(le,2,100); kb(p,le,3,0.6); le.getWorld().strikeLightningEffect(le.getLocation()); particles(le.getLocation(),Particle.ELECTRIC_SPARK,25); sound(le.getLocation(),Sound.ENTITY_PLAYER_ATTACK_CRIT,1f,0.7f); } });
                p.sendActionBar(Component.text("⚡ SAPPED!",NamedTextColor.YELLOW));
            }}.runTaskLater(plugin,14L);
        }
    }

    // ── Crypt Blade ───────────────────────────────────────────────────────────
    private void critCryptBlade(Player p) {
        if (p.isSprinting()) {
            p.sendActionBar(Component.text("💀 Cleave!",NamedTextColor.DARK_GREEN));
            sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.8f,0.6f);
            for(int i=0;i<2;i++){new BukkitRunnable(){@Override public void run(){
                p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){ le.damage(5,p); particles(le.getLocation(),Particle.ASH,8); sound(le.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.7f,1.2f); } });
            }}.runTaskLater(plugin,(long)(i*5));}
            new BukkitRunnable(){@Override public void run(){
                particles(p.getLocation(),Particle.TOTEM_OF_UNDYING,15); sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.7f,0.5f);
                p.getNearbyEntities(3.5,3.5,3.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){ le.damage(12,p); kb(p,le,3,0.8); wither(le,1,80); slow(le,2,60); particles(le.getLocation(),Particle.ASH,20); sound(le.getLocation(),Sound.ENTITY_PLAYER_ATTACK_CRIT,1f,0.6f); } });
            }}.runTaskLater(plugin,14L);
        } else {
            p.sendActionBar(Component.text("💀 Darkness Plunge!",NamedTextColor.DARK_GREEN));
            particles(p.getLocation(),Particle.ASH,20); sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.8f,0.4f);
            new BukkitRunnable(){@Override public void run(){
                p.getLocation().getNearbyLivingEntities(5).forEach(le->{ if(le!=p){
                    le.damage(8,p); wither(le,2,100); le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,60,0,false,true,true)); slow(le,1,60);
                    particles(le.getLocation(),Particle.ASH,15); particles(le.getLocation(),Particle.TOTEM_OF_UNDYING,10);
                    cryptChain.put(p.getUniqueId(),le); startChainPull(p,le);
                    p.sendActionBar(Component.text("💀 Chained!",NamedTextColor.DARK_GREEN));
                }});
                particles(p.getLocation(),Particle.ASH,40); sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.6f,0.5f);
            }}.runTaskLater(plugin,10L);
        }
    }

    // ── Red Death ─────────────────────────────────────────────────────────────
    private void critRedDeath(Player p) {
        if (p.isSprinting()) {
            p.sendActionBar(Component.text("🩸 Blood Thrust!",NamedTextColor.RED));
            Vector move=p.getLocation().getDirection().normalize().multiply(0.4);
            for(int i=0;i<5;i++){new BukkitRunnable(){@Override public void run(){
                p.setVelocity(move);
                p.getNearbyEntities(2,2,2).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){ le.damage(4,p); m1Bloodfouler(p,le); particles(le.getLocation(),Particle.DRIPPING_WATER,5); sound(le.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.6f,1.3f); } });
            }}.runTaskLater(plugin,(long)(i*3));}
            // Final blow + 3 stakes
            new BukkitRunnable(){@Override public void run(){
                p.getNearbyEntities(2.5,2.5,2.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){ le.damage(8,p); m1Bloodfouler(p,le); wither(le,1,60); } });
                spawnBloodStakes(p);
            }}.runTaskLater(plugin,20L);
        } else {
            // Standing: 3 blood stakes forward
            p.sendActionBar(Component.text("🩸 Blood Stakes!",NamedTextColor.RED));
            spawnBloodStakes(p);
        }
    }

    private void spawnBloodStakes(Player p) {
        sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.7f,0.6f);
        for(int i=0;i<3;i++){
            final int si=i; double angle=(Math.PI/6)*(si-1);
            Vector dir=p.getLocation().getDirection().rotateAroundY(angle).normalize().multiply(0.5);
            new BukkitRunnable(){ int t=0; Location pos=p.getLocation().clone().add(dir); @Override public void run(){
                t++; pos.add(dir);
                particles(pos,Particle.DRIPPING_WATER,5); particles(pos,Particle.ASH,3);
                pos.getNearbyLivingEntities(1.2).forEach(le->{ if(le!=p){ le.damage(8,p); m1Bloodfouler(p,le); wither(le,1,60); particles(le.getLocation(),Particle.TOTEM_OF_UNDYING,10); } });
                if(t>=10) cancel();
            }}.runTaskTimer(plugin,(long)(si*2),1L);
        }
    }

    // ── Hailbreaker ───────────────────────────────────────────────────────────
    private void critHailbreaker(Player p) {
        int stage=hailStage.getOrDefault(p.getUniqueId(),1);
        switch(stage){
            case 1 -> {
                p.sendActionBar(Component.text("❄ Stage 1 — Ice Slash!",NamedTextColor.AQUA));
                sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.8f,0.7f);
                new BukkitRunnable(){@Override public void run(){
                    p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){ le.damage(10,p); slow(le,2,80); particles(le.getLocation(),Particle.SNOWFLAKE,20); } });
                    spawnIceExplosions(p,1); hailStage.put(p.getUniqueId(),2);
                    p.sendActionBar(Component.text("❄ Stage 2 unlocked!",NamedTextColor.AQUA));
                }}.runTaskLater(plugin,12L);
            }
            case 2 -> {
                p.sendActionBar(Component.text("❄ Stage 2 — Freeze Slash!",NamedTextColor.AQUA));
                sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.8f,0.5f);
                new BukkitRunnable(){@Override public void run(){
                    p.getNearbyEntities(3.5,3.5,3.5).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){ le.damage(14,p); slow(le,4,120); particles(le.getLocation(),Particle.SNOWFLAKE,30); particles(le.getLocation(),Particle.ITEM_SNOWBALL,15); sound(le.getLocation(),Sound.BLOCK_GLASS_BREAK,1f,0.6f); } });
                    spawnIceExplosions(p,2); hailStage.put(p.getUniqueId(),3);
                    p.sendActionBar(Component.text("❄ Stage 3 unlocked!",NamedTextColor.WHITE));
                }}.runTaskLater(plugin,10L);
            }
            case 3 -> {
                p.sendActionBar(Component.text("❄ Stage 3 — Upward Slash!",NamedTextColor.WHITE));
                Vector dash=p.getLocation().getDirection().normalize().multiply(2.5); dash.setY(0.5); p.setVelocity(dash);
                sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.8f,0.3f);
                new BukkitRunnable(){ int t=0; @Override public void run(){
                    t++;
                    p.getNearbyEntities(3,3,3).forEach(en->{ if(en instanceof LivingEntity le&&en!=p){ le.damage(18,p); launch(le,2.0); slow(le,4,160); particles(le.getLocation(),Particle.SNOWFLAKE,40); particles(le.getLocation(),Particle.ITEM_SNOWBALL,20); sound(le.getLocation(),Sound.BLOCK_GLASS_BREAK,1f,0.4f); } });
                    if(t>=10){ spawnIceExplosions(p,3); hailStage.put(p.getUniqueId(),1); p.sendActionBar(Component.text("❄ Back to Stage 1",NamedTextColor.GRAY)); cancel(); }
                }}.runTaskTimer(plugin,5L,2L);
            }
        }
    }

    private void spawnIceExplosions(Player p, int stage) {
        int count=stage*2; double radius=stage*2.5;
        Random rng=new Random();
        for(int i=0;i<count;i++){
            final double ox=(rng.nextDouble()-0.5)*radius*2; final double oz=(rng.nextDouble()-0.5)*radius*2;
            new BukkitRunnable(){@Override public void run(){
                Location eLoc=p.getLocation().add(ox,0,oz);
                // Blue swirl telegraph
                particles(eLoc,Particle.SNOWFLAKE,15);
                new BukkitRunnable(){@Override public void run(){
                    eLoc.getWorld().createExplosion(eLoc,0f,false,false);
                    particles(eLoc,Particle.SNOWFLAKE,30); particles(eLoc,Particle.ITEM_SNOWBALL,10);
                    sound(eLoc,Sound.BLOCK_GLASS_BREAK,0.8f,0.7f);
                    eLoc.getNearbyLivingEntities(1.5).forEach(le->{ if(le!=p){ le.damage(stage*4.0,p); slow(le,2,80); } });
                }}.runTaskLater(plugin,10L);
            }}.runTaskLater(plugin,(long)(i*3));
        }
    }

    // ── Pyre Keeper ───────────────────────────────────────────────────────────
    private void critPyreKeeper(Player p) {
        if (!p.isOnGround()) {
            p.sendActionBar(Component.text("🔥 Ground Slam!",NamedTextColor.GOLD));
            p.setVelocity(new Vector(0,-3,0));
            new BukkitRunnable(){ int t=0; @Override public void run(){ t++;
                if(p.isOnGround()||t>40){ p.getWorld().createExplosion(p.getLocation(),0f,false,false); particles(p.getLocation(),Particle.FLAME,30);
                    p.getLocation().getNearbyLivingEntities(4).forEach(e->{ if(e!=p){e.damage(8,p);e.setFireTicks(80);e.setVelocity(p.getLocation().subtract(e.getLocation()).toVector().normalize().multiply(1.5));} });
                    sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,1f,0.7f); cancel(); }
            }}.runTaskTimer(plugin,0L,1L);
        } else if (p.isSprinting()) {
            p.sendActionBar(Component.text("🔥 Lunge & Launch!",NamedTextColor.GOLD));
            Vector lunge=p.getLocation().getDirection().normalize().multiply(2); lunge.setY(0.4); p.setVelocity(lunge);
            new BukkitRunnable(){ int t=0; @Override public void run(){ t++;
                p.getNearbyEntities(2.5,2.5,2.5).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){le.damage(6,p);le.setFireTicks(60);le.setVelocity(new Vector(0,2.5,0));p.setVelocity(new Vector(0,1.2,0));particles(le.getLocation(),Particle.FLAME,20);} });
                if(t>=5) cancel();
            }}.runTaskTimer(plugin,0L,2L);
        } else if (p.isSneaking()) {
            p.sendActionBar(Component.text("🔥 Rapid Slash Combo!",NamedTextColor.GOLD));
            for(int i=0;i<5;i++){final int hi=i; new BukkitRunnable(){@Override public void run(){
                p.getNearbyEntities(2.5,2.5,2.5).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){le.damage(3,p);le.setFireTicks(20);particles(le.getLocation(),Particle.SWEEP_ATTACK,3);} });
                sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.7f,1.3f+(hi*0.1f));
            }}.runTaskLater(plugin,(long)(i*4));}
        }
    }

    // ── Soulthorn ────────────────────────────────────────────────────────────
    private void critSoulthorn(Player p) {
        LivingEntity target=nearest(p,20);
        if(target==null){p.sendActionBar(Component.text("No target in range.",NamedTextColor.GRAY));return;}
        var pdc=target.getPersistentDataContainer();
        int marks=pdc.getOrDefault(DeepwokenWeapons.SOUL_MARKS_KEY,PersistentDataType.INTEGER,0);
        if(marks==0){
            particles(p.getLocation(),Particle.TOTEM_OF_UNDYING,10); target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,60,0,false,true,true));
            p.sendActionBar(Component.text("✦ Glowing slash",NamedTextColor.LIGHT_PURPLE));
        } else if(marks<=2){
            p.sendActionBar(Component.text("✦✦ Reel & Detonate!",NamedTextColor.LIGHT_PURPLE));
            p.setVelocity(new Vector(0,1.5,0)); final int fm=marks;
            new BukkitRunnable(){@Override public void run(){ target.setVelocity(p.getLocation().subtract(target.getLocation()).toVector().normalize().multiply(3)); target.damage(8.0*fm,p); pdc.remove(DeepwokenWeapons.SOUL_MARKS_KEY); particles(target.getLocation(),Particle.TOTEM_OF_UNDYING,25); sound(target.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.6f,1.5f); }}.runTaskLater(plugin,10L);
        } else {
            p.sendActionBar(Component.text("✦✦✦ TRUE HYPERARMOR — Downslash!",NamedTextColor.GOLD));
            p.teleport(target.getLocation().add(0,3,0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,30,4,false,true,true));
            for(int i=0;i<4;i++){new BukkitRunnable(){@Override public void run(){ target.damage(6,p); p.setVelocity(new Vector(0,-1.5,0)); particles(target.getLocation(),Particle.SWEEP_ATTACK,5); sound(target.getLocation(),Sound.ENTITY_PLAYER_ATTACK_CRIT,0.8f,1.2f); }}.runTaskLater(plugin,(long)(i*6+5));}
            pdc.remove(DeepwokenWeapons.SOUL_MARKS_KEY);
        }
    }

    // ── Gale Pale ─────────────────────────────────────────────────────────────
    private void critGalePale(Player p) {
        if(p.isSprinting()){
            p.sendActionBar(Component.text("🌬 Rush Sweep!",NamedTextColor.WHITE));
            p.setVelocity(p.getLocation().getDirection().normalize().multiply(3));
            new BukkitRunnable(){ int t=0; @Override public void run(){ t++;
                p.getNearbyEntities(2.5,2.5,2.5).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){le.damage(10,p);slow(le,0,60);le.addPotionEffect(new PotionEffect(PotionEffectType.POISON,80,0,false,true,true));particles(le.getLocation(),Particle.CLOUD,8);} });
                if(t>=6) cancel();
            }}.runTaskTimer(plugin,0L,2L);
        } else {
            p.sendActionBar(Component.text("🌬 360° Sweep!",NamedTextColor.WHITE));
            p.getLocation().getNearbyLivingEntities(5).forEach(e->{ if(e!=p){e.damage(12,p);kb(p,e,2,0.4);slow(e,1,60);e.addPotionEffect(new PotionEffect(PotionEffectType.POISON,80,0,false,true,true));} });
            particles(p.getLocation(),Particle.CLOUD,40); sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.7f,1.6f);
        }
    }

    // ── Cold Point ────────────────────────────────────────────────────────────
    private void critColdPoint(Player p) {
        LivingEntity target=nearest(p,8);
        if(target==null){p.sendActionBar(Component.text("No target in range.",NamedTextColor.GRAY));return;}
        p.sendActionBar(Component.text("❄ Pierce Dash!",NamedTextColor.AQUA));
        particles(p.getLocation(),Particle.SNOWFLAKE,20);
        Vector dir=target.getLocation().subtract(p.getLocation()).toVector().normalize();
        p.teleport(target.getLocation().add(dir.multiply(2)));
        target.damage(10,p); slow(target,3,120); target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,40,0,false,true,true));
        particles(target.getLocation(),Particle.SNOWFLAKE,30); sound(target.getLocation(),Sound.BLOCK_GLASS_BREAK,1f,1.8f);
    }

    // ── Iron Requiem ──────────────────────────────────────────────────────────
    private void critIronRequiem(Player p) {
        int b=bullets.getOrDefault(p.getUniqueId(),0);
        if(p.isSprinting()){
            if(b<=0){p.sendActionBar(Component.text("🔫 No bullets!",NamedTextColor.RED));return;}
            p.sendActionBar(Component.text("🔫 Spinning Fire!",NamedTextColor.GRAY));
            int shots=Math.min(4,b);
            for(int i=0;i<shots;i++){final int si=i; new BukkitRunnable(){@Override public void run(){
                double angle=(Math.PI*2/shots)*si;
                Snowball ball=p.launchProjectile(Snowball.class,new Vector(Math.cos(angle),0.1,Math.sin(angle)).multiply(2.5));
                handleProjectile(ball,p);
            }}.runTaskLater(plugin,(long)(si*3));}
            bullets.put(p.getUniqueId(),b-shots);
        } else {
            p.sendActionBar(Component.text("🔫 Explosive Shot!",NamedTextColor.GRAY));
            Snowball ball=p.launchProjectile(Snowball.class,p.getLocation().getDirection().multiply(3));
            handleProjectile(ball,p);
            bullets.put(p.getUniqueId(),Math.min(6,b+1));
        }
    }

    // ── Bloodfouler ───────────────────────────────────────────────────────────
    private void critBloodfouler(Player p) {
        if(p.isSprinting()){
            p.sendActionBar(Component.text("🩸 Rush Sweep!",NamedTextColor.DARK_RED));
            p.setVelocity(p.getLocation().getDirection().normalize().multiply(3));
            new BukkitRunnable(){ int t=0; @Override public void run(){ t++;
                p.getNearbyEntities(2.5,2.5,2.5).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){le.damage(9,p);m1Bloodfouler(p,le);} });
                if(t>=5) cancel();
            }}.runTaskTimer(plugin,0L,2L);
        } else {
            p.sendActionBar(Component.text("🩸 Blood Infect!",NamedTextColor.DARK_RED));
            p.getLocation().getNearbyLivingEntities(5).forEach(e->{ if(e!=p){e.damage(8,p);m1Bloodfouler(p,e);} });
            particles(p.getLocation(),Particle.DRIPPING_LAVA,30); sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.6f,0.8f);
        }
    }

    // ── Amethyst ──────────────────────────────────────────────────────────────
    private void critAmethyst(Player p) {
        int res=resonance.getOrDefault(p.getUniqueId(),0);
        if (p.isSneaking()) {
            // Echo Fracture — max combo required
            if(res<8){p.sendActionBar(Component.text("💎 Need more Resonance for Echo Fracture! ("+res+"/8)",NamedTextColor.LIGHT_PURPLE));return;}
            p.sendActionBar(Component.text("💎 Echo Fracture!",NamedTextColor.LIGHT_PURPLE));
            LivingEntity target=nearest(p,5);
            if(target==null){p.sendActionBar(Component.text("No target in range.",NamedTextColor.GRAY));return;}
            // Initial hit
            target.damage(10,p); particles(target.getLocation(),Particle.END_ROD,15);
            // Delayed echoes
            for(int i=1;i<=2;i++){final int ei=i; new BukkitRunnable(){@Override public void run(){
                particles(target.getLocation(),Particle.TOTEM_OF_UNDYING,20);
                target.damage(8,p); sound(target.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1f,1.2f);
            }}.runTaskLater(plugin,(long)(ei*25));}
            resonance.put(p.getUniqueId(),0);
        } else if(p.isSprinting()){
            // Prism Pierce — lunge through enemies
            p.sendActionBar(Component.text("💎 Prism Pierce!",NamedTextColor.LIGHT_PURPLE));
            Vector lunge=p.getLocation().getDirection().normalize().multiply(3); lunge.setY(0.1); p.setVelocity(lunge);
            particles(p.getLocation(),Particle.TOTEM_OF_UNDYING,15);
            sound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1f,0.8f);
            new BukkitRunnable(){ int t=0; @Override public void run(){ t++;
                p.getNearbyEntities(2,2,2).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){ le.damage(12,p); particles(le.getLocation(),Particle.END_ROD,10); sound(le.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,0.8f,1.5f); } });
                if(t>=12) cancel();
            }}.runTaskTimer(plugin,0L,1L);
        } else {
            // Shatterstrike — requires full resonance
            if(res<10){p.sendActionBar(Component.text("💎 Need full Resonance for Shatterstrike! ("+res+"/10)",NamedTextColor.LIGHT_PURPLE));return;}
            p.sendActionBar(Component.text("💎 SHATTERSTRIKE!",NamedTextColor.GOLD));
            particles(p.getLocation(),Particle.TOTEM_OF_UNDYING,30);
            sound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1f,0.5f);
            // Cone of crystal shards forward
            Vector dir=p.getLocation().getDirection().normalize();
            for(int i=0;i<5;i++){
                double angle=(Math.PI/8)*(i-2);
                Vector shard=dir.clone().rotateAroundY(angle).multiply(0.6);
                final Vector fs=shard;
                new BukkitRunnable(){ int t=0; Location pos=p.getLocation().clone().add(fs); @Override public void run(){
                    t++; pos.add(fs); particles(pos,Particle.END_ROD,5); particles(pos,Particle.TOTEM_OF_UNDYING,3);
                    pos.getNearbyLivingEntities(1.2).forEach(le->{ if(le!=p){ le.damage(14,p); slow(le,1,40); particles(le.getLocation(),Particle.TOTEM_OF_UNDYING,15); sound(le.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,0.9f,1.8f); } });
                    if(t>=8) cancel();
                }}.runTaskTimer(plugin,(long)(i*2),1L);
            }
            resonance.put(p.getUniqueId(),0);
            // Amethyst Memory passive — brief Strength
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,60,0,false,true,true));
        }
    }

    // ── Flamewall ─────────────────────────────────────────────────────────────
    private void critFlamewall(Player p) {
        int h=heat.getOrDefault(p.getUniqueId(),0);
        if(p.isSneaking()&&h>=10){
            // Solar Eruption — overheat explosion
            p.sendActionBar(Component.text("🔥 SOLAR ERUPTION!",NamedTextColor.GOLD));
            particles(p.getLocation(),Particle.FLAME,50); particles(p.getLocation(),Particle.LAVA,30);
            sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,1f,0.4f);
            p.getLocation().getNearbyLivingEntities(7).forEach(le->{ if(le!=p){ le.damage(20,p); le.setFireTicks(200); kb(p,le,3,1); particles(le.getLocation(),Particle.FLAME,25); } });
            p.getWorld().createExplosion(p.getLocation(),0f,false,false);
            // Disable weapon 5 seconds
            heat.put(p.getUniqueId(),0);
            flameDisabled.add(p.getUniqueId());
            new BukkitRunnable(){@Override public void run(){ flameDisabled.remove(p.getUniqueId()); p.sendActionBar(Component.text("🔥 Flamewall recharged!",NamedTextColor.GOLD)); }}.runTaskLater(plugin,100L);
        } else if(p.isSprinting()){
            // Blazing Surge — fire dash + trail
            p.sendActionBar(Component.text("🔥 Blazing Surge!",NamedTextColor.GOLD));
            p.setFireTicks(60);
            Vector dash=p.getLocation().getDirection().normalize().multiply(3); dash.setY(0.2); p.setVelocity(dash);
            new BukkitRunnable(){ int t=0; @Override public void run(){ t++;
                Location trail=p.getLocation().clone();
                particles(trail,Particle.FLAME,8); particles(trail,Particle.LAVA,3);
                p.getNearbyEntities(2,2,2).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){ le.damage(8,p); le.setFireTicks(120); kb(p,le,1.5,0.3); le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,80,0,false,true,true)); } });
                if(t>=15) cancel();
            }}.runTaskTimer(plugin,0L,1L);
        } else {
            // Inferno Barrier — wall of fire
            if(h<8){p.sendActionBar(Component.text("🔥 Need more heat! ("+h+"/8)",NamedTextColor.GOLD));return;}
            p.sendActionBar(Component.text("🔥 Inferno Barrier!",NamedTextColor.GOLD));
            sound(p.getLocation(),Sound.ENTITY_BLAZE_SHOOT,1f,0.5f);
            Vector right=p.getLocation().getDirection().rotateAroundY(Math.PI/2).normalize();
            for(int col=-3;col<=3;col++){for(int row=0;row<=3;row++){
                final int fc=col,fr=row;
                Location wallLoc=p.getLocation().add(p.getLocation().getDirection().normalize().multiply(2)).add(right.clone().multiply(fc)).add(0,fr,0);
                new BukkitRunnable(){@Override public void run(){
                    particles(wallLoc,Particle.FLAME,5); particles(wallLoc,Particle.LAVA,2);
                    wallLoc.getNearbyLivingEntities(1).forEach(le->{ if(le!=p){ le.damage(2,p); le.setFireTicks(80); le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,60,0,false,true,true)); } });
                }}.runTaskLater(plugin,(long)((Math.abs(fc)+fr)*2));
            }}
            // Wall lasts 3 seconds
            new BukkitRunnable(){ int t=0; @Override public void run(){ t++;
                if(t>60){cancel();return;}
                Vector right2=p.getLocation().getDirection().rotateAroundY(Math.PI/2).normalize();
                for(int c=-3;c<=3;c++){for(int r=0;r<=3;r++){
                    Location wl=p.getLocation().add(p.getLocation().getDirection().normalize().multiply(2)).add(right2.clone().multiply(c)).add(0,r,0);
                    particles(wl,Particle.FLAME,2);
                    wl.getNearbyLivingEntities(1).forEach(le->{ if(le!=p){ le.damage(1,p); le.setFireTicks(40); } });
                }}
            }}.runTaskTimer(plugin,10L,5L);
        }
    }

    // ── Im Blue ───────────────────────────────────────────────────────────────
    private void critImBlue(Player p) {
        int r=rhythm.getOrDefault(p.getUniqueId(),0);
        if(p.isSneaking()){
            // Deep Echo — max rhythm required
            if(r<10){p.sendActionBar(Component.text("🌊 Need max Rhythm! ("+r+"/10)",NamedTextColor.AQUA));return;}
            p.sendActionBar(Component.text("🌊 Deep Echo!",NamedTextColor.AQUA));
            for(int i=0;i<3;i++){final int wi=i; new BukkitRunnable(){@Override public void run(){
                double radius=(wi+1)*4.0;
                p.getLocation().getNearbyLivingEntities(radius).forEach(le->{ if(le!=p){ le.damage(6+(wi*2.0),p); kb(p,le,1.5,0.2); slow(le,1,40); particles(le.getLocation(),Particle.DRIPPING_WATER,10); } });
                particles(p.getLocation(),Particle.DRIPPING_WATER,30+(wi*10));
                sound(p.getLocation(),Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,0.8f,0.8f+(wi*0.2f));
                p.sendActionBar(Component.text("🌊 Wave "+(wi+1)+"!",NamedTextColor.AQUA));
            }}.runTaskLater(plugin,(long)(wi*15));}
            rhythm.put(p.getUniqueId(),0);
        } else if(p.isSprinting()){
            // Sonic Drift — phase dash + brief invincibility
            p.sendActionBar(Component.text("🌊 Sonic Drift!",NamedTextColor.AQUA));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,10,4,false,true,true)); // brief invincibility
            Vector dash=p.getLocation().getDirection().normalize().multiply(3.5); dash.setY(0.15); p.setVelocity(dash);
            particles(p.getLocation(),Particle.DRIPPING_WATER,25); sound(p.getLocation(),Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,0.8f,1.5f);
            new BukkitRunnable(){ int t=0; @Override public void run(){ t++;
                p.getNearbyEntities(2,2,2).forEach(e->{ if(e instanceof LivingEntity le&&e!=p){ le.damage(8+(r*0.5),p); kb(p,le,1.5,0.3); particles(le.getLocation(),Particle.DRIPPING_WATER,10); sound(le.getLocation(),Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,0.6f,1.8f); } });
                if(t>=12) cancel();
            }}.runTaskTimer(plugin,0L,1L);
        } else {
            // Tidal Pulse — expanding knockback wave, scales with rhythm
            p.sendActionBar(Component.text("🌊 Tidal Pulse! (x"+r+")",NamedTextColor.AQUA));
            double dmgMult=1.0+(r*0.15);
            p.getLocation().getNearbyLivingEntities(6).forEach(le->{ if(le!=p){ le.damage(10*dmgMult,p); kb(p,le,2.5,0.5); slow(le,1,60); particles(le.getLocation(),Particle.DRIPPING_WATER,15); } });
            // Expanding wave particles
            for(int i=1;i<=4;i++){final int ri=i; new BukkitRunnable(){@Override public void run(){
                double wrad=ri*2.0; int pts=(int)(wrad*8);
                for(int j=0;j<pts;j++){ double a=2*Math.PI*j/pts; Location wl=p.getLocation().add(Math.cos(a)*wrad,0.3,Math.sin(a)*wrad); particles(wl,Particle.DRIPPING_WATER,3); }
                sound(p.getLocation(),Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,0.6f,1.2f-(ri*0.1f));
            }}.runTaskLater(plugin,(long)(ri*4));}
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
                // Speed passive
                if((wid.equals("railblade")||wid.equals("hero_blade_wind"))&&!p.hasPotionEffect(PotionEffectType.SPEED))
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,40,1,false,true,true));
                // Im Blue rhythm regen
                if(wid.equals("im_blue")&&rhythm.getOrDefault(p.getUniqueId(),0)>=5){
                    if(!p.hasPotionEffect(PotionEffectType.SPEED)) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,40,0,false,true,true));
                    if(!p.hasPotionEffect(PotionEffectType.REGENERATION)) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,40,0,false,true,true));
                }
            }
        }}.runTaskTimer(plugin,0L,20L);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void addResonance(Player p, int amt) {
        int r=Math.min(10,resonance.getOrDefault(p.getUniqueId(),0)+amt);
        resonance.put(p.getUniqueId(),r);
        if(r>=10) p.sendActionBar(Component.text("💎 FULL RESONANCE — Shatterstrike ready!",NamedTextColor.GOLD));
        else p.sendActionBar(Component.text("💎 Resonance: "+r+"/10",NamedTextColor.LIGHT_PURPLE));
    }

    private void bleed(LivingEntity t, Player p, int ticks){
        new BukkitRunnable(){ int n=0; @Override public void run(){ n++; if(!t.isValid()||t.isDead()||n>ticks){cancel();return;} t.damage(1,p); particles(t.getLocation(),Particle.DRIPPING_LAVA,3); }}.runTaskTimer(plugin,10L,10L);
    }

    private void startBloodDot(LivingEntity t, Player p){
        new BukkitRunnable(){ int ticks=0; @Override public void run(){
            ticks++;
            if(!t.isValid()||t.isDead()){cancel();return;}
            var pdc=t.getPersistentDataContainer();
            int s=pdc.getOrDefault(DeepwokenWeapons.BLOOD_POISON_KEY,PersistentDataType.INTEGER,0);
            if(s<=0){cancel();return;}
            t.damage(s*0.5,p); particles(t.getLocation().add(0,1,0),Particle.DRIPPING_WATER,3);
            if(ticks%5==0) pdc.set(DeepwokenWeapons.BLOOD_POISON_KEY,PersistentDataType.INTEGER,Math.max(0,s-1));
            if(ticks>100) cancel();
        }}.runTaskTimer(plugin,20L,20L);
    }

    private void startChainPull(Player p, LivingEntity target){
        Location anchor=target.getLocation().clone();
        new BukkitRunnable(){ int t=0; @Override public void run(){
            t++;
            if(t>60||!target.isValid()||target.isDead()){cryptChain.remove(p.getUniqueId());cancel();return;}
            if(target.getLocation().distanceSquared(anchor)>16){ Vector pull=anchor.toVector().subtract(target.getLocation().toVector()).normalize().multiply(2); target.setVelocity(pull); particles(target.getLocation(),Particle.ASH,5); }
        }}.runTaskTimer(plugin,0L,2L);
    }

    private void triggerChainBreak(Player p, LivingEntity t){
        cryptChain.remove(p.getUniqueId());
        for(int i=0;i<3;i++){new BukkitRunnable(){@Override public void run(){ t.damage(5,p); wither(t,2,40); particles(t.getLocation(),Particle.TOTEM_OF_UNDYING,10); }}.runTaskLater(plugin,(long)(i*5));}
        p.sendActionBar(Component.text("💀 Chain Broken!",NamedTextColor.DARK_GREEN));
        sound(t.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.8f,1.5f);
    }

    private void handleProjectile(Snowball ball, Player owner){
        new BukkitRunnable(){ int t=0; @Override public void run(){ t++;
            if(!ball.isValid()||t>60){cancel();return;}
            ball.getNearbyEntities(0.5,0.5,0.5).forEach(e->{ if(e instanceof LivingEntity le&&e!=owner){ le.damage(12,owner); ball.getWorld().createExplosion(ball.getLocation(),0f,false,false); particles(ball.getLocation(),Particle.EXPLOSION_EMITTER,3); ball.remove(); cancel(); } });
        }}.runTaskTimer(plugin,0L,1L);
    }

    private void spawnIceExplosions(Player p, int stage){ /* handled inline in critHailbreaker */ }

    private void lightning(Location l){ l.getWorld().strikeLightningEffect(l); }
    private void slow(LivingEntity e, int amp, int dur){ e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,dur,amp,false,true,true)); }
    private void wither(LivingEntity e, int amp, int dur){ e.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,dur,amp,false,true,true)); }
    private void launch(LivingEntity e, double pow){ e.setVelocity(e.getVelocity().add(new Vector(0,pow,0))); }
    private void particles(Location l, Particle par, int n){ l.getWorld().spawnParticle(par,l,n,0.3,0.3,0.3,0.05); }
    private void sound(Location l, Sound s, float vol, float pitch){ l.getWorld().playSound(l,s,vol,pitch); }
    private void kb(Player src, LivingEntity tgt, double mul, double y){ Vector d=tgt.getLocation().subtract(src.getLocation()).toVector().normalize().multiply(mul); d.setY(y); tgt.setVelocity(d); }
    private LivingEntity nearest(Player p, double r){ return p.getNearbyEntities(r,r,r).stream().filter(e->e instanceof LivingEntity&&e!=p).map(e->(LivingEntity)e).min(Comparator.comparingDouble(e->e.getLocation().distanceSquared(p.getLocation()))).orElse(null); }
    private boolean checkCd(Player p, String wid, int ms){ long now=System.currentTimeMillis(); long last=critCooldowns.computeIfAbsent(p.getUniqueId(),k->new HashMap<>()).getOrDefault(wid,0L); if(now-last>=ms){critCooldowns.get(p.getUniqueId()).put(wid,now);return true;} return false; }
    private long getRemainingCd(Player p, String wid){ long now=System.currentTimeMillis(); return Math.max(0,9000-(now-critCooldowns.computeIfAbsent(p.getUniqueId(),k->new HashMap<>()).getOrDefault(wid,0L))); }
}
