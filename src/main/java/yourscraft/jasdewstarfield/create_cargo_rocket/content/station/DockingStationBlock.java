package yourscraft.jasdewstarfield.create_cargo_rocket.content.station;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlockEntities;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlocks;

import java.util.List;

public class DockingStationBlock extends BaseEntityBlock implements IWrenchable {
    public static final MapCodec<DockingStationBlock> CODEC = simpleCodec(DockingStationBlock::new);

    public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");

    public DockingStationBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(OCCUPIED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OCCUPIED);
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
    public void setPlacedBy(
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull BlockState state,
            @Nullable LivingEntity placer,
            @NotNull ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;

        // 在周围 3x3 区域生成代理方块
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue; // 跳过中心

                BlockPos offsetPos = pos.offset(x, 0, z);
                level.setBlock(offsetPos, ModBlocks.DOCKING_STATION_DUMMY.get().defaultBlockState(), 3);

                // 设置代理方块指向中心
                if (level.getBlockEntity(offsetPos) instanceof DockingStationDummyBlockEntity dummy) {
                    dummy.setMasterPos(pos);
                }
            }
        }
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
            if (!level.isClientSide) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && z == 0) continue;
                        BlockPos offsetPos = pos.offset(x, 0, z);
                        if (level.getBlockState(offsetPos).is(ModBlocks.DOCKING_STATION_DUMMY.get())) {
                            level.destroyBlock(offsetPos, false);
                        }
                    }
                }
            }
        }
        // 必须调用 super，否则 BlockEntity 不会被移除
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /**
     * 静态辅助方法：统一设置 3x3 站台的占用状态
     */
    public static void setOccupied(Level level, BlockPos masterPos, boolean occupied) {
        // 1. 更新主方块
        BlockState masterState = level.getBlockState(masterPos);
        if (masterState.is(ModBlocks.DOCKING_STATION.get())) {
            level.setBlock(masterPos, masterState.setValue(OCCUPIED, occupied), 3);
        }

        // 2. 更新周围 8 个 Dummy 方块
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue; // 跳过中心

                BlockPos dummyPos = masterPos.offset(x, 0, z);
                BlockState dummyState = level.getBlockState(dummyPos);

                // 确保它是我们的 Dummy 方块才更新
                if (dummyState.is(ModBlocks.DOCKING_STATION_DUMMY.get())) {
                    level.setBlock(dummyPos, dummyState.setValue(DockingStationDummyBlock.OCCUPIED, occupied), 3);
                }
            }
        }
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }
}
