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
        // ── Swords ───────────────────────────────────────────────────────────────
        add("kyrsewinter", build(
            "kyrsewinter", Material.DIAMOND_SWORD,
            "❄ Kyrsewinter", NamedTextColor.AQUA,
            List.of("Freezes & slows enemies on hit",
                    "Ice particles trail your swings",
                    "Ice of the eternal winter"),
            9.0, -2.2
        ));

        add("stormseye", build(
            "stormseye", Material.NETHERITE_SWORD,
            "⚡ Stormseye", NamedTextColor.YELLOW,
            List.of("Calls lightning upon every hit",
                    "Blinds those struck by the bolt"),
            10.0, -2.3
        ));

        add("hero_blade_fire", build(
            "hero_blade_fire", Material.NETHERITE_SWORD,
            "🔥 Hero Blade — Flame", NamedTextColor.RED,
            List.of("Sets enemies ablaze on every hit"),
            8.0, -2.0
        ));

        add("hero_blade_ice", build(
            "hero_blade_ice", Material.DIAMOND_SWORD,
            "❄ Hero Blade — Frost", NamedTextColor.AQUA,
            List.of("Slows enemies with ice on hit"),
            8.0, -2.0
        ));

        add("hero_blade_wind", build(
            "hero_blade_wind", Material.GOLDEN_SWORD,
            "🌬 Hero Blade — Gale", NamedTextColor.WHITE,
            List.of("Passive Speed II while held",
                    "Launches enemies into the air on hit"),
            7.0, -1.8
        ));

        add("hero_blade_thunder", build(
            "hero_blade_thunder", Material.NETHERITE_SWORD,
            "⚡ Hero Blade — Thunder", NamedTextColor.YELLOW,
            List.of("Calls lightning on every hit"),
            8.5, -2.1
        ));

        add("railblade", build(
            "railblade", Material.NETHERITE_SWORD,
            "💨 Railblade", NamedTextColor.GRAY,
            List.of("Passive Speed II while held",
                    "Right-Click: Lunge forward at high speed"),
            9.0, -2.0
        ));

        add("deepspindle", build(
            "deepspindle", Material.NETHERITE_SWORD,
            "☠ Deepspindle", NamedTextColor.DARK_PURPLE,
            List.of("Inflicts Wither II & Blindness on hit",
                    "Void particles engulf your enemies",
                    "A relic from beyond the Veil"),
            10.0, -2.4
        ));

        add("red_death", build(
            "red_death", Material.NETHERITE_SWORD,
            "🩸 Red Death", NamedTextColor.RED,
            List.of("Sets enemies on fire on hit",
                    "Poisons those it touches",
                    "Lifesteal: Heal 2 HP on kill"),
            11.0, -2.3
        ));

        add("crypt_blade", build(
            "crypt_blade", Material.NETHERITE_SWORD,
            "💀 Crypt Blade", NamedTextColor.DARK_GREEN,
            List.of("Withers enemies on hit",
                    "Spawns a zombie servant on kill"),
            10.0, -2.4
        ));

        add("soulthorn", build(
            "soulthorn", Material.IRON_SWORD,
            "✦ Soulthorn", NamedTextColor.LIGHT_PURPLE,
            List.of("M1 applies Soul Marks (max 3)",
                    "Right-Click crit changes with mark count:",
                    " 0: Glowing slash (no effect)",
                    " 1-2: Rise up, reel & detonate",
                    " 3: Teleport & downslash (True Hyperarmor)"),
            8.0, -2.0
        ));

        add("cold_point", build(
            "cold_point", Material.GOLDEN_SWORD,
            "❄ Cold Point", NamedTextColor.AQUA,
            List.of("Right-Click: Pierce dash through target",
                    "Applies Crystal Freeze & Slowness IV",
                    "Every M1 procs on-hit talents"),
            7.0, -1.6
        ));

        // ── Greatswords ───────────────────────────────────────────────────────────
        add("yselys_pyre_keeper", build(
            "yselys_pyre_keeper", Material.NETHERITE_SWORD,
            "🔥 Yselys' Pyre Keeper", NamedTextColor.GOLD,
            List.of("Burns enemies on every M1",
                    "Right-Click (In Air): Ground Slam — AOE pull-down",
                    "Right-Click (Sprinting): Lunge & Launch",
                    "Right-Click (Crouching): Rapid Slash Combo (x5)",
                    "Callow & Verdant — Fire greatsword"),
            12.0, -2.8
        ));

        add("bloodfouler", build(
            "bloodfouler", Material.NETHERITE_SWORD,
            "🩸 Bloodfouler", NamedTextColor.DARK_RED,
            List.of("M1 inflicts Blood Poisoning (stacks to 10)",
                    "Blood Poison deals 0.5 HP/s per stack",
                    "Right-Click standing: 360° Sweep + Blood Infect",
                    "Right-Click sprinting: Rush Sweep"),
            11.0, -2.6
        ));

        // ── Axes / Greathammers ───────────────────────────────────────────────────
        add("boltcrusher", build(
            "boltcrusher", Material.NETHERITE_AXE,
            "⚡ Boltcrusher", NamedTextColor.YELLOW,
            List.of("Lightning strike on hit",
                    "All enemies within 5 blocks are struck too",
                    "Massive knockback"),
            12.0, -3.2
        ));

        add("hailbreaker", build(
            "hailbreaker", Material.DIAMOND_AXE,
            "❄ Hailbreaker", NamedTextColor.AQUA,
            List.of("Ice shards slow all nearby enemies on hit",
                    "AOE Slowness III in 5 block radius"),
            11.0, -3.0
        ));

        add("gale_pale", build(
            "gale_pale", Material.NETHERITE_AXE,
            "🌬 Gale Pale", NamedTextColor.WHITE,
            List.of("M1 applies Knockdown (heavy knockback + slow)",
                    "Right-Click standing: 360° Sweep (AOE)",
                    "Right-Click sprinting: Rush Sweep",
                    "Block-breaks on hit"),
            13.0, -3.5
        ));

        // ── Ranged ────────────────────────────────────────────────────────────────
        add("iron_requiem", buildPistol());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private ItemStack build(String id, Material mat, String name, NamedTextColor color,
                            List<String> loreLines, double dmg, double spd) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        // Display name
        meta.displayName(Component.text(name, color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        for (String line : loreLines) {
            lore.add(Component.text("  " + line, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("  Deepwoken Legendary", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        // Remove default attack attributes first, then add custom ones
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // Attack damage
        if (dmg > 0) {
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE,
                new AttributeModifier(
                    new NamespacedKey(plugin, id + "_dmg"),
                    dmg,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
                ));
        }

        // Attack speed
        if (spd != 0) {
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED,
                new AttributeModifier(
                    new NamespacedKey(plugin, id + "_spd"),
                    spd,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
                ));
        }

        // Weapon ID tag
        meta.getPersistentDataContainer()
            .set(DeepwokenWeapons.WEAPON_ID_KEY, PersistentDataType.STRING, id);

        // Unbreakable
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildPistol() {
        ItemStack item = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("🔫 Iron Requiem", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        for (String line : List.of(
                "  6 Bullets | Ironsing Pistol",
                "  M1 with bullets: Ranged shot",
                "  M1 without bullets: Rod (slowness + posture)",
                "  Right-Click standing: Explosive Shot (reloads 1)",
                "  Right-Click sprinting: Spinning Fire (up to 4 shots)")) {
            lore.add(Component.text(line, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("  Deepwoken Legendary", NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);

        var pdc = meta.getPersistentDataContainer();
        pdc.set(DeepwokenWeapons.WEAPON_ID_KEY, PersistentDataType.STRING, "iron_requiem");

        item.setItemMeta(meta);
        return item;
    }

    private void add(String id, ItemStack item) {
        weapons.put(id, item);
    }

    // ── Public API ───────────────────────────────────────────────────────────────

    /** Returns a fresh copy of the weapon ItemStack, or null if not found. */
    public ItemStack getWeapon(String id) {
        ItemStack template = weapons.get(id.toLowerCase());
        return template != null ? template.clone() : null;
    }

    /** Returns all registered weapon IDs. */
    public Set<String> getWeaponIds() {
        return weapons.keySet();
    }

    public int getWeaponCount() {
        return weapons.size();
    }

    /**
     * Returns the weapon ID stored in an item's PDC, or null if it's not a registered weapon.
     */
    public static String getWeaponId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(DeepwokenWeapons.WEAPON_ID_KEY, PersistentDataType.STRING);
    }
}
