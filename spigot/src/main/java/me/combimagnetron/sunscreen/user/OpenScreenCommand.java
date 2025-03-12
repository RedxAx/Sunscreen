// File: OpenScreenCommand.java
package me.combimagnetron.sunscreen.user;

import me.combimagnetron.sunscreen.menu.builtin.AspectRatioMenu;
import me.combimagnetron.sunscreen.menu.builtin.SetupMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OpenScreenCommand implements CommandExecutor {
    private final UserManager userManager;

    public OpenScreenCommand(UserManager userManager) {
        this.userManager = userManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        var user = userManager.user(player);
        if (user == null) {
            player.sendMessage("User not found.");
            return true;
        }

        // Instantiate SetupMenu to open the UI correctly
        new AspectRatioMenu(user);
        player.sendMessage("Screen opened!");
        return true;
    }
}