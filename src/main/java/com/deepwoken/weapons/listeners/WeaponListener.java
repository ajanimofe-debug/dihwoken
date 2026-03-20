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
    private final Map<UUID, Integer>           bullets       = new HashMap<>();

    private static final int CRIT_CD_MS = 5000;

    public WeaponListener(DeepwokenWeapons plugin) {
        this.plugin = plugin;
        startPassiveTask();
    }

    @EventHandler public void onJoin(PlayerJoinEvent e)  { plugin.getBloodBarManager().showBar(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e)  { plugin.getBloodBarManager().hideBar(e.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity()  instanceof LivingEntity target)) return;
        String wid = WeaponManager.getWeaponId(player.getInventory().getItemInMainHand());
        if (wid == null) return;
        plugin.getBloodBarManager().onWeaponHit(player);
        switch (wid) {
            case "kyrsewinter"        -> { slow(target,3,80); particles(target.getLocation(), Particle.SNOWFLAKE, 20); sound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 1.5f); }
            case "stormseye"          -> { lightning(target.getLocation()); target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,60,0,false,true,true)); }
            case "hero_blade_fire"    -> target.setFireTicks(100);
            case "hero_blade_ice"     -> slow(target,2,60);
            case "hero_blade_wind"    -> launch(target,0.8);
            case "hero_blade_thunder" -> lightning(target.getLocation());
            case "boltcrusher"        -> { lightning(target.getLocation()); kb(player,target,2.0,0.5); target.getNearbyEntities(5,5,5).forEach(en->{ if(en instanceof LivingEntity le&&en!=player){lightning(le.getLocation());le.damage(4,player);} }); }
            case "hailbreaker"        -> { slow(target,2,80); target.getNearbyEntities(5,5,5).forEach(en->{ if(en instanceof LivingEntity le&&en!=player){slow(le,2,80);particles(le.getLocation(),Particle.SNOWFLAKE,10);} }); particles(target.getLocation(),Particle.SNOWFLAKE,25); }
            case "deepspindle"        -> { wither(target,1,100); target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,60,0,false,true,true)); particles(target.getLocation(),Particle.ASH,30); }
            case "red_death"          -> { target.setFireTicks(60); target.addPotionEffect(new PotionEffect(PotionEffectType.POISON,100,0,false,true,true)); }
            case "crypt_blade"        -> wither(target,1,100);
            case "soulthorn"          -> m1Soulthorn(player,target);
            case "bloodfouler"        -> m1Bloodfouler(player,target);
            case "yselys_pyre_keeper" -> target.setFireTicks(60);
            case "gale_pale"          -> { kb(player,target,2.5,0.3); slow(target,1,40); particles(target.getLocation(),Particle.CLOUD,12); }
            case "cold_point"         -> { slow(target,3,100); particles(target.getLocation(),Particle.SNOWFLAKE,15); sound(target.getLocation(),Sound.BLOCK_GLASS_BREAK,0.8f,1.6f); }
            case "iron_requiem"       -> m1IronRequiem(player,target,e);
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (!(e.getEntity().getKiller() instanceof Player p)) return;
        String wid = WeaponManager.getWeaponId(p.getInventory().getItemInMainHand());
        if (wid == null) return;
        if (wid.equals("red_death")) {
            p.setHealth(Math.min(p.getMaxHealth(), p.getHealth()+4));
            particles(p.getLocation().add(0,1,0), Particle.HEART, 6);
            p.sendActionBar(Component.text("❤ Lifesteal!", NamedTextColor.RED));
        } else if (wid.equals("crypt_blade")) {
            Zombie z = (Zombie) e.getEntity().getLocation().getWorld().spawnEntity(e.getEntity().getLocation(), EntityType.ZOMBIE);
            z.setCustomName("§cCrypt Servant"); z.setCustomNameVisible(true);
            new BukkitRunnable(){ @Override public void run(){ if(z.isValid())z.remove(); } }.runTaskLater(plugin,600L);
            p.sendActionBar(Component.text("💀 Crypt Servant summoned!", NamedTextColor.DARK_GREEN));
        }
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        var action = e.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        String wid = WeaponManager.getWeaponId(p.getInventory().getItemInMainHand());
        if (wid == null) return;
        e.setCancelled(true);
        if (!checkCooldown(p, wid, CRIT_CD_MS)) {
            p.sendActionBar(Component.text("⏱ Cooldown: " + getRemainingCooldown(p,wid)/1000.0 + "s", NamedTextColor.YELLOW));
            return;
        }
        switch (wid) {
            case "railblade"          -> critRailblade(p);
            case "yselys_pyre_keeper" -> critPyreKeeper(p);
            case "soulthorn"          -> critSoulthorn(p);
            case "gale_pale"          -> critGalePale(p);
            case "cold_point"         -> critColdPoint(p);
            case "iron_requiem"       -> critIronRequiem(p);
            case "bloodfouler"        -> critBloodfouler(p);
        }
    }

    @EventHandler
    public void onItemSwitch(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        p.removePotionEffect(PotionEffectType.SPEED);
        var newItem = p.getInventory().getItem(e.getNewSlot());
        String wid = WeaponManager.getWeaponId(newItem);
        if (wid != null && (wid.equals("railblade") || wid.equals("hero_blade_wind")))
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, true, true));
    }

    // ── M1s ──────────────────────────────────────────────────────────────────

    private void m1Soulthorn(Player p, LivingEntity target) {
        var pdc = target.getPersistentDataContainer();
        int marks = pdc.getOrDefault(DeepwokenWeapons.SOUL_MARKS_KEY, PersistentDataType.INTEGER, 0);
        if (marks < 3) { marks++; pdc.set(DeepwokenWeapons.SOUL_MARKS_KEY, PersistentDataType.INTEGER, marks); }
        Particle part = marks == 3 ? Particle.TOTEM_OF_UNDYING : Particle.END_ROD;
        particles(target.getLocation().add(0,1,0), part, marks * 7);
        p.sendActionBar(Component.text("✦".repeat(marks) + " Soul Mark: " + marks + "/3", NamedTextColor.LIGHT_PURPLE));
        if (marks == 3) sound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 2.0f);
    }

    private void m1Bloodfouler(Player p, LivingEntity target) {
        var pdc = target.getPersistentDataContainer();
        int stacks = Math.min(10, pdc.getOrDefault(DeepwokenWeapons.BLOOD_POISON_KEY, PersistentDataType.INTEGER, 0) + 1);
        pdc.set(DeepwokenWeapons.BLOOD_POISON_KEY, PersistentDataType.INTEGER, stacks);
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0, false, true, true));
        particles(target.getLocation(), Particle.DRIPPING_WATER, 8);
        final int fs = stacks;
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (!target.isValid() || target.isDead()) { cancel(); return; }
                int s = target.getPersistentDataContainer().getOrDefault(DeepwokenWeapons.BLOOD_POISON_KEY, PersistentDataType.INTEGER, 0);
                if (s <= 0) { cancel(); return; }
                target.damage(s * 0.5, p);
                if (t % 5 == 0) target.getPersistentDataContainer().set(DeepwokenWeapons.BLOOD_POISON_KEY, PersistentDataType.INTEGER, Math.max(0, s-1));
                if (t > 100) cancel();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void m1IronRequiem(Player p, LivingEntity target, EntityDamageByEntityEvent e) {
        int b = bullets.getOrDefault(p.getUniqueId(), 6);
        if (b > 0) {
            bullets.put(p.getUniqueId(), b-1);
            p.sendActionBar(Component.text("🔫 Bullets: "+(b-1)+"/6", NamedTextColor.GRAY));
            particles(target.getLocation(), Particle.CRIT, 8);
        } else {
            slow(target, 0, 40);
            e.setDamage(e.getDamage() * 0.7);
            p.sendActionBar(Component.text("⚡ Rod! No bullets — reload with crit", NamedTextColor.RED));
        }
    }

    // ── Crits ────────────────────────────────────────────────────────────────

    private void critRailblade(Player p) {
        Vector dir = p.getLocation().getDirection().normalize().multiply(2.5); dir.setY(0.3);
        p.setVelocity(dir); particles(p.getLocation(), Particle.CLOUD, 10);
        p.sendActionBar(Component.text("💨 Lunge!", NamedTextColor.GRAY));
    }

    private void critPyreKeeper(Player p) {
        if (!p.isOnGround()) {
            p.sendActionBar(Component.text("🔥 Ground Slam!", NamedTextColor.GOLD));
            p.setVelocity(new Vector(0,-3,0));
            new BukkitRunnable(){ int t=0; @Override public void run(){ t++;
                if(p.isOnGround()||t>40){
                    p.getWorld().createExplosion(p.getLocation(),0f,false,false);
                    particles(p.getLocation(),Particle.FLAME,30);
                    p.getLocation().getNearbyLivingEntities(4).forEach(e->{if(e!=p){e.damage(8,p);e.setFireTicks(80);e.setVelocity(p.getLocation().subtract(e.getLocation()).toVector().normalize().multiply(1.5));}});
                    sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,1f,0.7f); cancel();
                }
            }}.runTaskTimer(plugin,0L,1L);
        } else if (p.isSprinting()) {
            p.sendActionBar(Component.text("🔥 Lunge & Launch!", NamedTextColor.GOLD));
            Vector lunge = p.getLocation().getDirection().normalize().multiply(2); lunge.setY(0.4); p.setVelocity(lunge);
            new BukkitRunnable(){ int t=0; @Override public void run(){ t++;
                p.getNearbyEntities(2.5,2.5,2.5).forEach(e->{if(e instanceof LivingEntity le&&e!=p){le.damage(6,p);le.setFireTicks(60);le.setVelocity(new Vector(0,2.5,0));p.setVelocity(new Vector(0,1.2,0));particles(le.getLocation(),Particle.FLAME,20);}});
                if(t>=5)cancel();
            }}.runTaskTimer(plugin,0L,2L);
        } else if (p.isSneaking()) {
            p.sendActionBar(Component.text("🔥 Rapid Slash Combo!", NamedTextColor.GOLD));
            for(int i=0;i<5;i++){final int hi=i; new BukkitRunnable(){@Override public void run(){
                p.getNearbyEntities(2.5,2.5,2.5).forEach(e->{if(e instanceof LivingEntity le&&e!=p){le.damage(3,p);le.setFireTicks(20);particles(le.getLocation(),Particle.SWEEP_ATTACK,3);}});
                sound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,0.7f,1.3f+(hi*0.1f));
            }}.runTaskLater(plugin,(long)(i*4));}
        }
    }

    private void critSoulthorn(Player p) {
        LivingEntity target = nearest(p, 20);
        if (target == null) { p.sendActionBar(Component.text("No target in range.", NamedTextColor.GRAY)); return; }
        var pdc = target.getPersistentDataContainer();
        int marks = pdc.getOrDefault(DeepwokenWeapons.SOUL_MARKS_KEY, PersistentDataType.INTEGER, 0);
        if (marks == 0) {
            particles(p.getLocation(),Particle.TOTEM_OF_UNDYING,10); target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,60,0,false,true,true));
            p.sendActionBar(Component.text("✦ Glowing slash", NamedTextColor.LIGHT_PURPLE));
        } else if (marks <= 2) {
            p.sendActionBar(Component.text("✦✦ Reel & Detonate!", NamedTextColor.LIGHT_PURPLE));
            p.setVelocity(new Vector(0,1.5,0));
            final int fm=marks;
            new BukkitRunnable(){@Override public void run(){ target.setVelocity(p.getLocation().subtract(target.getLocation()).toVector().normalize().multiply(3)); target.damage(8.0*fm,p); pdc.remove(DeepwokenWeapons.SOUL_MARKS_KEY); particles(target.getLocation(),Particle.TOTEM_OF_UNDYING,25); sound(target.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.6f,1.5f); }}.runTaskLater(plugin,10L);
        } else {
            p.sendActionBar(Component.text("✦✦✦ TRUE HYPERARMOR — Downslash!", NamedTextColor.GOLD));
            p.teleport(target.getLocation().add(0,3,0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,30,4,false,true,true));
            for(int i=0;i<4;i++){new BukkitRunnable(){@Override public void run(){ target.damage(6,p); p.setVelocity(new Vector(0,-1.5,0)); particles(target.getLocation(),Particle.SWEEP_ATTACK,5); sound(target.getLocation(),Sound.ENTITY_PLAYER_ATTACK_CRIT,0.8f,1.2f); }}.runTaskLater(plugin,(long)(i*6+5));}
            pdc.remove(DeepwokenWeapons.SOUL_MARKS_KEY);
        }
    }

    private void critGalePale(Player p) {
        if (p.isSprinting()) {
            p.sendActionBar(Component.text("🌬 Rush Sweep!", NamedTextColor.WHITE));
            p.setVelocity(p.getLocation().getDirection().normalize().multiply(3));
            new BukkitRunnable(){ int t=0; @Override public void run(){ t++;
                p.getNearbyEntities(2.5,2.5,2.5).forEach(e->{if(e instanceof LivingEntity le&&e!=p){le.damage(10,p);slow(le,0,60);le.addPotionEffect(new PotionEffect(PotionEffectType.POISON,80,0,false,true,true));particles(le.getLocation(),Particle.CLOUD,8);}});
                if(t>=6)cancel();
            }}.runTaskTimer(plugin,0L,2L);
        } else {
            p.sendActionBar(Component.text("🌬 360° Sweep!", NamedTextColor.WHITE));
            p.getLocation().getNearbyLivingEntities(5).forEach(e->{ if(e!=p){ e.damage(12,p); kb(p,e,2,0.4); slow(e,1,60); e.addPotionEffect(new PotionEffect(PotionEffectType.POISON,80,0,false,true,true)); } });
            particles(p.getLocation(),Particle.CLOUD,40); sound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,0.7f,1.6f);
        }
    }

    private void critColdPoint(Player p) {
        LivingEntity target = nearest(p, 8);
        if (target == null) { p.sendActionBar(Component.text("No target in range.", NamedTextColor.GRAY)); return; }
        p.sendActionBar(Component.text("❄ Pierce Dash!", NamedTextColor.AQUA));
        particles(p.getLocation(),Particle.SNOWFLAKE,20);
        Vector dir = target.getLocation().subtract(p.getLocation()).toVector().normalize();
        p.teleport(target.getLocation().add(dir.multiply(2)));
        target.damage(10,p); slow(target,3,120); target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,40,0,false,true,true));
        particles(target.getLocation(),Particle.SNOWFLAKE,30); sound(target.getLocation(),Sound.BLOCK_GLASS_BREAK,1f,1.8f);
    }

    private void critIronRequiem(Player p) {
        int b = bullets.getOrDefault(p.getUniqueId(), 0);
        if (p.isSprinting()) {
            if (b <= 0) { p.sendActionBar(Component.text("🔫 No bullets!", NamedTextColor.RED)); return; }
            p.sendActionBar(Component.text("🔫 Spinning Fire!", NamedTextColor.GRAY));
            int shots = Math.min(4, b);
            for(int i=0;i<shots;i++){final int si=i; new BukkitRunnable(){@Override public void run(){
                double angle=(Math.PI*2/shots)*si;
                Snowball ball=p.launchProjectile(Snowball.class,new Vector(Math.cos(angle),0.1,Math.sin(angle)).multiply(2.5));
                handleProjectile(ball,p);
            }}.runTaskLater(plugin,(long)(si*3));}
            bullets.put(p.getUniqueId(), b-shots);
        } else {
            p.sendActionBar(Component.text("🔫 Explosive Shot!", NamedTextColor.GRAY));
            Snowball ball = p.launchProjectile(Snowball.class, p.getLocation().getDirection().multiply(3));
            handleProjectile(ball, p);
            bullets.put(p.getUniqueId(), Math.min(6, b+1));
        }
    }

    private void critBloodfouler(Player p) {
        if (p.isSprinting()) {
            p.sendActionBar(Component.text("🩸 Rush Sweep!", NamedTextColor.DARK_RED));
            p.setVelocity(p.getLocation().getDirection().normalize().multiply(3));
            new BukkitRunnable(){ int t=0; @Override public void run(){ t++;
                p.getNearbyEntities(2.5,2.5,2.5).forEach(e->{if(e instanceof LivingEntity le&&e!=p){le.damage(9,p);m1Bloodfouler(p,le);}});
                if(t>=5)cancel();
            }}.runTaskTimer(plugin,0L,2L);
        } else {
            p.sendActionBar(Component.text("🩸 Blood Infect!", NamedTextColor.DARK_RED));
            p.getLocation().getNearbyLivingEntities(5).forEach(e->{ if(e!=p){e.damage(8,p);m1Bloodfouler(p,e);} });
            particles(p.getLocation(),Particle.DRIPPING_LAVA,30); sound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,0.6f,0.8f);
        }
    }

    // ── Passive task ─────────────────────────────────────────────────────────

    private void startPassiveTask() {
        new BukkitRunnable(){@Override public void run(){
            for(Player p : Bukkit.getOnlinePlayers()){
                String wid = WeaponManager.getWeaponId(p.getInventory().getItemInMainHand());
                if(wid!=null&&(wid.equals("railblade")||wid.equals("hero_blade_wind"))&&!p.hasPotionEffect(PotionEffectType.SPEED))
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,40,1,false,true,true));
            }
        }}.runTaskTimer(plugin,0L,20L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void lightning(Location l){ l.getWorld().strikeLightningEffect(l); }
    private void slow(LivingEntity e,int amp,int dur){ e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,dur,amp,false,true,true)); }
    private void wither(LivingEntity e,int amp,int dur){ e.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,dur,amp,false,true,true)); }
    private void launch(LivingEntity e,double pow){ e.setVelocity(e.getVelocity().add(new Vector(0,pow,0))); }
    private void particles(Location l,Particle par,int n){ l.getWorld().spawnParticle(par,l,n,0.3,0.3,0.3,0.05); }
    private void sound(Location l,Sound s,float vol,float pitch){ l.getWorld().playSound(l,s,vol,pitch); }
    private void kb(Player src,LivingEntity tgt,double mul,double y){ Vector d=tgt.getLocation().subtract(src.getLocation()).toVector().normalize().multiply(mul); d.setY(y); tgt.setVelocity(d); }
    private LivingEntity nearest(Player p,double r){ return p.getNearbyEntities(r,r,r).stream().filter(e->e instanceof LivingEntity&&e!=p).map(e->(LivingEntity)e).min(Comparator.comparingDouble(e->e.getLocation().distanceSquared(p.getLocation()))).orElse(null); }
    private void handleProjectile(Snowball ball,Player owner){
        new BukkitRunnable(){ int t=0; @Override public void run(){ t++;
            if(!ball.isValid()||t>60){cancel();return;}
            ball.getNearbyEntities(0.5,0.5,0.5).forEach(e->{if(e instanceof LivingEntity le&&e!=owner){le.damage(12,owner);ball.getWorld().createExplosion(ball.getLocation(),0f,false,false);particles(ball.getLocation(),Particle.EXPLOSION_EMITTER,3);ball.remove();cancel();}});
        }}.runTaskTimer(plugin,0L,1L);
    }
    private boolean checkCooldown(Player p,String wid,int ms){
        long now=System.currentTimeMillis();
        long last=critCooldowns.computeIfAbsent(p.getUniqueId(),k->new HashMap<>()).getOrDefault(wid,0L);
        if(now-last>=ms){critCooldowns.get(p.getUniqueId()).put(wid,now);return true;}
        return false;
    }
    private long getRemainingCooldown(Player p,String wid){
        long now=System.currentTimeMillis();
        return Math.max(0,CRIT_CD_MS-(now-critCooldowns.computeIfAbsent(p.getUniqueId(),k->new HashMap<>()).getOrDefault(wid,0L)));
    }
}
