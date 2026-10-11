package com.example.deathwhisper;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Player-accessible case commands; no OP privilege required. */
public final class InvestigationCommands {
    private final InvestigationManager manager;
    public InvestigationCommands(InvestigationManager manager){this.manager=manager;}
    @SubscribeEvent public void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("deathwhisperinvestigate")
          .then(Commands.literal("journal").executes(c->{
              return manager.showJournal(c.getSource().getPlayerOrException())?1:0;
          }))
          .then(Commands.literal("manifest").requires(c->c.hasPermission(2)).executes(c->
              manager.forceManifest(c.getSource().getPlayerOrException())?1:0))
          .then(Commands.literal("diagnose").requires(c->c.hasPermission(2)).executes(c->{
              var player=c.getSource().getPlayerOrException();
              c.getSource().sendSuccess(()->Component.literal(manager.diagnostics(player)),false);
              return 1;
          }))
          .then(Commands.literal("test").requires(c->c.hasPermission(2))
            .then(Commands.literal("footprints").executes(c->
                manager.forceFootprints(c.getSource().getPlayerOrException())?1:0)))
          .then(Commands.literal("types").executes(c->{
              c.getSource().sendSuccess(()->Component.literal(
                "Possible ghost profiles: "+java.util.Arrays.toString(GhostProfile.values())),false);
              return 1;
          }))
          .then(Commands.literal("guess")
            .then(Commands.argument("ghost",StringArgumentType.word())
               .suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(
                  java.util.Arrays.stream(GhostProfile.values()).map(g->g.name().toLowerCase()).toList(),b))
               .executes(c->manager.guess(c.getSource().getPlayerOrException(),
                       StringArgumentType.getString(c,"ghost")))))
        );
    }
}
