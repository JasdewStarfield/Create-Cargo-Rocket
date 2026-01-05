package yourscraft.jasdewstarfield.create_cargo_rocket.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import yourscraft.jasdewstarfield.create_cargo_rocket.CreateCargoRocket;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateCargoRocket.MODID);

    // 站台方块的物品形式
    public static final DeferredItem<Item> DOCKING_STATION = ITEMS.register("docking_station",
            () -> new BlockItem(ModBlocks.DOCKING_STATION.get(), new Item.Properties()));

    // 火箭实体物品 (用于生成火箭)
    public static final DeferredItem<Item> CARGO_ROCKET = ITEMS.register("cargo_rocket",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
