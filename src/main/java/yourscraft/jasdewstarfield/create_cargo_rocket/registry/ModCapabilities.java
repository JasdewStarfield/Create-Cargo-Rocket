package yourscraft.jasdewstarfield.create_cargo_rocket.registry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import yourscraft.jasdewstarfield.create_cargo_rocket.CreateCargoRocket;

@EventBusSubscriber(modid = CreateCargoRocket.MODID)
public class ModCapabilities {

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // 1. 为火箭实体注册物品能力
        // 这允许其他模组（或漏斗）直接与火箭实体交互（如果它们能直接够到火箭的话）
        event.registerEntity(
                Capabilities.ItemHandler.ENTITY,
                ModEntities.CARGO_ROCKET.get(),
                (entity, context) -> entity.inventory
        );

        // 2. 为停机坪方块实体注册物品能力
        // 这允许管道/漏斗连接到停机坪方块时，实际上是在操作火箭的库存
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.DOCKING_STATION.get(),
                (be, context) -> be.getItemHandler()
        );
    }
}
