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

import java.lang.reflect.Field;

public final class ReflectionUtil {
    private ReflectionUtil() {
        throw new AssertionError();
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
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
