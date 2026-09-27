package com.tonywww.jeioptimize.runtime;

//? if neoforge {
/*import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import mezz.jei.common.Internal;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;

// Encodes live game objects on the client thread, incrementally; never reads mutable registries on a worker.
final class JeiSessionFingerprint implements AutoCloseable {
    private final MessageDigest digest;
    private final RegistryFriendlyByteBuf buffer;
    private final ArrayDeque<Runnable> work = new ArrayDeque<>();
    private byte[] result;

    @SuppressWarnings("unchecked")
    JeiSessionFingerprint() throws Exception {
        digest = MessageDigest.getInstance("SHA-256");
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.getConnection() == null) throw new IllegalStateException("No client level");
        RegistryAccess access = mc.level.registryAccess();
        buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            // Reflection keeps compilation compatible with JEI 19.27. The mixin ABI gate requires 19.57's method.
            List<RecipeHolder<?>> recipes = List.copyOf((List<RecipeHolder<?>>)
                Internal.class.getMethod("getClientSyncedRecipes").invoke(null));
            if (recipes.isEmpty()) throw new IllegalStateException("No synchronized recipes to verify");
            text("JET session fingerprint v1");
            text(mc.options.languageCode);
            text(Integer.toString(recipes.size()));
            for (var recipe : recipes) {
                work.add(() -> {
                    buffer.clear();
                    RecipeHolder.STREAM_CODEC.encode(buffer, recipe);
                    bytes(buffer.nioBuffer(0, buffer.writerIndex()));
                });
            }
            // Include IDs and order as well as tags: network recipe codecs can contain numeric registry IDs.
            access.registries().sorted(Comparator.comparing(e -> e.key().location().toString()))
                .forEach(e -> work.add(() -> addRegistry(e.value())));
            for (var data : RegistryDataLoader.SYNCHRONIZED_REGISTRIES) {
                work.add(() -> addDynamicRegistry(access, data));
            }
        } catch (Exception | LinkageError error) {
            buffer.release();
            throw error;
        }
    }

    private <T> void addRegistry(Registry<T> registry) {
        text("registry:" + registry.key().location());
        text(Integer.toString(registry.size()));
        for (T value : registry) {
            text(registry.getId(value) + ":" + registry.getKey(value));
        }
        registry.getTags().sorted(Comparator.comparing(p -> p.getFirst().location().toString()))
            .forEach(pair -> {
                // Insert at the tail, with explicit registry/tag keys, so preparation itself stays inexpensive.
                work.add(() -> {
                    text("tag:" + registry.key().location() + ":" + pair.getFirst().location());
                    text(Integer.toString(pair.getSecond().size()));
                    pair.getSecond().forEach(holder -> text(holder.unwrapKey().orElseThrow().location().toString()));
                });
            });
    }

    private <T> void addDynamicRegistry(RegistryAccess access, RegistryDataLoader.RegistryData<T> data) {
        Registry<T> registry = access.registryOrThrow(data.key());
        var ops = RegistryOps.create(JsonOps.INSTANCE, access);
        registry.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().location().toString()))
            .forEach(entry -> work.add(() -> {
                text("value:" + data.key().location() + ":" + entry.getKey().location());
                // Full data, not recipe IDs/counts alone. Encoding failures always invalidate reuse.
                text(data.elementCodec().encodeStart(ops, entry.getValue()).getOrThrow().toString());
            }));
    }

    boolean step(long deadline) {
        do {
            Runnable next = work.poll();
            if (next == null) {
                result = digest.digest();
                return true;
            }
            next.run();
        } while (System.nanoTime() < deadline);
        return false;
    }

    private void text(String value) { bytes(ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8))); }

    private void bytes(ByteBuffer value) {
        int length = value.remaining();
        digest.update((byte) (length >>> 24));
        digest.update((byte) (length >>> 16));
        digest.update((byte) (length >>> 8));
        digest.update((byte) length);
        digest.update(value);
    }

    byte[] result() { return result; }
    @Override public void close() { work.clear(); buffer.release(); }
}
*///?}
