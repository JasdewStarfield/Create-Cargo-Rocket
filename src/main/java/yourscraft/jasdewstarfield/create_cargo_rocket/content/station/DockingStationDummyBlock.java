package yourscraft.jasdewstarfield.create_cargo_rocket.content.station;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
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

public class DockingStationDummyBlock extends BaseEntityBlock implements IWrenchable {
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

    /**
     * 当玩家挖掘此方块时调用（在 onRemove 之前）。
     * 用于处理创造模式不掉落、以及工具等级判断。
     *
     * @return 方块 state
     */
    @Override
    public @NotNull BlockState playerWillDestroy(Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof DockingStationDummyBlockEntity dummy) {
                BlockPos masterPos = dummy.getMasterPos();
                if (masterPos != null) {
                    BlockState masterState = level.getBlockState(masterPos);
                    if (masterState.is(ModBlocks.DOCKING_STATION.get())) {
                        // 判定逻辑：
                        // 1. 玩家不是创造模式
                        // 2. 玩家持有正确的工具 (或者该方块不需要特定工具)
                        boolean requiresTool = masterState.requiresCorrectToolForDrops();
                        boolean hasTool = !requiresTool || player.getMainHandItem().isCorrectToolForDrops(masterState);

                        // 3. 综合判断：非创造模式 且 (不需要工具 或 工具合格)
                        boolean shouldDrop = !player.isCreative() && hasTool;

                        // 销毁主方块，并传入计算好的 shouldDrop
                        level.destroyBlock(masterPos, shouldDrop);
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
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

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        BlockPos clickedPos = context.getClickedPos();
        Level level = context.getLevel();

        // 获取当前方块实体
        if (level.getBlockEntity(clickedPos) instanceof DockingStationDummyBlockEntity dummy) {
            BlockPos masterPos = dummy.getMasterPos();

            // 如果找到了有效的主方块位置
            if (masterPos != null && level.getBlockState(masterPos).getBlock() instanceof DockingStationBlock) {

                // 构造一个新的 Context，把“点击位置”欺骗为主方块的位置
                UseOnContext newContext = new UseOnContext(
                        level,
                        context.getPlayer(),
                        context.getHand(),
                        context.getItemInHand(),
                        new BlockHitResult(
                                context.getClickLocation(),
                                context.getClickedFace(),
                                masterPos,
                                context.isInside()
                        )
                );

                // 获取主方块的状态
                BlockState masterState = level.getBlockState(masterPos);

                // 调用主方块的默认拆卸逻辑 (IWrenchable.super)
                // 这样会触发主方块的 onRemove，进而通过你的级联逻辑清理掉所有 Dummy
                if (masterState.getBlock() instanceof IWrenchable) {
                    return ((IWrenchable) masterState.getBlock()).onSneakWrenched(masterState, newContext);
                }
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }
}
