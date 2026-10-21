package fr.minenorth.immo;

import fr.minenorth.immo.network.ImmoNetwork;
import fr.minenorth.portes.agency.AgencyService;
import fr.minenorth.portes.agency.Catalogues;
import fr.minenorth.portes.door.EntrepriseCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Point d'entrée unique de l'agence : la commande de test aujourd'hui, le PNJ agent immobilier demain. */
public final class AgencyMenu {
    private AgencyMenu() {}

    public static final int TAB_CATALOG = 0, TAB_MINE = 1;

    /** Ouvre l'agence (biens sans catalogue). */
    public static void open(ServerPlayer p) { open(p, ""); }

    /** Ouvre un catalogue : « » = l'agence, sinon le catalogue d'un partenaire (ex. « casino »). */
    public static void open(ServerPlayer p, String catalogue) { open(p, TAB_CATALOG, "", true, catalogue); }

    public static void open(ServerPlayer p, int tab, String message, boolean ok, String catalogue) {
        String cat = Catalogues.normalize(catalogue);
        var data = new ImmoNetwork.AgencyDataPacket(tab, message, ok, AgencyService.catalog(p.server, cat), AgencyService.myProperties(p), companiesOf(p),
                p.hasPermissions(2) ? AgencyService.catalogAll(p.server) : List.of(), cat);
        ImmoNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), data);
    }

    /** Entreprises dont le joueur est patron, au format « id|nom ». */
    private static List<String> companiesOf(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        for (String c : EntrepriseCompat.companies(p.server)) {
            try {
                if (EntrepriseCompat.isOwner(p, Integer.parseInt(c.substring(0, c.indexOf('|'))))) out.add(c);
            } catch (RuntimeException ignored) {}
        }
        return out;
    }
}
