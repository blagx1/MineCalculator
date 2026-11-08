package dev.minecalc.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minecalc.client.MineCalcScreen;
import dev.minecalc.common.config.MineCalcConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.file.Path;

@Mod(value = MineCalcNeoForge.MOD_ID, dist = Dist.CLIENT)
public final class MineCalcNeoForge {
    public static final String MOD_ID = "minecalc";

    public MineCalcNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(ModBusEvents::registerKeys);
        NeoForge.EVENT_BUS.addListener(GameBusEvents::onClientTick);
    }

    public static final class ModBusEvents {
        private static KeyMapping openKey;

        public static void updateOpenKey(int key) {
            if (openKey != null) {
                openKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(key));
                KeyMapping.resetMapping();
            }
        }

        public static void registerKeys(RegisterKeyMappingsEvent event) {
            MineCalcConfig config = new MineCalcConfig();
            Path settings = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("minecalc").resolve("settings.properties");
            try {
                config.load(settings);
            } catch (IOException ignored) {
            }

            KeyMapping.Category category =
                    new KeyMapping.Category(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
            event.registerCategory(category);

            openKey = new KeyMapping(
                    "key.minecalc.open",
                    InputConstants.Type.KEYSYM,
                    config.openKey(),
                    category
            );
            event.register(openKey);
        }
    }

    public static final class GameBusEvents {
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            KeyMapping key = ModBusEvents.openKey;

            while (key != null && key.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new MineCalcScreen());
                } else if (client.screen instanceof MineCalcScreen screen) {
                    screen.requestCloseAnimated();
                }
            }
        }
    }
}