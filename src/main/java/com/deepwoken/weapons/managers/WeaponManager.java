package com.deepwoken.weapons.managers;

import com.deepwoken.weapons.DeepwokenWeapons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class WeaponManager {

    private final DeepwokenWeapons plugin;
    private final Map<String, ItemStack> weapons = new LinkedHashMap<>();

    public WeaponManager(DeepwokenWeapons plugin) {
        this.plugin = plugin;
        registerAll();
    }

    private void registerAll() {
        // ── Deepwoken Legendaries ─────────────────────────────────────────────
        add("kyrsewinter",        build("kyrsewinter",        Material.DIAMOND_SWORD,    "❄ Kyrsewinter",           NamedTextColor.AQUA,        List.of("Bleeds & freezes on M1","Stand Crit: Ice poke → blast","Sprint Crit: Leap → ice explosion"),                    9.0, -2.2));
        add("stormseye",          build("stormseye",          Material.NETHERITE_SWORD,  "⚡ Stormseye",            NamedTextColor.YELLOW,      List.of("Calls lightning on every hit","Blinds struck targets"),                                                          10.0,-2.3));
        add("hero_blade_fire",    build("hero_blade_fire",    Material.NETHERITE_SWORD,  "🔥 Hero Blade — Flame",   NamedTextColor.RED,         List.of("Sets enemies ablaze on hit"),                                                                                    8.0, -2.0));
        add("hero_blade_ice",     build("hero_blade_ice",     Material.DIAMOND_SWORD,    "❄ Hero Blade — Frost",    NamedTextColor.AQUA,        List.of("Slows enemies on hit"),                                                                                          8.0, -2.0));
        add("hero_blade_wind",    build("hero_blade_wind",    Material.GOLDEN_SWORD,     "🌬 Hero Blade — Gale",    NamedTextColor.WHITE,       List.of("Passive Speed II while held","Launches enemies on hit"),                                                         7.0, -1.8));
        add("hero_blade_thunder", build("hero_blade_thunder", Material.NETHERITE_SWORD,  "⚡ Hero Blade — Thunder",  NamedTextColor.YELLOW,     List.of("Calls lightning on hit"),                                                                                        8.5, -2.1));
        add("railblade",          build("railblade",          Material.NETHERITE_SWORD,  "💨 Railblade",            NamedTextColor.GRAY,        List.of("Passive Speed II while held","Ground Crit: Knockdown → Flaming Blow","Air Crit: Hover → Diagonal Downslash"),   9.0, -2.0));
        add("deepspindle",        build("deepspindle",        Material.NETHERITE_SWORD,  "☠ Deepspindle",           NamedTextColor.DARK_PURPLE, List.of("Wither+Blind on M1","Stand Crit: Thrust → 7 dark blasts","Sprint Crit: Self-stab → Dark Rift projectiles"),    10.0,-2.4));
        add("red_death",          build("red_death",          Material.NETHERITE_SWORD,  "🩸 Red Death",            NamedTextColor.RED,         List.of("Fire+Poison+Blood Poison on M1","Stand Crit: 3 blood stakes","Sprint Crit: Rapid thrust → 3 stakes","Lifesteal on kill"), 11.0,-2.3));
        add("crypt_blade",        build("crypt_blade",        Material.NETHERITE_SWORD,  "💀 Crypt Blade",          NamedTextColor.DARK_GREEN,  List.of("Wither on M1","Stand Crit: Darkness plunge → chain","Sprint Crit: 2 slashes → guardbreak cleave","Summon zombie on kill"), 10.0,-2.4));
        add("soulthorn",          build("soulthorn",          Material.IRON_SWORD,       "✦ Soulthorn",             NamedTextColor.LIGHT_PURPLE,List.of("M1 applies Soul Marks (max 3)","0 marks: Glow slash","1-2 marks: Rise, reel & detonate","3 marks: Teleport downslash (Hyperarmor)"), 8.0,-2.0));
        add("cold_point",         build("cold_point",         Material.GOLDEN_SWORD,     "❄ Cold Point",            NamedTextColor.AQUA,        List.of("Freeze+Slow on M1","Crit: Pierce dash through target"),                                                         7.0, -1.6));
        add("yselys_pyre_keeper", build("yselys_pyre_keeper", Material.NETHERITE_SWORD,  "🔥 Yselys Pyre Keeper",   NamedTextColor.GOLD,        List.of("Burns on M1","Air Crit: Ground Slam","Sprint Crit: Lunge & Launch","Crouch Crit: Rapid Slash x5"),               12.0,-2.8));
        add("bloodfouler",        build("bloodfouler",        Material.NETHERITE_SWORD,  "🩸 Bloodfouler",          NamedTextColor.DARK_RED,    List.of("M1: Blood Poisoning stacks (max 10)","Stand Crit: 360 Blood Infect","Sprint Crit: Rush Sweep"),                  11.0,-2.6));
        add("boltcrusher",        build("boltcrusher",        Material.NETHERITE_AXE,    "⚡ Boltcrusher",          NamedTextColor.YELLOW,      List.of("AOE lightning on M1","Stand Crit: Spin → Clobber (Sapped)","Sprint Crit: Ground Slam shockwave (Sapped)"),       12.0,-3.2));
        add("hailbreaker",        build("hailbreaker",        Material.DIAMOND_AXE,      "❄ Hailbreaker",           NamedTextColor.AQUA,        List.of("Stage 1: Heavy ice slash","Stage 2: Freeze slash","Stage 3: Dash + upward slash","Ice explosions after each stage"), 11.0,-3.0));
        add("gale_pale",          build("gale_pale",          Material.NETHERITE_AXE,    "🌬 Gale Pale",            NamedTextColor.WHITE,       List.of("M1: Knockdown","Stand Crit: 360 Sweep","Sprint Crit: Rush Sweep"),                                              13.0,-3.5));
        add("iron_requiem",       buildPistol());

        // ── Custom Weapons ────────────────────────────────────────────────────
        add("amethyst", build("amethyst", Material.AMETHYST_SHARD, "💎 Amethyst", NamedTextColor.LIGHT_PURPLE,
            List.of("M1 builds Crystal Resonance","Stand Crit (full): Shatterstrike — crystal cone AOE",
                    "Sprint Crit: Prism Pierce — lunge beam through enemies",
                    "Shift Crit (full combo): Echo Fracture — delayed repeat strikes",
                    "Passive: Crits grant stacking Strength briefly"), 8.0, -2.0));

        add("flamewall", build("flamewall", Material.BLAZE_ROD, "🔥 Flamewall", NamedTextColor.GOLD,
            List.of("M1 builds Heat (max 10)","Stand Crit (full heat): Inferno Barrier — fire wall",
                    "Sprint Crit: Blazing Surge — fire dash + trail",
                    "Shift Crit (overheat): Solar Eruption — massive AOE + weapon disabled 5s",
                    "Passive: Burning enemies deal less damage"), 12.0, -2.8));

        add("im_blue", build("im_blue", Material.PRISMARINE_SHARD, "🌊 Im Blue", NamedTextColor.AQUA,
            List.of("M1 builds Rhythm (hit within 2s to maintain)","Stand Crit: Tidal Pulse — expanding knockback wave",
                    "Sprint Crit: Sonic Drift — phase dash + brief invincibility",
                    "Shift Crit (max rhythm): Deep Echo — 3 pulsing AOE waves",
                    "Passive: Speed + regen while rhythm active"), 7.0, -1.8));
    }

    private ItemStack build(String id, Material mat, String name, NamedTextColor color,
                            List<String> loreLines, double dmg, double spd) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        for (String line : loreLines)
            lore.add(Component.text("  " + line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("  Legendary Weapon", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);
        if (dmg > 0) meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE,
            new AttributeModifier(new NamespacedKey(plugin, id+"_dmg"), dmg, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        if (spd != 0) meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED,
            new AttributeModifier(new NamespacedKey(plugin, id+"_spd"), spd, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.getPersistentDataContainer().set(DeepwokenWeapons.WEAPON_ID_KEY, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildPistol() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("🔫 Iron Requiem", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        for (String line : List.of("  6 Bullets | Ironsing Pistol","  M1 with bullets: Ranged shot",
                "  M1 without: Rod (slowness)","  Stand Crit: Explosive Shot (reloads 1)",
                "  Sprint Crit: Spinning Fire (up to 4 shots)"))
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("  Deepwoken Legendary", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(DeepwokenWeapons.WEAPON_ID_KEY, PersistentDataType.STRING, "iron_requiem");
        item.setItemMeta(meta);
        return item;
    }

    private void add(String id, ItemStack item) { weapons.put(id, item); }
    public ItemStack getWeapon(String id) { ItemStack t = weapons.get(id.toLowerCase()); return t != null ? t.clone() : null; }
    public Set<String> getWeaponIds() { return weapons.keySet(); }
    public int getWeaponCount() { return weapons.size(); }
    public static String getWeaponId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(DeepwokenWeapons.WEAPON_ID_KEY, PersistentDataType.STRING);
    }
}
