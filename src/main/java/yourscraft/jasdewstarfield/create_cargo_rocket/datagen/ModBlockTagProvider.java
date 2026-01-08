package yourscraft.jasdewstarfield.create_cargo_rocket.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.create_cargo_rocket.CreateCargoRocket;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlocks;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends BlockTagsProvider {

    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, CreateCargoRocket.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {
        // 1. 注册 "可被镐挖掘" (mineable/pickaxe)
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ModBlocks.DOCKING_STATION.get())
                .add(ModBlocks.DOCKING_STATION_DUMMY.get());

        // 2. 注册挖掘等级
        //tag(BlockTags.NEEDS_IRON_TOOL)
        //        .add(ModBlocks.DOCKING_STATION.get())
        //        .add(ModBlocks.DOCKING_STATION_DUMMY.get());
    }
}
