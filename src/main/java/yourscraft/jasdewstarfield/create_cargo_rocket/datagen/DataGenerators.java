package yourscraft.jasdewstarfield.create_cargo_rocket.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import yourscraft.jasdewstarfield.create_cargo_rocket.CreateCargoRocket;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = CreateCargoRocket.MODID)
public class DataGenerators {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        // 1. BlockStates & Block Models (方块状态和模型)
        generator.addProvider(event.includeClient(), new ModBlockStateProvider(packOutput, existingFileHelper));

        // 2. Item Models (物品模型)
        generator.addProvider(event.includeClient(), new ModItemModelProvider(packOutput, existingFileHelper));

        // 3. Loot Tables (掉落表)
        generator.addProvider(event.includeServer(), new LootTableProvider(
                packOutput,
                Collections.emptySet(),
                List.of(new LootTableProvider.SubProviderEntry(ModBlockLootTables::new, LootContextParamSets.BLOCK)),
                lookupProvider
        ));

        // 4. Recipes (配方)
        generator.addProvider(event.includeServer(), new ModRecipeProvider(packOutput, lookupProvider));

        // 5. Tags (标签)
        BlockTagsProvider blockTagsProvider = new ModBlockTagProvider(packOutput, lookupProvider, existingFileHelper);
        generator.addProvider(event.includeServer(), blockTagsProvider);
    }
}