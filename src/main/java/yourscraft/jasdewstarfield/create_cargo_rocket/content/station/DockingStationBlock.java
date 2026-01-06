package yourscraft.jasdewstarfield.create_cargo_rocket.content.station;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlockEntities;

public class DockingStationBlock extends BaseEntityBlock {
    public static final MapCodec<DockingStationBlock> CODEC = simpleCodec(DockingStationBlock::new);

    public DockingStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ModBlockEntities.DOCKING_STATION.get().create(pos, state);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(
            @NotNull BlockState state, Level level,
            @NotNull BlockPos pos,
            @NotNull Player player,
            @NotNull BlockHitResult hitResult
    ) {
        if (level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DockingStationBlockEntity station) {
                Minecraft.getInstance().setScreen(new DockingStationScreen(pos, station.getStationName()));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState, boolean isMoving) {
        // 只有当方块类型改变（即方块被破坏或替换）时才执行
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DockingStationBlockEntity station && !level.isClientSide) {
                String name = station.getStationName();
                if (name != null && !name.isEmpty()) {
                    // 从全局数据中移除该站点
                    GlobalStationData.get((ServerLevel) level).removeStation(GlobalPos.of(level.dimension(), pos));
                }
            }
        }
        // 必须调用 super，否则 BlockEntity 不会被移除
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NotNull Level level,
            @NotNull BlockState state,
            @NotNull BlockEntityType<T> blockEntityType
    ) {
        // 如果我们稍后需要让站台每 tick 执行逻辑（例如扫描火箭），这里会用到
        // 目前返回 null
        return null;
    }
}
