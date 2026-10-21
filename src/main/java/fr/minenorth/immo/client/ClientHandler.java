package fr.minenorth.immo.client;

import fr.minenorth.immo.AgencyMenu;
import fr.minenorth.immo.network.ImmoNetwork;
import net.minecraft.client.Minecraft;

/** Réception côté client : ouvre l'écran voulu et range les images reçues. */
public final class ClientHandler {
    private ClientHandler() {}

    public static void open(ImmoNetwork.AgencyDataPacket data) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(data.tab() == AgencyMenu.TAB_MINE ? new MyPropertiesScreen(data) : new CatalogScreen(data));
    }

    public static void imageReceived(ImmoNetwork.ImagePacket p) {
        ImageCache.put(p.dimension(), p.houseName(), p.index(), p.jpeg());
    }
}
