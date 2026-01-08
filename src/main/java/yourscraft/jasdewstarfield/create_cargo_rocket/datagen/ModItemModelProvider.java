package yourscraft.jasdewstarfield.create_cargo_rocket.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import yourscraft.jasdewstarfield.create_cargo_rocket.CreateCargoRocket;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModItems;

public class ModItemModelProvider extends ItemModelProvider {

    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, CreateCargoRocket.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        // 1. 站台方块物品：继承自 block/docking_station
        // 注意：因为方块有 blockstate，我们通常通过 parent 指向其默认状态的模型，或者简单地指向 occupied=false 的模型
        // 这里为了简单，我们让物品模型直接长得像安山机壳
        withExistingParent(ModItems.DOCKING_STATION.getId().getPath(), "create:block/andesite_casing");

        // 2. 火箭物品 (如果是 3D 物品可能复杂点，这里假设是生成的贴图 item/generated)
        // basicItem(ModItems.CARGO_ROCKET.get());
        withExistingParent(ModItems.CARGO_ROCKET.getId().getPath(), "minecraft:block/iron_block");
    }
}
