package yourscraft.jasdewstarfield.create_cargo_rocket.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import yourscraft.jasdewstarfield.create_cargo_rocket.CreateCargoRocket;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.station.DockingStationBlock;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.station.DockingStationDummyBlock;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlocks;

public class ModBlockStateProvider extends BlockStateProvider {

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, CreateCargoRocket.MODID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // 定义模型 (直接引用 Create 的机壳模型)
        ModelFile andesiteCasing = new ModelFile.UncheckedModelFile(ResourceLocation.fromNamespaceAndPath("create", "block/andesite_casing"));
        ModelFile copperCasing = new ModelFile.UncheckedModelFile(ResourceLocation.fromNamespaceAndPath("create", "block/copper_casing"));

        // 注册主方块 (Docking Station)
        // 逻辑：如果 occupied=false 显示安山机壳，true 显示铜机壳
        getVariantBuilder(ModBlocks.DOCKING_STATION.get())
                .forAllStates(state -> {
                    boolean occupied = state.getValue(DockingStationBlock.OCCUPIED);
                    return ConfiguredModel.builder()
                            .modelFile(occupied ? copperCasing : andesiteCasing)
                            .build();
                });

        // 注册 Dummy 方块 (逻辑相同)
        getVariantBuilder(ModBlocks.DOCKING_STATION_DUMMY.get())
                .forAllStates(state -> {
                    boolean occupied = state.getValue(DockingStationDummyBlock.OCCUPIED);
                    return ConfiguredModel.builder()
                            .modelFile(occupied ? copperCasing : andesiteCasing)
                            .build();
                });
    }
}