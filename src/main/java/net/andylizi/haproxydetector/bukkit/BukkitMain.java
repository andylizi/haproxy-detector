package net.andylizi.haproxydetector.bukkit;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.injector.netty.InjectionFactory;
import com.comphenix.protocol.injector.netty.ProtocolInjector;
import com.comphenix.protocol.reflect.FuzzyReflection;

import net.andylizi.haproxydetector.ProxyWhitelist;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;

import net.andylizi.haproxydetector.ReflectionUtil;
import static net.andylizi.haproxydetector.ReflectionUtil.sneakyThrow;

public final class BukkitMain extends JavaPlugin {
    static Logger logger;

    private Field injectorFactoryField;
    private ProtocolInjector injector;
    private InjectionFactory oldFactory;

    @Override
    public void onLoad() {
        logger = getLogger();
    }

    @Override
    public void onEnable() {
        try {
            Path path = this.getDataFolder().toPath().resolve("whitelist.conf");
            ProxyWhitelist whitelist = ProxyWhitelist.loadOrDefault(path).orElse(null);
            if (whitelist == null) {
                logger.warning("!!! ==============================");
                logger.warning("!!! Proxy whitelist is disabled in the config.");
                logger.warning("!!! This is EXTREMELY DANGEROUS, don't do this in production!");
                logger.warning("!!! ==============================");
            } else if (whitelist.size() == 0) {
                logger.warning("Proxy whitelist is empty. This will disallow all proxies!");
            }
            ProxyWhitelist.whitelist = whitelist;
        } catch (IOException e) {
            throw new RuntimeException("failed to load proxy whitelist", e);
        }

        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        try {
            Field injectorField = FuzzyReflection.fromObject(pm, true)
                    .getFieldByType("nettyInjector", ProtocolInjector.class);
            injectorField.setAccessible(true);
            injector = (ProtocolInjector) injectorField.get(pm);

            injectorFactoryField = FuzzyReflection.fromObject(injector, true)
                    .getFieldByType("factory", InjectionFactory.class);
            injectorFactoryField.setAccessible(true);

            oldFactory = (InjectionFactory) injectorFactoryField.get(injector);
            InjectionFactory newFactory = new HAProxyInjectorFactory(oldFactory.getPlugin());
            ReflectionUtil.copyState(InjectionFactory.class, oldFactory, newFactory);
            injectorFactoryField.set(injector, newFactory);
        } catch (ReflectiveOperationException e) {
            sneakyThrow(e);
        }

        try {
            new Metrics(this, 12604);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Failed to start metrics", t);
        }
    }

    @Override
    public void onDisable() {
        if (injectorFactoryField != null && injector != null && oldFactory != null) {
            try {
                InjectionFactory currentFactory = (InjectionFactory) injectorFactoryField.get(injector);
                ReflectionUtil.copyState(InjectionFactory.class, currentFactory, oldFactory);
                injectorFactoryField.set(injector, oldFactory);
                oldFactory = null;
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }
}
