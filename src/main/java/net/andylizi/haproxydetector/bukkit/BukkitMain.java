package net.andylizi.haproxydetector.bukkit;

import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitMain extends JavaPlugin {
    static Logger logger;

    @Override
    public void onLoad() {
        logger = getLogger();
    }
}
