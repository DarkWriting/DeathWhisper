package com.example.deathwhisper;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class WhisperCommands {
    private final StalkingManager manager;
    public WhisperCommands(StalkingManager manager) { this.manager = manager; }
    @SubscribeEvent public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("deathwhisper").requires(s -> s.hasPermission(2))
          .then(Commands.literal("force").executes(c -> spawn(c.getSource().getPlayerOrException()))
            .then(Commands.argument("player", EntityArgument.player()).executes(c -> spawn(EntityArgument.getPlayer(c, "player")))))
          .then(Commands.literal("status").executes(c -> {
              ServerPlayer p = c.getSource().getPlayerOrException();
              c.getSource().sendSuccess(() -> Component.literal(manager.status(p)), false);
              return 1;
          }).then(Commands.argument("player", EntityArgument.player()).executes(c -> {
              ServerPlayer p = EntityArgument.getPlayer(c, "player");
              c.getSource().sendSuccess(() -> Component.literal(manager.status(p)), false);
              return 1;
          })))
          .then(Commands.literal("clear").executes(c -> {
              ServerPlayer p = c.getSource().getPlayerOrException();
              manager.clear(p);
              c.getSource().sendSuccess(() -> Component.literal("Death Whisper reset for " + p.getName().getString()), true);
              return 1;
          }).then(Commands.argument("player", EntityArgument.player()).executes(c -> {
              ServerPlayer p = EntityArgument.getPlayer(c, "player");
              manager.clear(p);
              c.getSource().sendSuccess(() -> Component.literal("Death Whisper reset for " + p.getName().getString()), true);
              return 1;
          })))
          .then(Commands.literal("threat").then(Commands.argument("player", EntityArgument.player())
              .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100))
              .executes(c -> { ServerPlayer p = EntityArgument.getPlayer(c, "player"); manager.setThreat(p, IntegerArgumentType.getInteger(c, "amount")); return 1; }))))
        );
    }
    private int spawn(ServerPlayer player) {
        return manager.force(player) ? 1 : 0;
    }
}
