package me.combimagnetron.sunscreen;


import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTimeUpdate;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import me.combimagnetron.passport.internal.entity.impl.display.TextDisplay;
import me.combimagnetron.sunscreen.image.CanvasRenderer;
import me.combimagnetron.sunscreen.image.Color;
import me.combimagnetron.sunscreen.menu.builtin.AspectRatioMenu;
import me.combimagnetron.sunscreen.menu.element.Position;
import me.combimagnetron.sunscreen.menu.element.div.Div;
import me.combimagnetron.sunscreen.menu.element.impl.TextElement;
import me.combimagnetron.sunscreen.menu.listener.MenuListener;
import me.combimagnetron.sunscreen.style.Style;
import me.combimagnetron.sunscreen.style.Text;
import me.combimagnetron.sunscreen.user.SunscreenUser;
import me.combimagnetron.sunscreen.user.UserManager;
import me.combimagnetron.sunscreen.util.Identifier;
import me.combimagnetron.sunscreen.util.Scheduler;
import me.combimagnetron.sunscreen.util.Vec2d;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public class SunscreenPlugin extends JavaPlugin implements Listener {
    private SunscreenLibrary<SunscreenPlugin, Player> library;
    private UserManager userManager;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
        PacketEvents.getAPI().getEventManager().registerListener(new MenuListener(), PacketListenerPriority.LOWEST);
    }

    @Override
    public void onEnable() {
        PacketEvents.getAPI().init();
        this.library = new SunscreenLibrarySpigot(this);
        SunscreenLibrary.Holder.INSTANCE = library;
        getServer().getPluginManager().registerEvents(this, this);
        this.userManager = new UserManager(this);
        this.getDataFolder().mkdirs();

        this.getCommand("openscreen").setExecutor(new me.combimagnetron.sunscreen.user.OpenScreenCommand(userManager));
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
    }

    public static int combine(int a, int b) {
        return (a << 16) | (b & 0xFFFF);
    }


    //TEST
    @EventHandler
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        //new Test(userManager.user(player));
        SunscreenUser<Player> user = userManager.user(player);
        new AspectRatioMenu(userManager.user(player));
        System.out.println("x");
    }

    public UserManager userManager() {
        return userManager;
    }


}
