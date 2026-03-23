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
        add("kyrsewinter",        build("kyrsewinter",        Material.DIAMOND_SWORD,    "❄ Kyrsewinter",           NamedTextColor.AQUA,        List.of("Bleeds & freezes on M1","Stand Crit: Ice poke → blast","Sprint Crit: Leap → ice explosion"),                    20.0, -2.2, 1));
        add("stormseye",          build("stormseye",          Material.NETHERITE_SWORD,  "⚡ Stormseye",            NamedTextColor.YELLOW,      List.of("Rifle | Every 3rd hit: Medallion Shockwave AOE","Close air M1: Teleport behind enemy","Crit: Circle enemy → 3 lightning shots"),  18.0, -1.8, 2));
        add("hero_blade_fire",    build("hero_blade_fire",    Material.GOLDEN_SWORD,     "🔥 Hero Blade — Flame",   NamedTextColor.RED,         List.of("Sets enemies ablaze on hit"),                                                                                    19.0, -2.0, 3));
        add("hero_blade_ice",     build("hero_blade_ice",     Material.IRON_SWORD,       "❄ Hero Blade — Frost",    NamedTextColor.AQUA,        List.of("Slows enemies on hit"),                                                                                          19.0, -2.0, 4));
        add("hero_blade_wind",    build("hero_blade_wind",    Material.WOODEN_SWORD,     "🌬 Hero Blade — Gale",    NamedTextColor.WHITE,       List.of("Passive Speed II while held","Launches enemies on hit"),                                                         17.0, -1.8, 5));
        add("hero_blade_thunder", build("hero_blade_thunder", Material.STONE_SWORD,      "⚡ Hero Blade — Thunder",  NamedTextColor.YELLOW,     List.of("Lightning on hit","Crit: Ground slam → thunder line → ragdoll"),                                                 20.0, -2.1, 6));
        add("railblade",          build("railblade",          Material.NETHERITE_HOE,    "💨 Railblade",            NamedTextColor.GRAY,        List.of("Passive Speed II while held","Ground Crit: Knockdown → Flaming Blow","Air Crit: Hover → Diagonal Downslash"),   21.0, -2.0, 7));
        add("deepspindle",        build("deepspindle",        Material.NETHERITE_SHOVEL, "☠ Deepspindle",           NamedTextColor.DARK_PURPLE, List.of("Wither+Blind on M1","Stand Crit: Thrust → 7 dark blasts","Sprint Crit: Self-stab → Dark Rift"),                20.0, -2.4, 8));
        add("red_death",          build("red_death",          Material.NETHERITE_AXE,    "🩸 Red Death",            NamedTextColor.RED,         List.of("Fire+Poison+Blood on M1","Stand Crit: 3 blood stakes","Sprint Crit: Rapid thrust → stakes","Lifesteal on kill"), 18.0, -2.3, 9));
        add("crypt_blade",        build("crypt_blade",        Material.DIAMOND_AXE,      "💀 Crypt Blade",          NamedTextColor.DARK_GREEN,  List.of("Wither on M1","Stand Crit: Darkness plunge → chain","Sprint Crit: Slashes → guardbreak cleave","Summon zombie on kill"), 20.0, -2.4, 10));
        add("soulthorn",          build("soulthorn",          Material.IRON_AXE,         "✦ Soulthorn",             NamedTextColor.LIGHT_PURPLE,List.of("M1 applies Soul Marks (max 3)","0: Glow slash","1-2: Rise, reel & detonate","3: Teleport downslash + Hyperarmor"), 19.0, -2.0, 11));
        add("cold_point",         build("cold_point",         Material.GOLDEN_AXE,       "❄ Cold Point",            NamedTextColor.AQUA,        List.of("Freeze+Slow on M1","Crit: Pierce dash — high damage + Crystal Freeze"),                                         18.0, -1.6, 12));
        add("yselys_pyre_keeper", build("yselys_pyre_keeper", Material.DIAMOND_HOE,      "🔥 Yselys Pyre Keeper",   NamedTextColor.GOLD,        List.of("Callow & Verdant — Greatsword + Dagger","Green flames on M1 (4s cooldown)","5 Crits: Stand/Air/Sprint/Crouch/Slide"), 21.0, -2.8, 13));
        add("verdant",            buildVerdant());
        add("bloodfouler",        build("bloodfouler",        Material.IRON_HOE,         "🩸 Bloodfouler",          NamedTextColor.DARK_RED,    List.of("M1: Blood Poisoning stacks (max 10)","Stand Crit: 360 Blood Infect","Sprint Crit: Rush Sweep"),                  19.0, -2.6, 14));
        add("boltcrusher",        build("boltcrusher",        Material.STONE_AXE,        "⚡ Boltcrusher",          NamedTextColor.YELLOW,      List.of("AOE lightning on M1","Stand Crit: Spin → Clobber (Sapped)","Sprint Crit: Ground Slam shockwave"),               22.0, -3.2, 15));
        add("hailbreaker",        build("hailbreaker",        Material.WOODEN_AXE,       "❄ Hailbreaker",           NamedTextColor.AQUA,        List.of("Stage 1→2→3 advancing crits","Each stage: bigger ice explosions","Stage 3: Dash + upward slash"),               20.0, -3.0, 16));
        add("gale_pale",          build("gale_pale",          Material.IRON_SHOVEL,      "🌬 Gale Pale",            NamedTextColor.WHITE,       List.of("M1: Knockdown","Stand Crit: 360 Sweep (massive AOE)","Sprint Crit: Rush Sweep"),                               22.0, -3.5, 17));
        add("iron_requiem",       buildPistol());
        add("amethyst",           build("amethyst",           Material.AMETHYST_SHARD,   "💎 Amethyst",             NamedTextColor.LIGHT_PURPLE,List.of("M1 builds Crystal Resonance","Stand Crit (full): Shatterstrike (cone)","Sprint Crit: Prism Pierce","Shift Crit: Echo Fracture"), 18.0, -2.0, 19));
        add("flamewall",          build("flamewall",          Material.BLAZE_ROD,        "🔥 Flamewall",            NamedTextColor.GOLD,        List.of("M1 builds Heat (max 10)","Stand Crit (5+ heat): Inferno Barrier","Sprint Crit: Blazing Surge","Shift Crit (8+ heat): Solar Eruption"), 20.0, -2.8, 20));
        add("im_blue",            build("im_blue",            Material.PRISMARINE_SHARD, "🌊 Im Blue",              NamedTextColor.AQUA,        List.of("M1 builds Rhythm (hit within 3s)","Stand Crit (5+): Tidal Pulse","Sprint Crit: Sonic Drift","Shift Crit (8+): Deep Echo"), 17.0, -1.8, 21));
        add("zodiac",             build("zodiac",             Material.GOLD_INGOT,       "♈ The Zodiac",            NamedTextColor.GOLD,        List.of("Cycles Aries → Libra → Scorpio","Aries: Burst damage + knockback","Libra: Damage reduction field","Scorpio: Stacking poison back-strike"), 18.0, -2.0, 23));
        add("nullscapes",         build("nullscapes",         Material.ECHO_SHARD,       "🕳 Nullscapes",           NamedTextColor.DARK_GRAY,   List.of("Void weapon — weakens enemies on hit","Stand Crit: Erase Pulse","Sprint Crit: Rift Break","Shift Crit (full void): Absolute Zero"), 19.0, -2.2, 24));
        add("orbit",              build("orbit",              Material.LODESTONE,        "🪐 Orbit",                NamedTextColor.BLUE,        List.of("Gravity pulls enemies on hit","Jump Crit: Orbital Slam","Sprint Crit: Satellite Barrage","Shift Crit (8+ charge): Event Horizon"), 19.0, -2.1, 25));
    }

    private ItemStack build(String id, Material mat, String name, NamedTextColor color,
                            List<String> loreLines, double dmg, double spd, int customModelData) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        for (String line : loreLines)
            lore.add(Component.text("  " + line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("  Legendary Weapon", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.setUnbreakable(true);
        meta.setCustomModelData(customModelData);
        // Force attack damage on ALL weapons
        if (dmg > 0)
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE,
                new AttributeModifier(new NamespacedKey(plugin, id+"_dmg"), dmg,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        if (spd != 0)
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED,
                new AttributeModifier(new NamespacedKey(plugin, id+"_spd"), spd,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.getPersistentDataContainer().set(DeepwokenWeapons.WEAPON_ID_KEY, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildVerdant() {
        ItemStack item = new ItemStack(Material.STONE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("🌿 Verdant", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  Callow & Verdant — Offhand Dagger", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  Paired with Yselys Pyre Keeper", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("  Legendary Weapon", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        meta.setUnbreakable(true);
        meta.setCustomModelData(22);
        meta.getPersistentDataContainer().set(DeepwokenWeapons.WEAPON_ID_KEY, PersistentDataType.STRING, "verdant");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildPistol() {
        ItemStack item = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("🔫 Iron Requiem", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        for (String line : List.of("  6 Bullets | Ironsing Pistol","  M1 with bullets: Ranged shot (bonus dmg)",
                "  M1 without: Rod (slowness)","  Stand Crit: Explosive Shot (reloads 1)",
                "  Sprint Crit: Spinning Fire (up to 4 shots)"))
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("  Deepwoken Legendary", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        meta.setUnbreakable(true);
        meta.setCustomModelData(18);
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE,
            new AttributeModifier(new NamespacedKey(plugin, "iron_requiem_dmg"), 17.0,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED,
            new AttributeModifier(new NamespacedKey(plugin, "iron_requiem_spd"), -2.0,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
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
        return item.getItemMeta().getPersistentDataContainer()
                .get(DeepwokenWeapons.WEAPON_ID_KEY, PersistentDataType.STRING);
    }
}
