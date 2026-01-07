package yourscraft.jasdewstarfield.create_cargo_rocket.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import yourscraft.jasdewstarfield.create_cargo_rocket.CreateCargoRocket;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.rocket.CargoRocketEntity;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CreateCargoRocket.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CargoRocketEntity>> CARGO_ROCKET = ENTITY_TYPES.register("cargo_rocket",
            () -> EntityType.Builder.<CargoRocketEntity>of(CargoRocketEntity::new, MobCategory.MISC)
                    .sized(1.0f, 6.0f)
                    .clientTrackingRange(10)
                    .build("cargo_rocket"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
