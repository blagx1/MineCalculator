
package dev.minecalc.forge;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minecalc.client.MineCalcScreen;
import dev.minecalc.common.config.MineCalcConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.io.IOException;
import java.nio.file.Path;

@Mod(MineCalcForge.MOD_ID)
public final class MineCalcForge {
    public static final String MOD_ID = "minecalc";

    public MineCalcForge() {
        MineCalcScreen.setOpenKeyUpdater(ClientHooks::updateOpenKey);

        var modBusGroup = FMLJavaModLoadingContext.get().getModBusGroup();

        RegisterKeyMappingsEvent.getBus(modBusGroup)
                .addListener(ClientHooks::registerKeys);

        TickEvent.ClientTickEvent.Post.BUS
                .addListener(ClientHooks::onClientTick);
    }

    public static final class ClientHooks {
        private static KeyMapping openKey;

        public static void registerKeys(RegisterKeyMappingsEvent event) {
            MineCalcConfig config = new MineCalcConfig();

            Path settings = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config")
                    .resolve("minecalc")
                    .resolve("settings.properties");

            try {
                config.load(settings);
            } catch (IOException ignored) {
            }

            openKey = new KeyMapping(
                    "key.minecalc.open",
                    InputConstants.Type.KEYSYM,
                    config.openKey(),
                    KeyMapping.Category.register(
                            Identifier.fromNamespaceAndPath(MOD_ID, "main")
                    )
            );

            event.register(openKey);
        }

        public static void updateOpenKey(int key) {
            if (openKey != null) {
                openKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(key));
                KeyMapping.resetMapping();
            }
        }

        public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();

            while (openKey != null && openKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new MineCalcScreen());
                } else if (client.screen instanceof MineCalcScreen screen) {
                    screen.requestCloseAnimated();
                }
            }
        }
    }
}
