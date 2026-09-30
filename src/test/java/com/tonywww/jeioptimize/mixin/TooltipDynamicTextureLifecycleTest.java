package com.tonywww.jeioptimize.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs the compiled production wrappers, replacing only NativeImage's type to avoid native/OpenGL setup. */
public final class TooltipDynamicTextureLifecycleTest {
    public static void main(String[] args) throws Exception {
        setEnabledSnapshot(true);
        Class<?> fixture = loadProductionWrappers();
        Object texture = fixture.getConstructor().newInstance();
        Field pixels = fixture.getDeclaredField("pixels");
        pixels.setAccessible(true);
        Method initialize = fixture.getDeclaredMethod("jeiopt$initializeLiveImage", Operation.class);
        Method close = fixture.getDeclaredMethod("jeiopt$closeAtomically", Operation.class);
        Method replace = fixture.getDeclaredMethod("jeiopt$replaceAtomically", Object.class, Operation.class);
        initialize.setAccessible(true);
        close.setAccessible(true);
        replace.setAccessible(true);
        AtomicInteger uploads = new AtomicInteger();
        Operation<Void> originalInitialization = ignored -> {
            try {
                // The vanilla queued body dereferences pixels before upload.
                pixels.get(texture).toString();
                uploads.incrementAndGet();
                return null;
            } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        };
        try {
            originalInitialization.call();
            throw new AssertionError("original close-before-replay path did not reproduce");
        } catch (NullPointerException expected) {}
        invoke(initialize, texture, originalInitialization);
        check(uploads.get() == 0, "disposed queued callback neither crashes nor allocates/uploads");
        Object image = new Object();
        pixels.set(texture, image);
        invoke(initialize, texture, originalInitialization);
        check(uploads.get() == 1, "live image retains exactly one original initialization");

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch closeAttempted = new CountDownLatch(1);
        FutureTask<Void> replay = new FutureTask<>(() -> {
            invoke(initialize, texture, (Operation<Void>) ignored -> {
                entered.countDown();
                await(release);
                check(read(pixels, texture) == image, "close cannot free an image during queued initialization");
                return null;
            });
            return null;
        });
        FutureTask<Void> disposal = new FutureTask<>(() -> {
            closeAttempted.countDown();
            invoke(close, texture, (Operation<Void>) ignored -> { write(pixels, texture, null); return null; });
            return null;
        });
        Thread replayThread = new Thread(replay, "fixture-render");
        Thread closeThread = new Thread(disposal, "fixture-dispose");
        replayThread.setDaemon(true);
        closeThread.setDaemon(true);
        try {
            replayThread.start();
            check(entered.await(3, TimeUnit.SECONDS), "render callback entered");
            closeThread.start();
            check(closeAttempted.await(3, TimeUnit.SECONDS), "concurrent close attempted");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (closeThread.getState() != Thread.State.BLOCKED && !disposal.isDone() && System.nanoTime() < deadline) {
                Thread.yield();
            }
            check(closeThread.getState() == Thread.State.BLOCKED && !disposal.isDone(), "close waits for active initialization");
        } finally { release.countDown(); }
        replay.get(3, TimeUnit.SECONDS);
        disposal.get(3, TimeUnit.SECONDS);
        check(pixels.get(texture) == null, "close executes after initialization");
        invoke(initialize, texture, originalInitialization);
        check(uploads.get() == 1, "replay after disposal remains inert");

        Object replacement = new Object();
        invoke(replace, texture, replacement, (Operation<Void>) values -> {
            check(Thread.holdsLock(texture), "replacement shares the initialization/close monitor");
            write(pixels, texture, values[0]);
            return null;
        });
        check(pixels.get(texture) == replacement, "replacement passed to original unchanged");
        invoke(initialize, texture, originalInitialization);
        check(uploads.get() == 2, "replacement can be initialized normally");
        try {
            invoke(initialize, texture, (Operation<Void>) ignored -> { throw new IllegalStateException("real upload failure"); });
            throw new AssertionError("real upload error hidden");
        } catch (IllegalStateException expected) {}
        check(!Thread.holdsLock(texture), "failure releases lifecycle monitor");
        setEnabledSnapshot(false);
        pixels.set(texture, null);
        try {
            invoke(initialize, texture, originalInitialization);
            throw new AssertionError("master-off original behavior changed");
        } catch (NullPointerException expected) {}
        invoke(close, texture, (Operation<Void>) ignored -> {
            check(!Thread.holdsLock(texture), "master-off close is not wrapped in the monitor");
            return null;
        });
        System.out.println("TooltipDynamicTextureLifecycleTest passed: compiled wrappers, original crash, closed replay, concurrent close, replacement, error propagation, master-off");
    }

    private static Class<?> loadProductionWrappers() throws Exception {
        String original = "com/tonywww/jeioptimize/mixin/DynamicTextureStartupMixin";
        String renamed = "com/tonywww/jeioptimize/mixin/DynamicTextureTestFixture";
        ClassNode node = new ClassNode();
        try (var source = TooltipDynamicTextureLifecycleTest.class.getClassLoader().getResourceAsStream(original + ".class")) {
            check(source != null, "production mixin bytecode is present");
            new ClassReader(source).accept(node, 0);
        }
        node.access &= ~Opcodes.ACC_ABSTRACT;
        ClassWriter writer = new ClassWriter(0);
        node.accept(new ClassRemapper(writer, new SimpleRemapper(Map.of(original, renamed,
            "com/mojang/blaze3d/platform/NativeImage", "java/lang/Object"))));
        byte[] bytecode = writer.toByteArray();
        return new ClassLoader(TooltipDynamicTextureLifecycleTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(renamed.replace('/', '.'), bytecode, 0, bytecode.length); }
        }.define();
    }

    private static void invoke(Method method, Object receiver, Object... arguments) throws Exception {
        try { method.invoke(receiver, arguments); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception error) throw error;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }

    private static void await(CountDownLatch latch) {
        try { check(latch.await(3, TimeUnit.SECONDS), "fixture released"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }

    private static Object read(Field field, Object receiver) {
        try { return field.get(receiver); }
        catch (IllegalAccessException failure) { throw new AssertionError(failure); }
    }

    private static void setEnabledSnapshot(boolean enabled) throws Exception {
        Class<?> snapshotType = Class.forName("com.tonywww.jeioptimize.config.JeiOptConfigSnapshot$Snapshot");
        var components = snapshotType.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            types[index] = components[index].getType();
            values[index] = types[index] == boolean.class ? false : types[index] == int.class ? 1 : Set.of();
        }
        values[0] = enabled;
        var constructor = snapshotType.getDeclaredConstructor(types);
        constructor.setAccessible(true);
        Field current = Class.forName("com.tonywww.jeioptimize.config.JeiOptConfigSnapshot").getDeclaredField("current");
        current.setAccessible(true);
        current.set(null, constructor.newInstance(values));
    }

    private static void write(Field field, Object receiver, Object value) {
        try { field.set(receiver, value); }
        catch (IllegalAccessException failure) { throw new AssertionError(failure); }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
