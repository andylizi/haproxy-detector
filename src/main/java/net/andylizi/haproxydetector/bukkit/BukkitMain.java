package net.andylizi.haproxydetector.bukkit;

import java.lang.reflect.Field;
import java.util.logging.Logger;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.injector.netty.InjectionFactory;
import com.comphenix.protocol.injector.netty.ProtocolInjector;
import com.comphenix.protocol.reflect.FuzzyReflection;

import org.bukkit.plugin.java.JavaPlugin;

import static net.andylizi.haproxydetector.HAProxyDetectorHandler.sneakyThrow;

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
            copyState(InjectionFactory.class, oldFactory, newFactory);
            injectorFactoryField.set(injector, newFactory);
        } catch (ReflectiveOperationException e) {
            sneakyThrow(e);
        }
    }

    @Override
    public void onDisable() {
        if (injectorFactoryField != null && injector != null && oldFactory != null) {
            try {
                InjectionFactory currentFactory = (InjectionFactory) injectorFactoryField.get(injector);
                copyState(InjectionFactory.class, currentFactory, oldFactory);
                injectorFactoryField.set(injector, oldFactory);
                oldFactory = null;
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private static <T> void copyState(Class<? super T> templateClass, T src, T dst) throws ReflectiveOperationException {
        for (Field f : templateClass.getDeclaredFields()) {
            f.setAccessible(true);
            f.set(dst, f.get(src));
        }
    }
}
