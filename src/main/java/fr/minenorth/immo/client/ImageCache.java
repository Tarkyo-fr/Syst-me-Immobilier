package fr.minenorth.immo.client;

import com.mojang.blaze3d.platform.NativeImage;
import fr.minenorth.immo.network.ImmoNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Images d'aperçu des biens : demandées au serveur à la première utilisation, puis gardées en texture. */
public final class ImageCache {
    private ImageCache() {}

    public static final int WIDTH = 640, HEIGHT = 360;
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Set<String> REQUESTED = new HashSet<>();

    private static String key(String dim, String house, int index) { return dim + "|" + house.toLowerCase(Locale.ROOT) + "|" + index; }

    /** Texture de l'image, ou null tant qu'elle n'est pas arrivée (la demande part une seule fois). */
    public static ResourceLocation get(String dim, String house, int index) {
        String k = key(dim, house, index);
        ResourceLocation t = TEXTURES.get(k);
        if (t == null && REQUESTED.add(k)) ImmoNetwork.CHANNEL.sendToServer(new ImmoNetwork.RequestImagePacket(dim, house, index));
        return t;
    }

    public static void put(String dim, String house, int index, byte[] jpeg) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(jpeg));
            if (src == null) return;
            NativeImage img = new NativeImage(WIDTH, HEIGHT, false);
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int rgb = src.getRGB(x * src.getWidth() / WIDTH, y * src.getHeight() / HEIGHT);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    img.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
                }
            }
            ResourceLocation rl = Minecraft.getInstance().getTextureManager().register("minenorthimmo_img", new DynamicTexture(img));
            TEXTURES.put(key(dim, house, index), rl);
        } catch (Exception ignored) {}
    }
}
