package com.essentialsforfabric.commands;

import com.essentialsforfabric.data.PlayerDataManager;
import com.essentialsforfabric.util.PermissionUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import com.essentialsforfabric.util.WorldUtil;

import java.util.Map;

public class WarpCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("warp")
            .requires(source -> PermissionUtil.hasPermission(source, "essentials.warp", 0))
            .executes(context -> listWarps(context))
            .then(CommandManager.argument("name", StringArgumentType.string())
                .executes(context -> warp(context, StringArgumentType.getString(context, "name")))));

        dispatcher.register(CommandManager.literal("warps")
            .requires(source -> PermissionUtil.hasPermission(source, "essentials.warp", 0))
            .executes(WarpCommands::listWarps));

        dispatcher.register(CommandManager.literal("setwarp")
            .requires(source -> PermissionUtil.hasPermission(source, "essentials.setwarp", 2))
            .then(CommandManager.argument("name", StringArgumentType.string())
                .executes(context -> setWarp(context, StringArgumentType.getString(context, "name")))));

        dispatcher.register(CommandManager.literal("delwarp")
            .requires(source -> PermissionUtil.hasPermission(source, "essentials.delwarp", 2))
            .then(CommandManager.argument("name", StringArgumentType.string())
                .executes(context -> deleteWarp(context, StringArgumentType.getString(context, "name")))));
    }

    private static int warp(CommandContext<ServerCommandSource> context, String warpName) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        PlayerDataManager.WarpData warp = PlayerDataManager.getWarp(warpName);

        if (warp == null) {
            context.getSource().sendError(Text.literal("Warp '" + warpName + "' not found"));
            return 0;
        }

        PlayerDataManager.setLastLocation(player);

        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, new Identifier(warp.world));
        ServerWorld world = context.getSource().getServer().getWorld(worldKey);

        if (world == null) {
            context.getSource().sendError(Text.literal("Warp world not found"));
            return 0;
        }

        player.teleport(world, warp.x, warp.y, warp.z, warp.yaw, warp.pitch);
        context.getSource().sendFeedback(() -> Text.literal("Teleported to warp: " + warpName), false);

        return 1;
    }

    private static int setWarp(CommandContext<ServerCommandSource> context, String warpName) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        PlayerDataManager.setWarp(warpName, player);
        context.getSource().sendFeedback(() -> Text.literal("Warp '" + warpName + "' set"), true);

        return 1;
    }

    private static int deleteWarp(CommandContext<ServerCommandSource> context, String warpName) {
        PlayerDataManager.WarpData warp = PlayerDataManager.getWarp(warpName);

        if (warp == null) {
            context.getSource().sendError(Text.literal("Warp '" + warpName + "' not found"));
            return 0;
        }

        PlayerDataManager.deleteWarp(warpName);
        context.getSource().sendFeedback(() -> Text.literal("Warp '" + warpName + "' deleted"), true);

        return 1;
    }

    private static int listWarps(CommandContext<ServerCommandSource> context) {
        Map<String, PlayerDataManager.WarpData> warps = PlayerDataManager.getAllWarps();

        if (warps.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No warps available"), false);
            return 0;
        }

        int count = warps.size();
        context.getSource().sendFeedback(() -> Text.literal("Available warps (" + count + "):"), false);

        warps.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
            .forEachOrdered(entry -> {
                String name = entry.getKey();
                PlayerDataManager.WarpData loc = entry.getValue();

                String worldLabel = WorldUtil.readableWorld(loc.world);
                int ix = (int) Math.floor(loc.x);
                int iy = (int) Math.floor(loc.y);
                int iz = (int) Math.floor(loc.z);

                MutableText line = Text.literal(" - ")
                    .append(
                        Text.literal("[Teleport]")
                            .styled(style -> style
                                .withColor(Formatting.GREEN)
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/warp " + name))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to teleport to '" + name + "'"))))
                    )
                    .append(Text.literal("  "))
                    .append(Text.literal(name).formatted(Formatting.AQUA))
                    .append(Text.literal("  "))
                    .append(Text.literal("[" + worldLabel + "] ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(ix + ", " + iy + ", " + iz).formatted(Formatting.GRAY))
                    .append(Text.literal("  "));

                context.getSource().sendFeedback(() -> line, false);
            });

        return count;
    }

}
