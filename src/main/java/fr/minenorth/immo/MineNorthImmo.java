package fr.minenorth.immo;

import fr.minenorth.immo.network.ImmoNetwork;
import net.minecraftforge.fml.common.Mod;

/** Agence immobilière MineNorth : écrans et réseau. Les données et la logique métier sont dans minenorth-portes (AgencyService). */
@Mod(MineNorthImmo.MOD_ID)
public class MineNorthImmo {
    public static final String MOD_ID = "minenorthimmo";

    public MineNorthImmo() {
        ImmoNetwork.register();
    }
}
