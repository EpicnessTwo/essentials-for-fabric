package com.essentialsforfabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.essentialsforfabric.util.PermissionUtil;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.util.Formatting;

public class TeleportCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("tp")
            .requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.argument("target", EntityArgumentType.player())
                .executes(context -> teleportToPlayer(context))));

        dispatcher.register(CommandManager.literal("tphere")
            .requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(context -> teleportPlayerHere(context))));

        // Teleport request commands
        dispatcher.register(CommandManager.literal("tpa")
            .requires(source -> PermissionUtil.hasPermission(source, "essentials.tpa", 0))
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(context -> sendTeleportRequest(context))));

        dispatcher.register(CommandManager.literal("tpaccept")
            .requires(source -> PermissionUtil.hasPermission(source, "essentials.tpaccept", 0))
            .executes(context -> acceptTeleportRequest(context)));

        dispatcher.register(CommandManager.literal("tpdeny")
            .requires(source -> PermissionUtil.hasPermission(source, "essentials.tpdeny", 0))
            .executes(context -> denyTeleportRequest(context)));
    }

    private static int teleportToPlayer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");

        if (player == target) {
            context.getSource().sendError(Text.literal("Cannot teleport to yourself"));
            return 0;
        }

        player.teleport(target.getServerWorld(), target.getX(), target.getY(), target.getZ(), 
                       target.getYaw(), target.getPitch());
        context.getSource().sendFeedback(() -> Text.literal("Teleported to " + target.getGameProfile().getName()), false);
        return 1;
    }

    private static int teleportPlayerHere(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");

        if (player == target) {
            context.getSource().sendError(Text.literal("Cannot teleport yourself to yourself"));
            return 0;
        }

        target.teleport(player.getServerWorld(), player.getX(), player.getY(), player.getZ(), 
                       target.getYaw(), target.getPitch());
        context.getSource().sendFeedback(() -> Text.literal("Teleported " + target.getGameProfile().getName() + " to you"), true);
        target.sendMessage(Text.literal("You have been teleported to " + player.getGameProfile().getName()));
        return 1;
    }

    // --- TPA system ---
    private static final java.util.Map<java.util.UUID, TeleportRequest> PENDING_REQUESTS = new java.util.HashMap<>();
    private static final long REQUEST_TTL_MILLIS = 60_000L; // 60 seconds

    private static class TeleportRequest {
        final java.util.UUID requester;
        final java.util.UUID target;
        final long createdAt;
        final String requesterName;

        TeleportRequest(java.util.UUID requester, java.util.UUID target, String requesterName) {
            this.requester = requester;
            this.target = target;
            this.requesterName = requesterName;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > REQUEST_TTL_MILLIS;
        }
    }

    private static int sendTeleportRequest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity requester = context.getSource().getPlayerOrThrow();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");

        if (requester.getUuid().equals(target.getUuid())) {
            context.getSource().sendError(Text.literal("You cannot send a teleport request to yourself"));
            return 0;
        }

        TeleportRequest req = new TeleportRequest(requester.getUuid(), target.getUuid(), requester.getGameProfile().getName());
        synchronized (PENDING_REQUESTS) {
            PENDING_REQUESTS.put(target.getUuid(), req);
        }

        requester.sendMessage(Text.literal("Teleport request sent to " + target.getGameProfile().getName()).formatted(Formatting.GRAY));

        MutableText actions = Text.literal("")
            .append(Text.literal("[Accept]")
                .styled(style -> style
                    .withColor(Formatting.GREEN)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Accept teleport request")))))
            .append(Text.literal(" "))
            .append(Text.literal("[Deny]")
                .styled(style -> style
                    .withColor(Formatting.RED)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Deny teleport request")))));

        target.sendMessage(Text.literal(req.requesterName + " has requested to teleport to you.").formatted(Formatting.AQUA));
        target.sendMessage(actions);

        return 1;
    }

    private static int acceptTeleportRequest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = context.getSource().getPlayerOrThrow();
        TeleportRequest req;
        synchronized (PENDING_REQUESTS) {
            req = PENDING_REQUESTS.get(target.getUuid());
        }

        if (req == null) {
            context.getSource().sendError(Text.literal("You have no pending teleport requests"));
            return 0;
        }

        if (req.isExpired()) {
            synchronized (PENDING_REQUESTS) {
                PENDING_REQUESTS.remove(target.getUuid());
            }
            context.getSource().sendError(Text.literal("The teleport request has expired"));
            return 0;
        }

        ServerPlayerEntity requester = target.getServer().getPlayerManager().getPlayer(req.requester);
        if (requester == null) {
            synchronized (PENDING_REQUESTS) {
                PENDING_REQUESTS.remove(target.getUuid());
            }
            context.getSource().sendError(Text.literal("The requesting player is no longer online"));
            return 0;
        }

        requester.teleport(target.getServerWorld(), target.getX(), target.getY(), target.getZ(), requester.getYaw(), requester.getPitch());

        requester.sendMessage(Text.literal("Your teleport request to " + target.getGameProfile().getName() + " was accepted").formatted(Formatting.GREEN));
        target.sendMessage(Text.literal("Accepted teleport request from " + req.requesterName).formatted(Formatting.GREEN));

        synchronized (PENDING_REQUESTS) {
            PENDING_REQUESTS.remove(target.getUuid());
        }

        return 1;
    }

    private static int denyTeleportRequest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = context.getSource().getPlayerOrThrow();
        TeleportRequest req;
        synchronized (PENDING_REQUESTS) {
            req = PENDING_REQUESTS.get(target.getUuid());
        }

        if (req == null) {
            context.getSource().sendError(Text.literal("You have no pending teleport requests"));
            return 0;
        }

        ServerPlayerEntity requester = target.getServer().getPlayerManager().getPlayer(req.requester);
        if (requester != null) {
            requester.sendMessage(Text.literal("Your teleport request to " + target.getGameProfile().getName() + " was denied").formatted(Formatting.RED));
        }
        target.sendMessage(Text.literal("Denied teleport request from " + req.requesterName).formatted(Formatting.RED));

        synchronized (PENDING_REQUESTS) {
            PENDING_REQUESTS.remove(target.getUuid());
        }

        return 1;
    }
}
