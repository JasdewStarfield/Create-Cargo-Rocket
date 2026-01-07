package yourscraft.jasdewstarfield.create_cargo_rocket;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.rocket.CargoRocketRenderer;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.station.GlobalStationData;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlockEntities;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlocks;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModEntities;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModItems;

@Mod(CreateCargoRocket.MODID)
public class CreateCargoRocket {
    public static final String MODID = "create_cargo_rocket";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateCargoRocket(IEventBus modEventBus, ModContainer modContainer) {
        // 初始化注册表
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);

        LOGGER.info("[Create Cargo Rocket] Mod initialized!");
    }

    @SubscribeEvent
    private void onCommonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[Create Cargo Rocket] HELLO FROM COMMON SETUP");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[Create Cargo Rocket] Server starting, restoring persistent chunks...");

        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            GlobalStationData.get(overworld).restoreForcedChunks(event.getServer());
        }
    }

    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("[Create Cargo Rocket] HELLO FROM CLIENT SETUP");
            LOGGER.info("[Create Cargo Rocket] MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.CARGO_ROCKET.get(), CargoRocketRenderer::new);
        }
    }
}
