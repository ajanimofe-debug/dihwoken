package com.deepwoken.weapons;

import com.deepwoken.weapons.listeners.WeaponListener;
import com.deepwoken.weapons.managers.BloodBarManager;
import com.deepwoken.weapons.managers.WeaponManager;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public class DeepwokenWeapons extends JavaPlugin {

    private static DeepwokenWeapons instance;
    private WeaponManager weaponManager;
    private BloodBarManager bloodBarManager;

    // --- Persistent Data Keys ---
    public static NamespacedKey WEAPON_ID_KEY;
    public static NamespacedKey SOUL_MARKS_KEY;       // Soul marks on a target entity
    public static NamespacedKey BULLETS_KEY;           // Iron Requiem bullets (on player)
    public static NamespacedKey BLOOD_POISON_KEY;      // Blood poison stacks (on player)
    public static NamespacedKey CRIT_COOLDOWN_KEY;     // Last crit timestamp (on player)

    @Override
    public void onEnable() {
        instance = this;

        // Init keys
        WEAPON_ID_KEY     = new NamespacedKey(this, "weapon_id");
        SOUL_MARKS_KEY    = new NamespacedKey(this, "soul_marks");
        BULLETS_KEY       = new NamespacedKey(this, "ir_bullets");
        BLOOD_POISON_KEY  = new NamespacedKey(this, "blood_poison");
        CRIT_COOLDOWN_KEY = new NamespacedKey(this, "crit_cooldown");

        weaponManager  = new WeaponManager(this);
        bloodBarManager = new BloodBarManager(this);

        // Register events
        getServer().getPluginManager().registerEvents(new WeaponListener(this), this);

        // Register commands
        var giveCmd = getCommand("giveweapon");
        var bbCmd   = getCommand("bloodbar");
        if (giveCmd != null) {
            var exec = new WeaponCommand(this);
            giveCmd.setExecutor(exec);
            giveCmd.setTabCompleter(exec);
        }
        if (bbCmd != null) {
            bbCmd.setExecutor((sender, cmd, label, args) -> {
                if (sender instanceof org.bukkit.entity.Player p) {
                    bloodBarManager.toggleBar(p);
                }
                return true;
            });
        }

        getLogger().info("DeepwokenWeapons loaded! " + weaponManager.getWeaponCount() + " weapons registered.");
    }

    @Override
    public void onDisable() {
        if (bloodBarManager != null) bloodBarManager.cleanup();
        getLogger().info("DeepwokenWeapons disabled.");
    }

    public static DeepwokenWeapons getInstance() { return instance; }
    public WeaponManager getWeaponManager()       { return weaponManager; }
    public BloodBarManager getBloodBarManager()   { return bloodBarManager; }
}
