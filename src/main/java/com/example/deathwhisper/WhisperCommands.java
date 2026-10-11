package com.example.deathwhisper;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
          .then(Commands.literal("scenario")
            .then(Commands.argument("kind", StringArgumentType.word())
                .suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(EncounterKind.values()).map(k->k.name().toLowerCase()).toList(),b))
                .executes(c->spawnKind(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"kind")))))
          .then(Commands.literal("scenarioPlayer")
            .then(Commands.argument("player", EntityArgument.player())
            .then(Commands.argument("kind", StringArgumentType.word())
                .suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(EncounterKind.values()).map(k->k.name().toLowerCase()).toList(),b))
                .executes(c->spawnKind(EntityArgument.getPlayer(c,"player"),StringArgumentType.getString(c,"kind"))))))
          .then(Commands.literal("debug").executes(c-> {
              ServerPlayer p=c.getSource().getPlayerOrException();
              c.getSource().sendSuccess(()->Component.literal(manager.diagnostics(p)),false);return 1;
          }).then(Commands.argument("player",EntityArgument.player()).executes(c->{
              ServerPlayer p=EntityArgument.getPlayer(c,"player");
              c.getSource().sendSuccess(()->Component.literal(manager.diagnostics(p)),false);return 1;
          })))
          .then(Commands.literal("memory").executes(c-> {
              ServerPlayer p=c.getSource().getPlayerOrException();
              c.getSource().sendSuccess(()->Component.literal(manager.memory(p)),false);return 1;
          }).then(Commands.argument("player",EntityArgument.player()).executes(c->{
              ServerPlayer p=EntityArgument.getPlayer(c,"player");
              c.getSource().sendSuccess(()->Component.literal(manager.memory(p)),false);return 1;
          })))
          .then(Commands.literal("test").then(Commands.argument("mode",StringArgumentType.word())
             .suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(
                java.util.List.of("peek","charge","whistle"),b))
             .executes(c->manager.diagnostic(c.getSource().getPlayerOrException(),
                StringArgumentType.getString(c,"mode"))?1:0)))
          .then(Commands.literal("scene").then(Commands.argument("kind", StringArgumentType.word())
              .executes(c -> {
                  ServerPlayer p = c.getSource().getPlayerOrException();
                  EncounterKind kind = EncounterKind.parse(StringArgumentType.getString(c,"kind"));
                  if(kind==null) {
                      c.getSource().sendFailure(Component.literal("Invalid scene. Choose: "+java.util.Arrays.toString(EncounterKind.values())));
                      return 0;
                  }
                  return manager.force(p,kind)?1:0;
              })))
          .then(Commands.literal("debug").executes(c -> {
              ServerPlayer p=c.getSource().getPlayerOrException();
              c.getSource().sendSuccess(() -> Component.literal(manager.diagnostics(p)),false);
              return 1;
          }))
          .then(Commands.literal("memory").executes(c -> {
              ServerPlayer p=c.getSource().getPlayerOrException();
              c.getSource().sendSuccess(() -> Component.literal(manager.memory(p)),false);
              return 1;
          }))
          .then(Commands.literal("powers").executes(c->{
              ServerPlayer p=c.getSource().getPlayerOrException();
              c.getSource().sendSuccess(()->Component.literal(DeathWhisper.FRAMEWORK.status(p)),false);
              return 1;
          }).then(Commands.argument("player",EntityArgument.player()).executes(c->{
              ServerPlayer p=EntityArgument.getPlayer(c,"player");
              c.getSource().sendSuccess(()->Component.literal(DeathWhisper.FRAMEWORK.status(p)),false);
              return 1;
          })))
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
        // Network panel commands are player-accessible but ALWAYS require holding
        // a linked controller; the networking class performs distance and chunk checks.
        event.getDispatcher().register(Commands.literal("deathwhispersecurity")
            .then(Commands.literal("list").executes(c->{
                SecurityDoorController.showPanel(c.getSource().getPlayerOrException());return 1;
            }))
            .then(Commands.literal("scan").then(Commands.argument("door",IntegerArgumentType.integer(1,12))
                .executes(c->SecurityDoorController.scan(c.getSource().getPlayerOrException(),
                    IntegerArgumentType.getInteger(c,"door")))))
            .then(Commands.literal("travel").then(Commands.argument("door",IntegerArgumentType.integer(1,12))
                .executes(c->SecurityDoorController.travel(c.getSource().getPlayerOrException(),
                    IntegerArgumentType.getInteger(c,"door")))))
        );
    }
    private int spawnKind(ServerPlayer p,String kind) {
        EncounterKind script=EncounterKind.parse(kind);
        return script!=null && manager.force(p,script)?1:0;
    }
    private int spawn(ServerPlayer player) {
        return manager.force(player) ? 1 : 0;
    }
}
