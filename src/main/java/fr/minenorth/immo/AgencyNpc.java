package fr.minenorth.immo;

import fr.minenorth.portes.agency.Catalogues;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * PNJ agent immobilier : tout PNJ (CustomNPCs ou autre) qui porte le tag {@value #TAG} ouvre l'agence au clic droit ;
 * le tag « agence:nom » ouvre à la place le catalogue « nom » (ex. « agence:casino » pour les chambres du casino).
 * Mise en place : se placer près du PNJ puis /tag @e[type=!player,limit=1,sort=nearest,distance=..3] add agence
 * (retrait : /tag ... remove agence). Le serveur reçoit le clic sur l'entité, donc le joueur est forcément à portée.
 */
@Mod.EventBusSubscriber
public final class AgencyNpc {
    private AgencyNpc() {}

    public static final String TAG = "agence";

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p)) return;
        Entity target = e.getTarget();
        if (target instanceof ServerPlayer) return;
        String catalogue = null;
        for (String tag : target.getTags()) {
            catalogue = Catalogues.fromNpcTag(tag);
            if (catalogue != null) break;
        }
        if (catalogue == null) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        AgencyMenu.open(p, catalogue);
    }
}
