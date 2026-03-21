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
        add("kyrsewinter", build("kyrsewinter", Material.DIAMOND_SWORD, "❄ Kyrsewinter", NamedTextColor.AQUA, List.of("Bleeds & freezes on M1", "Stand Crit: Ice poke → blast", "Sprint Crit: Leap → ice explosion"), 9.0, -2.2, true, 1));
        add("stormseye", build("stormseye", Material.NETHERITE_SWORD, "⚡ Stormseye", NamedTextColor.YELLOW, List.of("Calls lightning on every hit", "Blinds struck targets"), 10.0, -2.3, true, 2));
        add("hero_blade_fire", build("hero_blade_fire", Material.GOLDEN_SWORD, "🔥 Hero Blade — Flame", NamedTextColor.RED, List.of("Sets enemies ablaze on hit"), 8.0, -2.0, true, 3));
        add("hero_blade_ice", build("hero_blade_ice", Material.IRON_SWORD, "❄ Hero Blade — Frost", NamedTextColor.AQUA, List.of("Slows enemies on hit"), 8.0, -2.0, true, 4));
        add("hero_blade_wind", build("hero_blade_wind", Material.WOODEN_SWORD, "🌬 Hero Blade — Gale", NamedTextColor.WHITE, List.of("Passive Speed II while held", "Launches enemies on hit"), 7.0, -1.8, true, 5));
        add("hero_blade_thunder", build("hero_blade_thunder", Material.STONE_SWORD, "⚡ Hero Blade — Thunder", NamedTextColor.YELLOW, List.of("Calls lightning on hit"), 8.5, -2.1, true, 6));
        add("railblade", build("railblade", Material.NETHERITE_HOE, "💨 Railblade", NamedTextColor.GRAY, List.of("Passive Speed II while held", "Ground Crit: Knockdown → Flaming Blow", "Air Crit: Hover → Diagonal Downslash"), 9.0, -2.0, true, 7));
        add("deepspindle", build("deepspindle", Material.NETHERITE_SHOVEL, "☠ Deepspindle", NamedTextColor.DARK_PURPLE, List.of("Wither+Blind on M1", "Stand Crit: Thrust → 7 dark blasts", "Sprint Crit: Self-stab → Dark Rift"), 10.0, -2.4, true, 8));
        add("red_death", build("red_death", Material.NETHERITE_AXE, "🩸 Red Death", NamedTextColor.RED, List.of("Fire+Poison+Blood on M1", "Stand Crit: 3 blood stakes", "Sprint Crit: Rapid thrust → stakes", "Lifesteal on kill"), 11.0, -2.3, true, 9));
        add("crypt_blade", build("crypt_blade", Material.DIAMOND_AXE, "💀 Crypt Blade", NamedTextColor.DARK_GREEN, List.of("Wither on M1", "Stand Crit: Darkness plunge → chain", "Sprint Crit: Slashes → guardbreak cleave", "Summon zombie on kill"), 10.0, -2.4, true, 10));
        add("soulthorn", build("soulthorn", Material.IRON_AXE, "✦ Soulthorn", NamedTextColor.LIGHT_PURPLE, List.of("M1 applies Soul Marks (max 3)", "0: Glow slash", "1-2: Rise, reel & detonate", "3: Teleport downslash (Hyperarmor)"), 8.0, -2.0, true, 11));
        add("cold_point", build("cold_point", Material.GOLDEN_AXE, "❄ Cold Point", NamedTextColor.AQUA, List.of("Freeze+Slow on M1", "Crit: Pierce dash through target"), 7.0, -1.6, true, 12));
        add("yselys_pyre_keeper", build("yselys_pyre_keeper", Material.DIAMOND_HOE, "🔥 Yselys Pyre Keeper", NamedTextColor.GOLD, List.of("Burns on M1", "Air Crit: Ground Slam", "Sprint Crit: Lunge & Launch", "Crouch Crit: Rapid Slash x5"), 12.0, -2.8, true, 13));
        add("bloodfouler", build("bloodfouler", Material.IRON_HOE, "🩸 Bloodfouler", NamedTextColor.DARK_RED, List.of("M1: Blood Poisoning stacks (max 10)", "Stand Crit: 360 Blood Infect", "Sprint Crit: Rush Sweep"), 11.0, -2.6, true, 14));
        add("boltcrusher", build("boltcrusher", Material.STONE_AXE, "⚡ Boltcrusher", NamedTextColor.YELLOW, List.of("AOE lightning on M1", "Stand Crit: Spin → Clobber (Sapped)", "Sprint Crit: Ground Slam shockwave"), 12.0, -3.2, true, 15));
        add("hailbreaker", build("hailbreaker", Material.WOODEN_AXE, "❄ Hailbreaker", NamedTextColor.AQUA, List.of("Stage 1: Heavy ice slash", "Stage 2: Freeze slash", "Stage 3: Dash + upward slash", "Ice explosions after each stage"), 11.0, -3.0, true, 16));
        add("gale_pale", build("gale_pale", Material.IRON_SHOVEL, "🌬 Gale Pale", NamedTextColor.WHITE, List.of("M1: Knockdown", "Stand Crit: 360 Sweep", "Sprint Crit: Rush Sweep"), 13.0, -3.5, true, 17));
        add("iron_requiem", build("iron_requiem", Material.CROSSBOW, "🔫 Iron Requiem", NamedTextColor.GRAY, List.of("6 Bullets | Ironsing Pistol", "M1 with bullets: Ranged shot", "M1 without: Rod", "Stand Crit: Explosive Shot", "Sprint Crit: Spinning Fire"), 8.0, -2.0, false, 18));
        add("amethyst", build("amethyst", Material.AMETHYST_SHARD, "💎 Amethyst", NamedTextColor.LIGHT_PURPLE, List.of("M1 builds Crystal Resonance", "Stand Crit (full): Shatterstrike", "Sprint Crit: Prism Pierce", "Shift Crit (full combo): Echo Fracture"), 8.0, -2.0, false, 19));
        add("flamewall", build("flamewall", Material.BLAZE_ROD, "🔥 Flamewall", NamedTextColor.GOLD, List.of("M1 builds Heat (max 10)", "Stand Crit (full heat): Inferno Barrier", "Sprint Crit: Blazing Surge", "Shift Crit (overheat): Solar Eruption"), 12.0, -2.8, false, 20));
        add("im_blue", build("im_blue", Material.PRISMARINE_SHARD, "🌊 Im Blue", NamedTextColor.AQUA, List.of("M1 builds Rhythm (hit within 2s)", "Stand Crit: Tidal Pulse", "Sprint Crit: Sonic Drift", "Shift Crit (max): Deep Echo"), 7.0, -1.8, false, 21));
    }

    private ItemStack build(String id, Material mat, String name, NamedTextColor color,
                            List<String> loreLines, double dmg, double spd, boolean addAttrs, int customModelData) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        // Display name
        meta.displayName(Component.text(name, color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        for (String line : loreLines)
            lore.add(Component.text("  " + line, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("  Legendary Weapon", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        // Flags
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);

        // Custom Model Data — tells resource pack which texture to use
        meta.setCustomModelData(customModelData);

        // Attack attributes
        if (addAttrs && dmg > 0)
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE,
                new AttributeModifier(new NamespacedKey(plugin, id+"_dmg"), dmg,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        if (addAttrs && spd != 0)
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED,
                new AttributeModifier(new NamespacedKey(plugin, id+"_spd"), spd,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

        // Weapon ID tag
        meta.getPersistentDataContainer()
                .set(DeepwokenWeapons.WEAPON_ID_KEY, PersistentDataType.STRING, id);

        item.setItemMeta(meta);
        return item;
    }

    private void add(String id, ItemStack item) { weapons.put(id, item); }

    public ItemStack getWeapon(String id) {
        ItemStack t = weapons.get(id.toLowerCase());
        return t != null ? t.clone() : null;
    }

    public Set<String> getWeaponIds() { return weapons.keySet(); }
    public int getWeaponCount() { return weapons.size(); }

    public static String getWeaponId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(DeepwokenWeapons.WEAPON_ID_KEY, PersistentDataType.STRING);
    }
}
