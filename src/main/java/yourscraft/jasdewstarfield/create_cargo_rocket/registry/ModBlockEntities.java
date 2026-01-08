package yourscraft.jasdewstarfield.create_cargo_rocket.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import yourscraft.jasdewstarfield.create_cargo_rocket.CreateCargoRocket;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.station.DockingStationBlockEntity;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.station.DockingStationDummyBlockEntity;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateCargoRocket.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DockingStationBlockEntity>> DOCKING_STATION = BLOCK_ENTITIES.register("docking_station",
            () -> BlockEntityType.Builder.of(DockingStationBlockEntity::new, ModBlocks.DOCKING_STATION.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DockingStationDummyBlockEntity>> DOCKING_STATION_DUMMY = BLOCK_ENTITIES.register("docking_station_dummy",
            () -> BlockEntityType.Builder.of(DockingStationDummyBlockEntity::new, ModBlocks.DOCKING_STATION_DUMMY.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
