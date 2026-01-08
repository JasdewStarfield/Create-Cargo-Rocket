package yourscraft.jasdewstarfield.create_cargo_rocket.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlocks;

import java.util.Set;
import java.util.stream.Collectors;

public class ModBlockLootTables extends BlockLootSubProvider {

    public ModBlockLootTables(HolderLookup.Provider provider) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), provider);
    }

    @Override
    protected void generate() {
        // 主方块：掉落自身
        this.dropSelf(ModBlocks.DOCKING_STATION.get());

        // dummy：无掉落
        this.add(ModBlocks.DOCKING_STATION_DUMMY.get(), noDrop());
    }

    @Override
    protected @NotNull Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(DeferredHolder::get).collect(Collectors.toList());
    }
}
