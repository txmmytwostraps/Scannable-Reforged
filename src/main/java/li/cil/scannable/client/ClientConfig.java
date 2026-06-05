package li.cil.scannable.client;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import li.cil.scannable.util.config.*;
import net.minecraft.util.Util;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.Tags;

@Type(ConfigType.CLIENT)
public final class ClientConfig {
    @WorldRestart
    @Comment("""
        The colors for blocks used when rendering their result bounding box
        by block name. Each entry must be a key-value pair separated by a `=`,
        with the key being the tag name and the value being the hexadecimal
        RGB value of the color.""")
    @KeyValueTypes(keyType = Identifier.class, valueType = int.class,
        valueSerializer = @CustomSerializer(serializer = "toHexString", deserializer = "fromHexString"))
    public static Object2IntMap<Identifier> blockColors = new Object2IntOpenHashMap<>();

    @WorldRestart
    @Comment("The colors for blocks used when rendering their result bounding box\n" +
        "by block tag. See `blockColors` for format entries have to be in.")
    @KeyValueTypes(keyType = Identifier.class, valueType = int.class,
        valueSerializer = @CustomSerializer(serializer = "toHexString", deserializer = "fromHexString"))
    public static Object2IntMap<Identifier> blockTagColors = getDefaultBlockTagColors();

    @WorldRestart
    @Comment("The colors for fluids used when rendering their result bounding box\n" +
        "by fluid name. See `blockColors` for format entries have to be in.")
    @KeyValueTypes(keyType = Identifier.class, valueType = int.class,
        valueSerializer = @CustomSerializer(serializer = "toHexString", deserializer = "fromHexString"))
    public static Object2IntMap<Identifier> fluidColors = new Object2IntOpenHashMap<>();

    @WorldRestart
    @Comment("The colors for fluids used when rendering their result bounding box\n" +
        "by fluid tag. See `blockColors` for format entries have to be in.")
    @KeyValueTypes(keyType = Identifier.class, valueType = int.class,
        valueSerializer = @CustomSerializer(serializer = "toHexString", deserializer = "fromHexString"))
    public static Object2IntMap<Identifier> fluidTagColors = Util.make(new Object2IntOpenHashMap<>(), c -> {
        c.put(FluidTags.WATER.location(), MapColor.WATER.col);
        c.put(FluidTags.LAVA.location(), MapColor.TERRACOTTA_ORANGE.col);
    });

    @SuppressWarnings("unused") // Referenced in annotations.
    public static String toHexString(final Object value) {
        return "0x" + Integer.toHexString((int) value);
    }

    @SuppressWarnings("unused") // Referenced in annotations.
    public static Object fromHexString(final String value) {
        return Integer.decode(value);
    }

    private static Object2IntMap<Identifier> getDefaultBlockTagColors() {
        return Util.make(new Object2IntOpenHashMap<>(), c -> {
            // Minecraft
            c.put(Tags.Blocks.ORES_COAL.location(), MapColor.COLOR_GRAY.col);
            c.put(Tags.Blocks.ORES_IRON.location(), MapColor.COLOR_BROWN.col); // MaterialColor.IRON is also gray, so...
            c.put(Tags.Blocks.ORES_GOLD.location(), MapColor.GOLD.col);
            c.put(Tags.Blocks.ORES_LAPIS.location(), MapColor.LAPIS.col);
            c.put(Tags.Blocks.ORES_DIAMOND.location(), MapColor.DIAMOND.col);
            c.put(Tags.Blocks.ORES_REDSTONE.location(), MapColor.COLOR_RED.col);
            c.put(Tags.Blocks.ORES_EMERALD.location(), MapColor.EMERALD.col);
            c.put(Tags.Blocks.ORES_QUARTZ.location(), MapColor.QUARTZ.col);

            // Common modded ores
            c.put(Identifier.fromNamespaceAndPath("c", "ores/tin"), MapColor.COLOR_CYAN.col);
            c.put(Identifier.fromNamespaceAndPath("c", "ores/copper"), MapColor.TERRACOTTA_ORANGE.col);
            c.put(Identifier.fromNamespaceAndPath("c", "ores/lead"), MapColor.TERRACOTTA_BLUE.col);
            c.put(Identifier.fromNamespaceAndPath("c", "ores/silver"), MapColor.COLOR_LIGHT_GRAY.col);
            c.put(Identifier.fromNamespaceAndPath("c", "ores/nickel"), MapColor.COLOR_LIGHT_BLUE.col);
            c.put(Identifier.fromNamespaceAndPath("c", "ores/platinum"), MapColor.TERRACOTTA_WHITE.col);
            c.put(Identifier.fromNamespaceAndPath("c", "ores/mithril"), MapColor.COLOR_PURPLE.col);
        });
    }
}
