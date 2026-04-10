package com.tpforall;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TpForAll implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("tpforall");

    private static final Map<UUID, TpaRequest> pendingRequests = new HashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("TpForAll mod loaded!");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(
                CommandManager.literal("tpa")
                    .requires(source -> source.isExecutedByPlayer())
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity requester = getPlayer(ctx.getSource());
                            if (requester == null) return 0;

                            ServerPlayerEntity target = getTargetPlayer(ctx, "target");
                            if (target == null) return 0;

                            if (target.equals(requester)) {
                                ctx.getSource().sendError(Text.literal("Você não pode pedir teleporte para si mesmo."));
                                return 0;
                            }

                            pendingRequests.put(target.getUuid(), new TpaRequest(requester, target));

                            ctx.getSource().sendFeedback(() -> Text.literal(
                                "✉ Pedido enviado para " + target.getName().getString() +
                                ". Aguardando resposta... (expira em 60s)")
                                .formatted(Formatting.YELLOW), false);

                            MutableText msg = Text.literal(
                                "✉ " + requester.getName().getString() + " quer se teleportar até você!  ")
                                .formatted(Formatting.AQUA);

                            MutableText accept = Text.literal("[✔ Aceitar]")
                                .setStyle(Style.EMPTY
                                    .withColor(Formatting.GREEN)
                                    .withBold(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept")));

                            MutableText deny = Text.literal("  [✘ Recusar]")
                                .setStyle(Style.EMPTY
                                    .withColor(Formatting.RED)
                                    .withBold(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny")));

                            target.sendMessage(msg.append(accept).append(deny));
                            return 1;
                        })
                    )
            );

            dispatcher.register(
                CommandManager.literal("tpaccept")
                    .requires(source -> source.isExecutedByPlayer())
                    .executes(ctx -> {
                        ServerPlayerEntity target = getPlayer(ctx.getSource());
                        if (target == null) return 0;

                        TpaRequest req = pendingRequests.remove(target.getUuid());

                        if (req == null) {
                            ctx.getSource().sendError(Text.literal("Não há pedido de teleporte pendente para você."));
                            return 0;
                        }
                        if (req.isExpired()) {
                            ctx.getSource().sendError(Text.literal("O pedido de teleporte expirou."));
                            return 0;
                        }
                        if (req.requester.isDisconnected()) {
                            ctx.getSource().sendError(Text.literal(
                                req.requester.getName().getString() + " saiu do servidor."));
                            return 0;
                        }

                        ServerPlayerEntity requester = req.requester;
                        requester.teleport(target.getServerWorld(),
                                target.getX(), target.getY(), target.getZ(),
                                requester.getYaw(), requester.getPitch());

                        ctx.getSource().sendFeedback(() -> Text.literal(
                            "✔ Você aceitou o pedido de " + requester.getName().getString() + ".")
                            .formatted(Formatting.GREEN), false);

                        requester.sendMessage(Text.literal(
                            "✔ " + target.getName().getString() + " aceitou seu pedido!")
                            .formatted(Formatting.GREEN));
                        return 1;
                    })
            );

            dispatcher.register(
                CommandManager.literal("tpdeny")
                    .requires(source -> source.isExecutedByPlayer())
                    .executes(ctx -> {
                        ServerPlayerEntity target = getPlayer(ctx.getSource());
                        if (target == null) return 0;

                        TpaRequest req = pendingRequests.remove(target.getUuid());

                        if (req == null) {
                            ctx.getSource().sendError(Text.literal("Não há pedido de teleporte pendente para você."));
                            return 0;
                        }

                        ctx.getSource().sendFeedback(() -> Text.literal(
                            "✘ Você recusou o pedido de " + req.requester.getName().getString() + ".")
                            .formatted(Formatting.RED), false);

                        if (!req.requester.isDisconnected()) {
                            req.requester.sendMessage(Text.literal(
                                "✘ " + target.getName().getString() + " recusou seu pedido.")
                                .formatted(Formatting.RED));
                        }
                        return 1;
                    })
            );
        });
    }

    private ServerPlayerEntity getPlayer(ServerCommandSource source) {
        try {
            return source.getPlayerOrThrow();
        } catch (CommandSyntaxException e) {
            source.sendError(Text.literal("Este comando só pode ser usado por jogadores."));
            return null;
        }
    }

    private ServerPlayerEntity getTargetPlayer(CommandContext<ServerCommandSource> ctx, String argName) {
        try {
            return EntityArgumentType.getPlayer(ctx, argName);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendError(Text.literal("Jogador não encontrado."));
            return null;
        }
    }
}
