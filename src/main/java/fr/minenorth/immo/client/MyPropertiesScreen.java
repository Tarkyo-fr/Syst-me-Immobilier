package fr.minenorth.immo.client;

import fr.minenorth.immo.AgencyMenu;
import fr.minenorth.immo.network.ImmoNetwork;
import fr.minenorth.portes.agency.AgencyService.OwnedView;
import fr.minenorth.portes.agency.ImmoRules;
import fr.minenorth.portes.client.MineNorthButton;
import fr.minenorth.portes.client.MineNorthStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** « Mes biens » : biens achetés (revente à 65 %) et locations en cours (prolongation). */
public class MyPropertiesScreen extends Screen {
    private static final int W = CatalogScreen.PANEL_W, H = CatalogScreen.PANEL_H, ROW_H = 58, PER_PAGE = 5;
    private final ImmoNetwork.AgencyDataPacket data;
    private int page = 0;
    /** Bien dont la revente attend une confirmation (clé dimension|maison). */
    private String pendingResell = "";
    /** Location dont l'arrêt attend une confirmation (clé dimension|maison). */
    private String pendingStop = "";

    public MyPropertiesScreen(ImmoNetwork.AgencyDataPacket data) {
        super(Component.literal("Mes biens"));
        this.data = data;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return Math.max(8, (height - H) / 2); }

    private MineNorthButton btn(int x, int y, int w, int h, String label, int color, Runnable r) {
        return addRenderableWidget(new MineNorthButton(x, y, w, h, Component.literal(label), color, r));
    }

    private void act(int action, OwnedView o) {
        ImmoNetwork.CHANNEL.sendToServer(new ImmoNetwork.AgencyActionPacket(action, AgencyMenu.TAB_MINE, o.view().dimension(), o.view().houseName(), o.companyId(), data.catalogue()));
    }

    @Override
    protected void init() {
        super.init();
        int x = left(), y = top();
        btn(x + 20, y + 52, 130, 18, "CATALOGUE", MineNorthStyle.DARK, () -> Minecraft.getInstance().setScreen(new CatalogScreen(data)));
        btn(x + 156, y + 52, 130, 18, "MES BIENS", MineNorthStyle.GREEN, () -> {});
        btn(x + W - 100, y + 10, 86, 16, "Fermer", MineNorthButton.GHOST, this::onClose);
        int pages = Math.max(1, (data.mine().size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= data.mine().size()) break;
            OwnedView o = data.mine().get(idx);
            int ry = y + 80 + i * ROW_H;
            String key = o.view().dimension() + "|" + o.view().houseName();
            if (o.rented()) {
                btn(x + W - 230, ry + 3, 210, 22, "PROLONGER +" + o.view().days() + " j (" + MineNorthStyle.euros(o.view().priceCents()) + ")", MineNorthStyle.CYAN, () -> act(ImmoNetwork.EXTEND, o));
                boolean confirmStop = key.equals(pendingStop);
                btn(x + W - 230, ry + 27, 210, 20, confirmStop ? "CONFIRMER : SANS REMBOURSEMENT" : "ARRÊTER LA LOCATION",
                        confirmStop ? MineNorthStyle.PINK : MineNorthStyle.DARK, () -> {
                            if (key.equals(pendingStop)) act(ImmoNetwork.STOP_RENTAL, o);
                            else { pendingStop = key; pendingResell = ""; rebuildWidgets(); }
                        });
            } else {
                boolean confirm = key.equals(pendingResell);
                long back = ImmoRules.resaleCents(o.purchaseCents());
                btn(x + W - 230, ry + 14, 210, 24, confirm ? "CONFIRMER : +" + MineNorthStyle.euros(back) : "REVENDRE (" + MineNorthStyle.euros(back) + ")",
                        confirm ? MineNorthStyle.PINK : MineNorthStyle.DARK, () -> {
                            if (key.equals(pendingResell)) act(ImmoNetwork.RESELL, o);
                            else { pendingResell = key; pendingStop = ""; rebuildWidgets(); }
                        });
            }
        }
        int by = y + 80 + PER_PAGE * ROW_H + 6;
        int p = pages;
        btn(x + 20, by, 60, 18, "◀", MineNorthStyle.DARK, () -> { page = Math.max(0, page - 1); rebuildWidgets(); }).enabled(page > 0);
        btn(x + 86, by, 60, 18, "▶", MineNorthStyle.DARK, () -> { page = Math.min(p - 1, page + 1); rebuildWidgets(); }).enabled(page < p - 1);
    }

    private static String remaining(long expiryMs) {
        long ms = Math.max(0, expiryMs - System.currentTimeMillis());
        long days = ms / 86_400_000L, hours = ms / 3_600_000L % 24;
        return days > 0 ? days + " j " + hours + " h" : hours + " h";
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = left(), y = top();
        MineNorthStyle.panel(g, x, y, W, H, "MES BIENS", data.mine().size() + " bien(s)");
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= data.mine().size()) break;
            OwnedView o = data.mine().get(idx);
            int ry = y + 80 + i * ROW_H;
            MineNorthStyle.card(g, x + 20, ry, W - 40, ROW_H - 6, false, o.rented() ? MineNorthStyle.CYAN : MineNorthStyle.GREEN);
            g.drawString(font, o.view().houseName(), x + 30, ry + 8, MineNorthStyle.TEXT, false);
            g.drawString(font, CatalogScreen.categoryLabel(o.view().category()) + (o.companyId() > 0 ? " • " + o.companyName() : ""), x + 30, ry + 21, MineNorthStyle.MUTED, false);
            g.drawString(font, o.rented() ? "Loué • reste " + remaining(o.expiryMs()) : "Acheté • " + MineNorthStyle.euros(o.purchaseCents()), x + 30, ry + 34, MineNorthStyle.OK, false);
        }
        if (data.mine().isEmpty()) g.drawCenteredString(font, "Vous n'avez aucun bien.", width / 2, y + 190, MineNorthStyle.MUTED);
        if (!data.message().isEmpty()) g.drawString(font, data.message(), x + 20, y + H - 28, data.ok() ? MineNorthStyle.OK : MineNorthStyle.ALERT, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
