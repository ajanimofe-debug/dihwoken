package com.deepwoken.weapons;

import com.deepwoken.weapons.managers.WeaponManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * /giveweapon <weapon_id> [player]
 * Gives a Deepwoken weapon to yourself or another player.
 */
public class WeaponCommand implements CommandExecutor, TabCompleter {

    private final DeepwokenWeapons plugin;

    public WeaponCommand(DeepwokenWeapons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /giveweapon <weapon> [player]", NamedTextColor.RED));
            sender.sendMessage(Component.text("Weapons: " + String.join(", ",
                    plugin.getWeaponManager().getWeaponIds()), NamedTextColor.YELLOW));
            return true;
        }

        String weaponId = args[0].toLowerCase();

        // Determine target player
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Component.text("Console must specify a player.", NamedTextColor.RED));
            return true;
        }

        ItemStack weapon = plugin.getWeaponManager().getWeapon(weaponId);
        if (weapon == null) {
            sender.sendMessage(Component.text("Unknown weapon: " + weaponId, NamedTextColor.RED));
            sender.sendMessage(Component.text("Available: " + String.join(", ",
                    plugin.getWeaponManager().getWeaponIds()), NamedTextColor.YELLOW));
            return true;
        }

        // Give the weapon
        target.getInventory().addItem(weapon);
        target.sendMessage(Component.text("⚔ You received: ", NamedTextColor.GOLD)
                .append(Component.text(weaponId.replace("_", " ").toUpperCase(), NamedTextColor.YELLOW)));

        if (!target.equals(sender)) {
            sender.sendMessage(Component.text("Gave " + weaponId + " to " + target.getName(), NamedTextColor.GREEN));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String id : plugin.getWeaponManager().getWeaponIds()) {
                if (id.startsWith(partial)) completions.add(id);
            }
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
        }

        return completions;
    }
}
