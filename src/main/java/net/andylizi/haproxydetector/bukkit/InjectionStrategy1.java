package net.andylizi.haproxydetector.bukkit;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.injector.netty.InjectionFactory;
import com.comphenix.protocol.injector.netty.ProtocolInjector;
import com.comphenix.protocol.reflect.FuzzyReflection;
import net.andylizi.haproxydetector.ReflectionUtil;

import java.lang.reflect.Field;

public class InjectionStrategy1 implements InjectionStrategy {
    private Field injectorFactoryField;
    private ProtocolInjector injector;
    private InjectionFactory oldFactory;

    @Override
    public void inject() throws ReflectiveOperationException {
        try {
            this.uninject();
        } catch (Throwable ignored) {
        }

        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
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
    }

    @Override
    public void uninject() throws ReflectiveOperationException {
        if (injectorFactoryField != null && injector != null && oldFactory != null) {
            InjectionFactory currentFactory = (InjectionFactory) injectorFactoryField.get(injector);
            ReflectionUtil.copyState(InjectionFactory.class, currentFactory, oldFactory);
            injectorFactoryField.set(injector, oldFactory);
            oldFactory = null;
        }
    }
}
