package fr.minenorth.immo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Pas de commande pour les joueurs : l'agence s'ouvre par le PNJ (tag « agence », voir AgencyNpc) ou par un dialogue de PNJ.
 * /agence ouvrir &lt;joueur&gt; [catalogue] : ouvre l'agence (ou le catalogue d'un partenaire, ex. « casino ») pour un joueur (OP ou console).
 * Sert aux dialogues des PNJ CustomNPCs : dans l'option de dialogue, mettre « agence ouvrir @dp » (@dp = le joueur qui parle au PNJ),
 * ou « agence ouvrir @dp casino » pour les chambres du casino.
 */
@Mod.EventBusSubscriber
public final class AgencyCommand {
    private AgencyCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("agence").requires(src -> src.hasPermission(2))
                .then(Commands.literal("ouvrir")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("joueur", EntityArgument.player())
                                .executes(ctx -> {
                                    AgencyMenu.open(EntityArgument.getPlayer(ctx, "joueur"));
                                    return 1;
                                })
                                .then(Commands.argument("catalogue", StringArgumentType.word())
                                        .executes(ctx -> {
                                            AgencyMenu.open(EntityArgument.getPlayer(ctx, "joueur"), StringArgumentType.getString(ctx, "catalogue"));
                                            return 1;
                                        })))));
    }
}
