package li.cil.scannable.client.scanning.filter;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Objects;
import java.util.function.Predicate;

@OnlyIn(Dist.CLIENT)
public record EntityTypeScanFilter(EntityType<?> entityType) implements Predicate<Entity> {
    @Override
    public boolean test(final Entity entity) {
        return Objects.equals(entityType, entity.getType());
    }
}
