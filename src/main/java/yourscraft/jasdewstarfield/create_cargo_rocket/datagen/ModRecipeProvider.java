package yourscraft.jasdewstarfield.create_cargo_rocket.datagen;

import com.simibubi.create.AllBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlocks;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider {

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(@NotNull RecipeOutput output) {
        // 临时占位符：9 个安山机壳合成 1 个站台
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.DOCKING_STATION.get())
                .pattern("CCC")
                .pattern("CCC")
                .pattern("CCC")
                .define('C', AllBlocks.ANDESITE_CASING.get()) // 引用 Create 的方块
                .unlockedBy("has_andesite_casing", has(AllBlocks.ANDESITE_CASING.get()))
                .save(output);
    }
}