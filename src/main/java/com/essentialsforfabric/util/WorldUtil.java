package com.essentialsforfabric.util;

import net.minecraft.util.Identifier;

public class WorldUtil {
    public static String readableWorld(String worldId) {
        try {
            Identifier id = new Identifier(worldId);
            String path = id.getPath();
            return switch (path) {
                case "overworld" -> "Overworld";
                case "the_nether" -> "Nether";
                case "the_end" -> "End";
                default -> id.toString();
            };
        } catch (Exception e) {
            return worldId;
        }
    }
}

