package fr.minenorth.immo.client;

import fr.minenorth.immo.AgencyMenu;
import fr.minenorth.immo.network.ImmoNetwork;
import fr.minenorth.portes.agency.AgencyService.PropertyView;
import fr.minenorth.portes.agency.PropertyCategory;
import fr.minenorth.portes.client.MineNorthButton;
import fr.minenorth.portes.client.MineNorthStyle;
import fr.minenorth.portes.door.OwnerDoorData.ListingMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Fiche d'un bien : aperçu du bâtiment, informations, carte, achat ou location. */
public class PropertyScreen extends Screen {
    private static final int W = 560, H = 400;
    private final ImmoNetwork.AgencyDataPacket data;
    private final PropertyView view;
    private final boolean available;
    private int image = 0;
    /** Payeur : -1 = compte personnel (seulement pour local, stockage, garage), sinon index dans data.companies(). */
    private int company;

    public PropertyScreen(ImmoNetwork.AgencyDataPacket data, PropertyView view, boolean available) {
        super(Component.literal(view.houseName()));
        this.data = data;
        this.view = view;
        this.available = available;
        this.company = view.category().companyOptional() ? -1 : 0;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return Math.max(8, (height - H) / 2); }

    private MineNorthButton btn(int x, int y, int w, int h, String label, int color, Runnable r) {
        return addRenderableWidget(new MineNorthButton(x, y, w, h, Component.literal(label), color, r));
    }

    private void act(int action, int companyId) {
        ImmoNetwork.CHANNEL.sendToServer(new ImmoNetwork.AgencyActionPacket(action, AgencyMenu.TAB_CATALOG, view.dimension(), view.houseName(), companyId, data.catalogue()));
    }

    private int selectedCompanyId() {
        List<String> list = data.companies();
        if (list.isEmpty() || company < 0) return 0;
        company = Math.max(company < 0 ? -1 : 0, Math.min(list.size() - 1, company));
        try { return Integer.parseInt(list.get(company).substring(0, list.get(company).indexOf('|'))); } catch (RuntimeException e) { return 0; }
    }

    private String selectedCompanyName() {
        List<String> list = data.companies();
        if (list.isEmpty() || company < 0) return "MOI (compte perso)";
        company = Math.max(company < 0 ? -1 : 0, Math.min(list.size() - 1, company));
        return list.get(company).substring(list.get(company).indexOf('|') + 1);
    }

    @Override
    protected void init() {
        super.init();
        int x = left(), y = top();
        btn(x + W - 100, y + 10, 86, 16, "Retour", MineNorthButton.GHOST, () -> Minecraft.getInstance().setScreen(new CatalogScreen(data)));
        btn(x + 20, y + 258, 40, 18, "◀", MineNorthStyle.DARK, () -> image = Math.max(0, image - 1)).enabled(view.imageCount() > 1);
        btn(x + 66, y + 258, 40, 18, "▶", MineNorthStyle.DARK, () -> image = Math.min(view.imageCount() - 1, image + 1)).enabled(view.imageCount() > 1);
        int rx = x + 360, rw = W - 360 - 20;
        btn(rx, y + 190, rw, 20, "VOIR SUR LA CARTE", MineNorthStyle.CYAN, () -> act(ImmoNetwork.SHOW_ON_MAP, 0));
        if (!data.all().isEmpty()) btn(rx, y + 214, rw, 20, "ADMIN : MODIFIER LA PORTE", MineNorthStyle.PINK, () -> act(ImmoNetwork.ADMIN_EDIT, 0));
        boolean companyOnly = view.category().companyOnly();
        boolean choice = view.category().companyOptional() && !data.companies().isEmpty();
        int ay = y + 318;
        if ((companyOnly && !data.companies().isEmpty()) || choice) {
            btn(rx, y + 290, rw, 20, "PAYEUR : " + selectedCompanyName(), MineNorthStyle.DARK, () -> {
                int n = data.companies().size();
                company = choice ? (company + 1 >= n ? -1 : company + 1) : (company + 1) % n;
                rebuildWidgets();
            });
        }
        boolean rental = view.mode() == ListingMode.RENTAL;
        boolean noCompany = companyOnly && data.companies().isEmpty();
        String label = !available ? "NON DISPONIBLE" : noCompany ? "RÉSERVÉ AUX PATRONS D'ENTREPRISE" : rental ? "LOUER" : "ACHETER";
        btn(rx, ay, rw, 30, label, available && !noCompany ? (rental ? MineNorthStyle.CYAN : MineNorthStyle.GREEN) : MineNorthStyle.DARK,
                () -> act(rental ? ImmoNetwork.RENT : ImmoNetwork.BUY, companyOnly || choice ? selectedCompanyId() : 0))
                .enabled(available && !noCompany);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = left(), y = top();
        MineNorthStyle.panel(g, x, y, W, H, view.houseName().toUpperCase(), CatalogScreen.categoryLabel(view.category()).toUpperCase());
        int iw = 320, ih = 180, ix = x + 20, iy = y + 62;
        g.fill(ix, iy, ix + iw, iy + ih, 0xFF0E0A34);
        ResourceLocation tex = view.imageCount() > 0 ? ImageCache.get(view.dimension(), view.houseName(), image) : null;
        if (tex != null) g.blit(tex, ix, iy, iw, ih, 0f, 0f, ImageCache.WIDTH, ImageCache.HEIGHT, ImageCache.WIDTH, ImageCache.HEIGHT);
        else g.drawCenteredString(font, view.imageCount() > 0 ? "Chargement…" : "Aucun aperçu disponible", ix + iw / 2, iy + ih / 2 - 4, MineNorthStyle.MUTED);
        if (view.imageCount() > 1) g.drawString(font, (image + 1) + " / " + view.imageCount(), x + 116, y + 263, MineNorthStyle.MUTED, false);

        int rx = x + 360;
        boolean rental = view.mode() == ListingMode.RENTAL;
        g.drawString(font, rental ? "LOCATION" : "VENTE", rx, y + 62, MineNorthStyle.BLUE, false);
        g.drawString(font, MineNorthStyle.euros(view.priceCents()) + (rental ? " pour " + view.days() + " jour(s)" : ""), rx, y + 76, MineNorthStyle.OK, false);
        g.drawString(font, view.furnished() ? "Meublé : oui" : "Meublé : non", rx, y + 94, MineNorthStyle.TEXT, false);
        g.drawWordWrap(font, Component.literal(view.description().isBlank() ? "Pas de description." : view.description()), rx, y + 112, W - 380, MineNorthStyle.MUTED);
        if (view.category().companyAllowed()) {
            boolean only = view.category().companyOnly();
            String hint = only ? (data.companies().isEmpty() ? "Réservé aux patrons d'entreprise." : "Payé par le compte de l'entreprise.")
                    : (data.companies().isEmpty() ? "Paiement personnel." : "Paiement personnel ou par une de vos entreprises.");
            g.drawString(font, hint, rx, y + 276, only && data.companies().isEmpty() ? MineNorthStyle.ALERT : MineNorthStyle.MUTED, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
