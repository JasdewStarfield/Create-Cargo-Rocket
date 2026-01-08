package yourscraft.jasdewstarfield.create_cargo_rocket.content.rocket;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.station.DockingStationBlock;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.station.DockingStationDummyBlockEntity;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.station.GlobalStationData;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlocks;

public class CargoRocketItem extends Item {
    public CargoRocketItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState state = level.getBlockState(clickedPos);

        BlockPos masterPos = null;
        BlockState masterState = null;

        // 1. 判定点击的是主方块还是代理方块
        if (state.is(ModBlocks.DOCKING_STATION.get())) {
            masterPos = clickedPos;
            masterState = state;
        } else if (state.is(ModBlocks.DOCKING_STATION_DUMMY.get())) {
            if (level.getBlockEntity(clickedPos) instanceof DockingStationDummyBlockEntity dummy) {
                masterPos = dummy.getMasterPos();
                if (masterPos != null) {
                    masterState = level.getBlockState(masterPos);
                }
            }
        }

        // 2. 如果找到了有效的主方块，且未被占用
        if (masterPos != null && masterState.is(ModBlocks.DOCKING_STATION.get())) {
            if (!masterState.getValue(DockingStationBlock.OCCUPIED)) {
                // 火箭永远生成在主方块的正上方
                BlockPos spawnPos = masterPos.above();

                // 检查空间（这里假设火箭只需 1x1 空间，如果火箭模型很大，可能需要检查 3x3 的上方空间）
                if (!level.isEmptyBlock(spawnPos)) {
                    return InteractionResult.FAIL;
                }
                if (!level.isClientSide) {
                    CargoRocketEntity rocket = new CargoRocketEntity(level, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

                    Player player = context.getPlayer();
                    if (player != null && level instanceof ServerLevel sl) {
                        rocket.setOwner(player.getUUID());
                        GlobalStationData data = GlobalStationData.get(sl);
                        String baseName = player.getName().getString() + "'s Rocket";
                        String uniqueName = data.getUniqueRocketName(baseName);
                        rocket.setRocketName(uniqueName);
                    }

                    level.addFreshEntity(rocket);
                    DockingStationBlock.setOccupied(level, masterPos, true);
                    context.getItemInHand().shrink(1);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }
}
