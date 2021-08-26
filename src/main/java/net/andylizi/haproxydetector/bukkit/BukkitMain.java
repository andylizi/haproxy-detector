package net.andylizi.haproxydetector.bukkit;

import java.lang.reflect.Field;
import java.util.logging.Logger;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.injector.netty.InjectionFactory;
import com.comphenix.protocol.injector.netty.ProtocolInjector;
import com.comphenix.protocol.reflect.FuzzyReflection;

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

        new Metrics(this, 12604);
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
