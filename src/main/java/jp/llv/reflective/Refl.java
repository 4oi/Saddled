/* 
 * Copyright (C) 2015 Toyblocks
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jp.llv.reflective;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.function.Function;

/**
 *
 * @author Toyblocks
 */
public class Refl {

    public static ReflexiveClass<?> getRClass(String name) {
        try {
            return new ReflexiveClass<>(Class.forName(name));
        } catch(ReflectiveOperationException ex) {
            throwException(ex);
            throw new Error("Unreachable");
        }
    }
    
    public static <T> ReflexiveClass<T> wrap(Class<T> value) {
        return new ReflexiveClass<>(value);
    }

    public static <T> ReflexiveObject<T> wrap(T value) {
        return new ReflexiveObject<>(value);
    }

    private static boolean match(Class[] s, Class[] c) {
        if (s.length != c.length) {
            return false;
        }
        for (int i = 0; i < s.length; i++) {
            if (!s[i].isAssignableFrom(c[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean match(Executable e, Class[] c) {
        return match(e.getParameterTypes(), c);
    }

    private static <E extends Executable> E find(E[] exe, Object[] type) {
        Class[] c = Arrays.stream(type).map(o -> {
            return o != null ? o.getClass() : Object.class;
        }).toArray(Class[]::new);
        for (E e : exe) {
            if (match(e, c)) {
                return e;
            }
        }
        return null;
    }

    private static Method find(Method[] exe, String name, Object[] type) {
        return find(Arrays.stream(exe).filter(m -> m.getName().equals(name)).toArray(Method[]::new), type);
    }

    public static <E extends Throwable> void throwException(E ex) {
        Refl.<RuntimeException>throwGenericException(ex);
    }

    private static <E extends Throwable> void throwGenericException(Throwable ex) throws E {
        throw (E) ex;
    }

    public static final class ReflexiveObject<T> {

        private final T value;

        private ReflexiveObject(T value) {
            this.value = value;
        }

        public ReflexiveObject<?> invoke(String method, Object... args) {
            ReflexiveObject.unwrap(args);
            Class<?> c = this.value.getClass();
            Method t = find(c.getMethods(), method, args);
            if (t == null) {
                t = find(c.getDeclaredMethods(), method, args);
                if (t == null) {
                    throwException(new NoSuchMethodException(c.getName()+"#"+method));
                    throw new Error("Unreachable");
                }
                t.setAccessible(true);
            }
            try {
                Object result = t.invoke(this.value, args);
                return result != null ? new ReflexiveObject<>(result) : null;
            } catch (IllegalAccessException ex) {
                throwException(ex);
            } catch (InvocationTargetException ex) {
                throwException(ex.getCause());
            }
            throw new Error("Unreachable");
        }
        
        private Field getField(String name) {
            Field result;
            try {
                result = this.value.getClass().getField(name);
                return result;
            } catch (ReflectiveOperationException ex) {
            }
            try {
                result = this.value.getClass().getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (ReflectiveOperationException ex) {
            }
            throwException(new NoSuchFieldException());
            throw new Error("unreachable");
        }

        public ReflexiveObject<?> get(String name) {
            try {
                return new ReflexiveObject<>(getField(name).get(this.value));
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                throwException(ex);
            }
            throw new Error("Unreachable");
        }
        
        public ReflexiveObject<T> set(String name, Object object) {
            Object o = object;
            if (o instanceof ReflexiveObject) {
                o = ((ReflexiveObject) o).unwrap();
            }
            try {
                getField(name).set(this.value, o);
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                throwException(ex);
            }
            return this;
        }
        
        public <E> ReflexiveObject<E> map(Function<T, E> func) {
            return new ReflexiveObject<>(func.apply(value));
        }

        public ReflexiveClass<?> getRClass() {
            return new ReflexiveClass<>(this.value.getClass());
        }

        public boolean isNull() {
            return this.value == null;
        }

        public <E> E unwrap(Class<E> castTo) {
            return castTo.cast(this.value);
        }

        public boolean unwrapAsBoolean() {
            return (Boolean) this.value;
        }

        public byte unwrapAsByte() {
            return (Byte) this.value;
        }

        public short unwrapAsShort() {
            return (Short) this.value;
        }

        public char unwrapAsChar() {
            return (Character) this.value;
        }

        public int unwrapAsInt() {
            return (Integer) this.value;
        }

        public long unwrapAsLong() {
            return (Long) this.value;
        }

        public float unwrapAsFloat() {
            return (Float) this.value;
        }

        public double unwrapAsDouble() {
            return (Double) this.value;
        }

        public T unwrap() {
            return this.value;
        }

        @Override
        public int hashCode() {
            return this.value.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ReflexiveObject<?> other = (ReflexiveObject<?>) obj;
            return true;
        }

        @Override
        public String toString() {
            if (this.value == null) {
                return "null";
            }
            return this.value.toString();
        }

        public static void unwrap(Object[] toUnwrap) {
            for (int i = 0; i < toUnwrap.length; i++) {
                if (toUnwrap[i] instanceof ReflexiveObject) {
                    toUnwrap[i] = ((ReflexiveObject) toUnwrap[i]).unwrap();
                }
            }
        }

    }

    public static final class ReflexiveClass<T> {

        private final Class<T> value;

        private ReflexiveClass(Class<T> value) {
            this.value = value;
        }

        public ReflexiveObject<T> newInstance(Object... args) {
            ReflexiveObject.unwrap(args);
            Constructor<?> t = find(this.value.getConstructors(), args);
            if (t == null) {
                throwException(new NoSuchMethodException(this.value.getName()));
                throw new Error("Unreachable");
            }
            try {
                t.setAccessible(true);
                return new ReflexiveObject<>((T) t.newInstance(args));
            } catch (IllegalAccessException | InstantiationException | IllegalArgumentException ex) {
                throwException(ex);
            } catch (InvocationTargetException ex) {
                throwException(ex.getCause());
            }
            throw new Error("Unreachable");
        }

        public ReflexiveObject<?> invoke(String method, Object... args) {
            ReflexiveObject.unwrap(args);
            Method t = find(this.value.getMethods(), method, args);
            if (t == null) {
                t = find(this.value.getDeclaredMethods(), method, args);
                if (t == null) {
                    throwException(new NoSuchMethodException(this.value.getName()+"."+method));
                    throw new Error("Unreachable");
                }
                t.setAccessible(true);
            }
            try {
                Object result = t.invoke(null, args);
                return result != null ? new ReflexiveObject<>(result) : null;
            } catch (IllegalAccessException ex) {
                throwException(ex);
            } catch (InvocationTargetException ex) {
                throwException(ex.getCause());
            }
            throw new Error("Unreachable");
        }

        private Field getField(String name) {
            Field result;
            try {
                result = this.value.getField(name);
                return result;
            } catch (ReflectiveOperationException ex) {
            }
            try {
                result = this.value.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (ReflectiveOperationException ex) {
            }
            throwException(new NoSuchFieldException());
            throw new Error("unreachable");
        }
        
        public ReflexiveObject<?> get(String name) {
            try {
                return new ReflexiveObject<>(getField(name).get(null));
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                throwException(ex);
            }
            throw new Error("Unreachable");
        }
        
        public void set(String name, Object object) {
            Object o = object;
            if (o instanceof ReflexiveObject) {
                o = ((ReflexiveObject) o).unwrap();
            }
            try {
                getField(name).set(null, o);
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                throwException(ex);
            }
        }
        
        public Class<T> unwrap() {
            return this.value;
        }

    }

}
