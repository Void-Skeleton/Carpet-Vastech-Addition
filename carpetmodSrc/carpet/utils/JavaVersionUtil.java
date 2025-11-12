package carpet.utils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

public final class JavaVersionUtil {
    public static final int JAVA_VERSION;

    static {
        JAVA_VERSION = getJavaVersion();
//        if (JAVA_VERSION >= 17) {
//            crackReflectionAccess();
//        }
    }

    private JavaVersionUtil() {}

    private static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            // old format (Java 8 and below)
            return version.charAt(2) - '0';
        } else {
            // new format (Java 9 and above)
            int dotIndex = version.indexOf('.');
            if (dotIndex == -1) {
                return Integer.parseInt(version);
            } else {
                return Integer.parseInt(version.substring(0, dotIndex));
            }
        }
    }

    public static <T> FieldAccessor<T> objectFieldAccessor(Class<?> ownerClass, String name, Class<T> fieldType) {
        Field field;
        try {
            field = ownerClass.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Could not find field", e);
        }
        if (field.getType() != fieldType) {
            throw new RuntimeException("Field has wrong type, expected \"" + fieldType.getName() + "\", got \"" + field.getType().getName() + "\"");
        }
        if (fieldType.isPrimitive()) {
            throw new RuntimeException("objectFieldAccessor does not work for primitive field types");
        }
        try {
            field.setAccessible(true);
        } catch (RuntimeException e) { // InaccessibleObjectException
//            if (JAVA_VERSION <= 8 || JAVA_VERSION >= 17) {
//                // For Java <= 8 we have natural unrestricted reflection
//                // For Java > 17 we have cracked reflective access
//                throw new AssertionError(e);
//            } else {
//                throw new RuntimeException("!!! You should add JVM args --illegal-access=permit", e);
//            }
//
            long fieldOffset = UnsafeFieldAccessor.unsafe.objectFieldOffset(field);
            return new UnsafeFieldAccessor<>(ownerClass, fieldOffset);
        }

        try {
            return new MethodHandleFieldAccessor<>(
                    MethodHandles.lookup().unreflectGetter(field),
                    MethodHandles.lookup().unreflectSetter(field));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public interface FieldAccessor<T> {
        T get(Object instance);

        void set(Object instance, T value);
    }

    private static class MethodHandleFieldAccessor<T> implements FieldAccessor<T> {
        private final MethodHandle getter;
        private final MethodHandle setter;

        private MethodHandleFieldAccessor(MethodHandle getter, MethodHandle setter) {
            this.getter = getter;
            this.setter = setter;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get(Object instance) {
            try {
                return (T) getter.invoke(instance);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void set(Object instance, T value) {
            try {
                setter.invoke(instance, value);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class UnsafeFieldAccessor<T> implements FieldAccessor<T> {
        private static final sun.misc.Unsafe unsafe = getUnsafe();

        private final Class<?> ownerClass;
        private final long fieldOffset;

        private UnsafeFieldAccessor(Class<?> ownerClass, long fieldOffset) {
            this.ownerClass = ownerClass;
            this.fieldOffset = fieldOffset;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get(Object instance) {
            return (T) unsafe.getObject(ownerClass.cast(instance), fieldOffset);
        }

        @Override
        public void set(Object instance, T value) {
            unsafe.getAndSetObject(ownerClass.cast(instance), fieldOffset, value);
        }

        private static sun.misc.Unsafe getUnsafe() {
            try {
                Field field = null;
                for (Field f : sun.misc.Unsafe.class.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) && f.getType() == sun.misc.Unsafe.class) {
                        field = f;
                        break;
                    }
                }
                if (field == null) {
                    throw new RuntimeException("Unable to get Unsafe instance");
                }
                field.setAccessible(true);
                return (sun.misc.Unsafe) field.get(null);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Unable to get Unsafe instance", e);
            }
        }
    }
//
//    private static void crackReflectionAccess() {
//        Object classLoader = JavaVersionUtil.class.getClassLoader();
//        Object unnamedModule = invokeQuietly(classLoader, "getUnnamedModule");
//        Method method;
//        try {
//            method = unnamedModule.getClass().getDeclaredMethod( "implAddExportsOrOpens", String.class,
//                    unnamedModule.getClass(), boolean.class, boolean.class );
//        } catch (NoSuchMethodException e) {
//            throw new RuntimeException(e);
//        }
//
//        try {
//            method.setAccessible(true);
//        } catch (RuntimeException e) {
//            System.err.println("!!! PLEASE ADD JVM ARGS --add-opens java.base/java.lang=ALL-UNNAMED");
//            throw e;
//        }
//
//        Object moduleLayerBoot = invokeStaticQuietly(classForName("java.lang.ModuleLayer"), "boot");
//        ((Iterable<?>) invokeQuietly(moduleLayerBoot, "modules")).forEach(module -> {
//            try {
//                Set<String> packages = (Set<String>) invokeQuietly(module, "getPackages");
//                for(String eachPackage : packages) {
//                    method.invoke( module, eachPackage, unnamedModule, true, true );
//                }
//            } catch (InvocationTargetException e) {
//                throw new RuntimeException( e );
//            } catch (IllegalAccessException e) {
//                System.err.println("!!! PLEASE ADD JVM ARGS --add-opens java.base/java.lang=ALL-UNNAMED");
//                throw new RuntimeException(e);
//            }
//        } );
//    }
//
//    private static Object invokeQuietly(Object instance, String methodName, Object... params) {
//        try {
//            Method method = Arrays.stream(instance.getClass().getMethods()).filter(
//                    m -> methodName.equals(m.getName()) && m.getParameterCount() == params.length)
//                    .findFirst().orElseThrow(IllegalArgumentException::new);
//            return method.invoke(instance, params);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    private static Object invokeStaticQuietly(Class<?> clazz, String methodName, Object... params) {
//        try {
//            Method method = Arrays.stream(clazz.getMethods()).filter(
//                    m -> methodName.equals(m.getName()) && m.getParameterCount() == params.length)
//                    .findFirst().orElseThrow(IllegalArgumentException::new);
//            return method.invoke(null, params);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    private static Class<?> classForName(String className) {
//        try {
//            return Class.forName(className);
//        } catch (ClassNotFoundException e) {
//            throw new RuntimeException(e);
//        }
//    }
}
