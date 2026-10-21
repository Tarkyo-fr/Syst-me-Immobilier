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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Catalogue de l'agence : grille de cartes avec filtres. Un clic sur une carte ouvre la fiche du bien. */
public class CatalogScreen extends Screen {
    static final int PANEL_W = 560, PANEL_H = 440;
    private static final int COLS = 3, ROWS = 2, CARD_W = 165, CARD_H = 130, GAP = 10;

    // Filtres gardés d'une ouverture à l'autre.
    private static int modeFilter = 0;       // 0 tous, 1 achat, 2 location
    private static int categoryFilter = -1;  // -1 toutes, sinon ordinal de PropertyCategory
    private static boolean furnishedOnly = false;
    private static String maxPrice = "";
    /** Admin : afficher aussi les biens vendus, loués ou hors vente. */
    private static boolean adminAll = false;

    private final ImmoNetwork.AgencyDataPacket data;
    private int page = 0;
    private EditBox priceBox;
    private final List<PropertyView> shown = new ArrayList<>();

    public CatalogScreen(ImmoNetwork.AgencyDataPacket data) {
        super(Component.literal("Agence immobilière"));
        this.data = data;
    }

    private int left() { return (width - PANEL_W) / 2; }
    private int top() { return Math.max(8, (height - PANEL_H) / 2); }

    private MineNorthButton btn(int x, int y, int w, int h, String label, int color, Runnable r) {
        return addRenderableWidget(new MineNorthButton(x, y, w, h, Component.literal(label), color, r));
    }

    private void refilter() {
        shown.clear();
        long max = parseMax();
        for (PropertyView v : adminAll && !data.all().isEmpty() ? data.all() : data.catalog()) {
            if (modeFilter == 1 && v.mode() != ListingMode.SALE) continue;
            if (modeFilter == 2 && v.mode() != ListingMode.RENTAL) continue;
            if (categoryFilter >= 0 && v.category().ordinal() != categoryFilter) continue;
            if (furnishedOnly && !v.furnished()) continue;
            if (max > 0 && v.priceCents() > max) continue;
            shown.add(v);
        }
        page = Math.max(0, Math.min(page, Math.max(0, (shown.size() - 1) / (COLS * ROWS))));
    }

    private static long parseMax() {
        try { return Math.round(Double.parseDouble(maxPrice.replace(',', '.').trim()) * 100); } catch (RuntimeException e) { return 0; }
    }

    @Override
    protected void init() {
        super.init();
        int x = left(), y = top();
        btn(x + 20, y + 52, 130, 18, "CATALOGUE", MineNorthStyle.GREEN, () -> {});
        btn(x + 156, y + 52, 130, 18, "MES BIENS", MineNorthStyle.DARK, () -> Minecraft.getInstance().setScreen(new MyPropertiesScreen(data)));
        btn(x + PANEL_W - 100, y + 10, 86, 16, "Fermer", MineNorthButton.GHOST, this::onClose);
        if (!data.all().isEmpty()) btn(x + 292, y + 52, 200, 18, adminAll ? "ADMIN : TOUS LES BIENS" : "ADMIN : DISPONIBLES SEULS", adminAll ? MineNorthStyle.PINK : MineNorthStyle.DARK, () -> { adminAll = !adminAll; page = 0; rebuildWidgets(); });

        int fy = y + 78;
        String[] modes = {"TOUT", "ACHAT", "LOCATION"};
        btn(x + 20, fy, 100, 18, modes[modeFilter], MineNorthStyle.CYAN, () -> { modeFilter = (modeFilter + 1) % 3; page = 0; rebuildWidgets(); });
        String catLabel = categoryFilter < 0 ? "TOUTES" : PropertyCategory.values()[categoryFilter].shortLabel();
        btn(x + 126, fy, 120, 18, catLabel, MineNorthStyle.CYAN, () -> { categoryFilter = categoryFilter >= PropertyCategory.values().length - 1 ? -1 : categoryFilter + 1; page = 0; rebuildWidgets(); });
        btn(x + 252, fy, 110, 18, furnishedOnly ? "MEUBLÉ SEUL" : "MEUBLÉ OU NON", furnishedOnly ? MineNorthStyle.GREEN : MineNorthStyle.DARK, () -> { furnishedOnly = !furnishedOnly; page = 0; rebuildWidgets(); });
        priceBox = new EditBox(font, x + 440, fy, 100, 18, Component.literal("Prix max"));
        priceBox.setHint(Component.literal("Prix max (€)"));
        priceBox.setValue(maxPrice);
        priceBox.setResponder(s -> { maxPrice = s; page = 0; refilter(); });
        addRenderableWidget(priceBox);

        refilter();
        int pages = Math.max(1, (shown.size() + COLS * ROWS - 1) / (COLS * ROWS));
        int by = y + 78 + 26 + ROWS * (CARD_H + GAP) + 4;
        btn(x + 20, by, 60, 18, "◀", MineNorthStyle.DARK, () -> { page = Math.max(0, page - 1); rebuildWidgets(); }).enabled(page > 0);
        btn(x + 86, by, 60, 18, "▶", MineNorthStyle.DARK, () -> { page = Math.min(pages - 1, page + 1); rebuildWidgets(); }).enabled(page < pages - 1);
    }

    private int cardX(int i) { return left() + 20 + (i % COLS) * (CARD_W + GAP); }
    private int cardY(int i) { return top() + 78 + 26 + (i / COLS) * (CARD_H + GAP); }

    private PropertyView viewAt(int slot) {
        int idx = page * COLS * ROWS + slot;
        return idx < shown.size() ? shown.get(idx) : null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (int s = 0; s < COLS * ROWS; s++) {
            PropertyView v = viewAt(s);
            if (v == null) continue;
            if (mx >= cardX(s) && mx < cardX(s) + CARD_W && my >= cardY(s) && my < cardY(s) + CARD_H) {
                Minecraft.getInstance().setScreen(new PropertyScreen(data, v, isAvailable(v)));
                return true;
            }
        }
        return false;
    }

    private boolean isAvailable(PropertyView v) {
        for (PropertyView c : data.catalog()) if (c.dimension().equals(v.dimension()) && c.houseName().equalsIgnoreCase(v.houseName())) return true;
        return false;
    }

    static String categoryLabel(PropertyCategory c) {
        return c.label();
    }

    static String priceLabel(PropertyView v) {
        return v.mode() == ListingMode.RENTAL ? MineNorthStyle.euros(v.priceCents()) + " / " + v.days() + " j" : MineNorthStyle.euros(v.priceCents());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = left(), y = top();
        MineNorthStyle.panel(g, x, y, PANEL_W, PANEL_H, data.catalogue().isBlank() ? "AGENCE IMMOBILIÈRE" : data.catalogue().replace('_', ' ').toUpperCase(), shown.size() + " bien(s) disponible(s)");
        for (int s = 0; s < COLS * ROWS; s++) {
            PropertyView v = viewAt(s);
            if (v == null) continue;
            int cx = cardX(s), cy = cardY(s);
            boolean hover = mx >= cx && mx < cx + CARD_W && my >= cy && my < cy + CARD_H;
            MineNorthStyle.card(g, cx, cy, CARD_W, CARD_H, hover, v.mode() == ListingMode.RENTAL ? MineNorthStyle.CYAN : MineNorthStyle.GREEN);
            ResourceLocation tex = v.imageCount() > 0 ? ImageCache.get(v.dimension(), v.houseName(), 0) : null;
            int iw = CARD_W - 8, ih = 78;
            if (tex != null) g.blit(tex, cx + 4, cy + 4, iw, ih, 0f, 0f, ImageCache.WIDTH, ImageCache.HEIGHT, ImageCache.WIDTH, ImageCache.HEIGHT);
            else g.fill(cx + 4, cy + 4, cx + 4 + iw, cy + 4 + ih, 0xFF0E0A34);
            g.drawString(font, font.plainSubstrByWidth(v.houseName(), CARD_W - 10), cx + 6, cy + 86, MineNorthStyle.TEXT, false);
            boolean free = isAvailable(v);
            g.drawString(font, free ? priceLabel(v) + (v.mode() == ListingMode.RENTAL ? "" : " (achat)") : "Non disponible", cx + 6, cy + 98, free ? MineNorthStyle.OK : MineNorthStyle.ALERT, false);
            g.drawString(font, categoryLabel(v.category()) + (v.furnished() ? " • meublé" : "") + (v.catalogue().isBlank() ? "" : " • " + v.catalogue()), cx + 6, cy + 111, MineNorthStyle.MUTED, false);
        }
        if (shown.isEmpty()) g.drawCenteredString(font, "Aucun bien ne correspond à votre recherche.", width / 2, y + 190, MineNorthStyle.MUTED);
        if (!data.message().isEmpty()) g.drawString(font, data.message(), x + 20, y + PANEL_H - 28, data.ok() ? MineNorthStyle.OK : MineNorthStyle.ALERT, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
