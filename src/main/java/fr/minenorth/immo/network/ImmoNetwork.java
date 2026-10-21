package fr.minenorth.immo.network;

import fr.minenorth.immo.AgencyMenu;
import fr.minenorth.immo.MineNorthImmo;
import fr.minenorth.immo.client.ClientHandler;
import fr.minenorth.immo.server.MapBridge;
import fr.minenorth.portes.agency.AgencyService;
import fr.minenorth.portes.agency.AgencyService.OwnedView;
import fr.minenorth.portes.agency.AgencyService.PropertyView;
import fr.minenorth.portes.agency.PropertyCategory;
import fr.minenorth.portes.agency.PropertyImages;
import fr.minenorth.portes.agency.PropertyInfoData;
import fr.minenorth.portes.door.OwnerDoorData;
import fr.minenorth.portes.door.OwnerDoorManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Réseau de l'agence : données du catalogue (serveur vers client), actions et demandes d'image (client vers serveur). */
public final class ImmoNetwork {
    private ImmoNetwork() {}

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(MineNorthImmo.MOD_ID, "network"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static final int BUY = 0, RENT = 1, EXTEND = 2, RESELL = 3, SHOW_ON_MAP = 4, ADMIN_EDIT = 5, STOP_RENTAL = 6;
    private static final int MARKER_COLOR = 0x20AAEB;

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, AgencyDataPacket.class, AgencyDataPacket::encode, AgencyDataPacket::decode, AgencyDataPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, AgencyActionPacket.class, AgencyActionPacket::encode, AgencyActionPacket::decode, AgencyActionPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, RequestImagePacket.class, RequestImagePacket::encode, RequestImagePacket::decode, RequestImagePacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ImagePacket.class, ImagePacket::encode, ImagePacket::decode, ImagePacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
    }

    // ------------------------------------------------------------------ sérialisation des fiches

    private static void writeView(FriendlyByteBuf b, PropertyView v) {
        b.writeUtf(v.dimension()); b.writeUtf(v.houseName()); b.writeEnum(v.category()); b.writeEnum(v.mode());
        b.writeLong(v.priceCents()); b.writeVarInt(v.days()); b.writeBoolean(v.furnished()); b.writeUtf(v.description());
        b.writeVarInt(v.imageCount()); b.writeInt(v.x()); b.writeInt(v.y()); b.writeInt(v.z()); b.writeUtf(v.catalogue());
    }

    private static PropertyView readView(FriendlyByteBuf b) {
        return new PropertyView(b.readUtf(), b.readUtf(), b.readEnum(PropertyCategory.class), b.readEnum(OwnerDoorData.ListingMode.class),
                b.readLong(), b.readVarInt(), b.readBoolean(), b.readUtf(), b.readVarInt(), b.readInt(), b.readInt(), b.readInt(), b.readUtf());
    }

    private static void writeOwned(FriendlyByteBuf b, OwnedView o) {
        writeView(b, o.view()); b.writeBoolean(o.rented()); b.writeLong(o.expiryMs()); b.writeLong(o.purchaseCents());
        b.writeVarInt(o.companyId()); b.writeUtf(o.companyName());
    }

    private static OwnedView readOwned(FriendlyByteBuf b) {
        return new OwnedView(readView(b), b.readBoolean(), b.readLong(), b.readLong(), b.readVarInt(), b.readUtf());
    }

    // ------------------------------------------------------------------ paquets

    /** Catalogue, biens du joueur et entreprises dont il est patron ; message = résultat de la dernière action (vide sinon). */
    public record AgencyDataPacket(int tab, String message, boolean ok, List<PropertyView> catalog, List<OwnedView> mine, List<String> companies, List<PropertyView> all, String catalogue) {
        static void encode(AgencyDataPacket p, FriendlyByteBuf b) {
            b.writeVarInt(p.tab); b.writeUtf(p.message); b.writeBoolean(p.ok);
            b.writeCollection(p.catalog, ImmoNetwork::writeView);
            b.writeCollection(p.mine, ImmoNetwork::writeOwned);
            b.writeCollection(p.companies, FriendlyByteBuf::writeUtf);
            b.writeCollection(p.all, ImmoNetwork::writeView);
            b.writeUtf(p.catalogue);
        }

        static AgencyDataPacket decode(FriendlyByteBuf b) {
            return new AgencyDataPacket(b.readVarInt(), b.readUtf(), b.readBoolean(), b.readList(ImmoNetwork::readView), b.readList(ImmoNetwork::readOwned), b.readList(FriendlyByteBuf::readUtf), b.readList(ImmoNetwork::readView), b.readUtf());
        }

        static void handle(AgencyDataPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.open(p)));
            c.get().setPacketHandled(true);
        }
    }

    /** Action du joueur : action = BUY, RENT, EXTEND, RESELL ou SHOW_ON_MAP ; tab = onglet à rouvrir en cas d'échec. */
    public record AgencyActionPacket(int action, int tab, String dimension, String houseName, int companyId, String catalogue) {
        static void encode(AgencyActionPacket p, FriendlyByteBuf b) { b.writeVarInt(p.action); b.writeVarInt(p.tab); b.writeUtf(p.dimension); b.writeUtf(p.houseName); b.writeVarInt(p.companyId); b.writeUtf(p.catalogue); }

        static AgencyActionPacket decode(FriendlyByteBuf b) { return new AgencyActionPacket(b.readVarInt(), b.readVarInt(), b.readUtf(), b.readUtf(), b.readVarInt(), b.readUtf()); }

        static void handle(AgencyActionPacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer sp = c.get().getSender();
                if (sp != null) handleAction(sp, p);
            });
            c.get().setPacketHandled(true);
        }
    }

    public record RequestImagePacket(String dimension, String houseName, int index) {
        static void encode(RequestImagePacket p, FriendlyByteBuf b) { b.writeUtf(p.dimension); b.writeUtf(p.houseName); b.writeVarInt(p.index); }

        static RequestImagePacket decode(FriendlyByteBuf b) { return new RequestImagePacket(b.readUtf(), b.readUtf(), b.readVarInt()); }

        static void handle(RequestImagePacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer sp = c.get().getSender();
                if (sp == null) return;
                var level = sp.server.overworld();
                List<String> ids = PropertyInfoData.get(level).info(OwnerDoorData.get(level), p.dimension, p.houseName).imageIds();
                if (p.index < 0 || p.index >= ids.size()) return;
                byte[] jpeg = PropertyImages.load(sp.server, ids.get(p.index));
                if (jpeg != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new ImagePacket(p.dimension, p.houseName, p.index, jpeg));
            });
            c.get().setPacketHandled(true);
        }
    }

    public record ImagePacket(String dimension, String houseName, int index, byte[] jpeg) {
        static void encode(ImagePacket p, FriendlyByteBuf b) { b.writeUtf(p.dimension); b.writeUtf(p.houseName); b.writeVarInt(p.index); b.writeByteArray(p.jpeg); }

        static ImagePacket decode(FriendlyByteBuf b) { return new ImagePacket(b.readUtf(), b.readUtf(), b.readVarInt(), b.readByteArray(PropertyImages.MAX_BYTES)); }

        static void handle(ImagePacket p, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.imageReceived(p)));
            c.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ serveur

    private static void handleAction(ServerPlayer p, AgencyActionPacket a) {
        AgencyService.AgencyResult r;
        switch (a.action) {
            case BUY -> r = AgencyService.buy(p, a.dimension, a.houseName, a.companyId);
            case RENT -> r = AgencyService.rent(p, a.dimension, a.houseName, a.companyId);
            case EXTEND -> r = AgencyService.extend(p, a.dimension, a.houseName);
            case RESELL -> r = AgencyService.resell(p, a.dimension, a.houseName);
            case STOP_RENTAL -> r = AgencyService.stopRental(p, a.dimension, a.houseName);
            case ADMIN_EDIT -> {
                var key = AgencyService.referenceDoorKey(p.server, a.dimension, a.houseName);
                if (key != null) OwnerDoorManager.openAdminPanel(p, key);
                return;
            }
            case SHOW_ON_MAP -> {
                showOnMap(p, a.dimension, a.houseName, a.catalogue);
                return;
            }
            default -> { return; }
        }
        AgencyMenu.open(p, r.ok() ? AgencyMenu.TAB_MINE : a.tab, r.message(), r.ok(), a.catalogue);
    }

    private static void showOnMap(ServerPlayer p, String dimension, String houseName, String catalogue) {
        List<PropertyView> views = new ArrayList<>(AgencyService.catalog(p.server, catalogue));
        for (OwnedView o : AgencyService.myProperties(p)) views.add(o.view());
        for (PropertyView v : views) {
            if (!v.dimension().equals(dimension) || !v.houseName().equalsIgnoreCase(houseName)) continue;
            boolean ok = MapBridge.set(p, "Bien : " + v.houseName(), v.dimension(), v.x(), v.y(), v.z(), MARKER_COLOR, true);
            p.displayClientMessage(Component.literal(ok ? "§aRepère posé sur votre carte, le guidage est lancé." : "§cLa carte n'est pas disponible sur ce serveur."), true);
            return;
        }
    }
}
