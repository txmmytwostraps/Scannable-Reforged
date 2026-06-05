package li.cil.scannable.client.scanning.filter;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.function.Predicate;

@OnlyIn(Dist.CLIENT)
public enum FriendlyEntityScanFilter implements Predicate<Entity> {
    INSTANCE;

    @Override
    public boolean test(final Entity entity) {
        return entity instanceof LivingEntity &&
            !(entity instanceof Player) &&
            entity.getType().getCategory().isFriendly();
    }
}
