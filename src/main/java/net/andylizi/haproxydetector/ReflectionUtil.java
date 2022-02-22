/*
 * Copyright (C) 2020 Andy Li
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Lesser Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
*/
package net.andylizi.haproxydetector;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;

import sun.misc.Unsafe;

public final class ReflectionUtil {
    private ReflectionUtil() {
        throw new AssertionError();
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    static Unsafe getUnsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    public static Lookup getLookupImpl() throws ReflectiveOperationException {
        Field field = Lookup.class.getDeclaredField("IMPL_LOOKUP");
        if (trySetAccessible(field)) {
            return (Lookup) field.get(null);
        } else {
            Unsafe unsafe = getUnsafe();
            return (Lookup) unsafe.getObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field));
        }
    }

    public static boolean trySetAccessible(AccessibleObject obj) {
        try {
            try {
                //noinspection JavaReflectionMemberAccess
                return (boolean) AccessibleObject.class.getMethod("trySetAccessible").invoke(obj);
            } catch (NoSuchMethodException e) {
                obj.setAccessible(true);
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static void setModifiers(Field field, int modifiers) throws ReflectiveOperationException {
        try {
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, modifiers);
        } catch (ReflectiveOperationException e) {
            Lookup lookup = getLookupImpl();
            MethodHandle setter = lookup.findSetter(Field.class, "modifiers", int.class);
            try {
                setter.invokeExact(field, modifiers);
            } catch (Throwable t) {
                sneakyThrow(t);
            }
        }
    }

    public static <T> void copyState(Class<? super T> templateClass, T src, T dst) throws ReflectiveOperationException {
        for (Field f : templateClass.getDeclaredFields()) {
            f.setAccessible(true);
            f.set(dst, f.get(src));
        }
    }

    public static Field getFirstDeclaringFieldByType(Class<?> cls, Class<?> type) throws ReflectiveOperationException {
        for (Field field : cls.getDeclaredFields()) {
            if (type.equals(field.getType())) {
                return field;
            }
        }
        throw new NoSuchFieldException("field with type " + type.getName() + " in " + cls.getName());
    }
}
