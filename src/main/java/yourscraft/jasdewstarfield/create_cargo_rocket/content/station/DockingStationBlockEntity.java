package yourscraft.jasdewstarfield.create_cargo_rocket.content.station;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlockEntities;

public class DockingStationBlockEntity extends BlockEntity {
    public DockingStationBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.DOCKING_STATION.get(), pos, blockState);
    }

    // 将来我们会在这里添加 Capability (IItemHandler)
    // 以及检测上方火箭的逻辑
}
