package yourscraft.jasdewstarfield.create_cargo_rocket.content.station;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlocks;

public class DockingStationDummyBlock extends BaseEntityBlock {
    public static final MapCodec<DockingStationDummyBlock> CODEC = simpleCodec(DockingStationDummyBlock::new);

    public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");

    public DockingStationDummyBlock(Properties properties) {
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
        return new DockingStationDummyBlockEntity(pos, state);
    }

    // 右键点击代理方块 -> 打开主方块的界面
    @Override
    protected @NotNull InteractionResult useWithoutItem(
            @NotNull BlockState state,
            Level level,
            @NotNull BlockPos pos,
            @NotNull Player player,
            @NotNull BlockHitResult hitResult
    ) {
        if (level.getBlockEntity(pos) instanceof DockingStationDummyBlockEntity dummy) {
            DockingStationBlockEntity master = dummy.getMasterBE();
            if (master != null) {
                return master.getBlockState().useWithoutItem(level, player, hitResult.withPosition(dummy.getMasterPos()));
            }
        }
        return InteractionResult.PASS;
    }

    // 破坏代理方块 -> 破坏主方块
    @Override
    public void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof DockingStationDummyBlockEntity dummy) {
                BlockPos masterPos = dummy.getMasterPos();
                if (masterPos != null && level.getBlockState(masterPos).is(ModBlocks.DOCKING_STATION.get())) {
                    level.destroyBlock(masterPos, true); // 连锁破坏
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // 中键拾取 (Pick Block) -> 返回主方块物品
    @Override
    @SuppressWarnings("deprecation")
    public @NotNull ItemStack getCloneItemStack(@NotNull LevelReader level, @NotNull BlockPos pos, @NotNull BlockState state) {
        return new ItemStack(ModBlocks.DOCKING_STATION.get());
    }
}
