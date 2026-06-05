package li.cil.scannable.util;

import li.cil.scannable.common.neoforge.ModEventBus;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

public final class RegistryUtils {
    private enum Phase {
        PRE_INIT,
        INIT,
        POST_INIT,
    }

    private static final List<DeferredRegister<?>> ENTRIES = new ArrayList<>();
    private static Phase phase = Phase.PRE_INIT;
    private static String modId;

    public static <T> DeferredRegister<T> get(final ResourceKey<? extends Registry<T>> registryKey) {
        if (phase != Phase.INIT) throw new IllegalStateException();

        final DeferredRegister<T> entry = DeferredRegister.create(registryKey, modId);
        ENTRIES.add(entry);
        return entry;
    }

    public static void begin(final String modId) {
        RegistryUtils.modId = modId;
        if (phase != Phase.PRE_INIT) throw new IllegalStateException();
        phase = Phase.INIT;
    }

    public static void finish() {
        if (phase != Phase.INIT) throw new IllegalStateException();
        phase = Phase.POST_INIT;

        for (final DeferredRegister<?> register : ENTRIES) {
            register.register(ModEventBus.INSTANCE);
        }

        ENTRIES.clear();
    }

    private RegistryUtils() {
    }
}
