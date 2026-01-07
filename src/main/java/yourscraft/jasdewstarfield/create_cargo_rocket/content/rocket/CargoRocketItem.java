package yourscraft.jasdewstarfield.create_cargo_rocket.content.rocket;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
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

        // 只允许放置在停机坪上方
        if (state.is(ModBlocks.DOCKING_STATION.get())) {
            BlockPos spawnPos = clickedPos.above();
            if (!level.isEmptyBlock(spawnPos)) {
                return InteractionResult.FAIL;
            }

            if (!level.isClientSide) {
                CargoRocketEntity rocket = new CargoRocketEntity(level, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                level.addFreshEntity(rocket);
                context.getItemInHand().shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }
}
