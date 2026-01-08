package yourscraft.jasdewstarfield.create_cargo_rocket.content.station;

import net.createmod.catnip.data.Pair;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlocks;

public class DockingStationBlockItem extends BlockItem {

    public DockingStationBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public @NotNull InteractionResult place(@NotNull BlockPlaceContext context) {
        // 1. 先进行自定义的检查
        if (!checkSpace(context)) {
            // 如果检查失败，且在客户端，则展示红框和提示
            if (context.getLevel().isClientSide) {
                showBounds(context);
            }
            return InteractionResult.FAIL;
        }

        // 2. 如果检查通过，执行原版放置逻辑
        return super.place(context);
    }

    /**
     * 检查 3x3 区域是否合法（无方块阻挡、无实体阻挡）
     */
    private boolean checkSpace(BlockPlaceContext context) {
        BlockPos center = context.getClickedPos();
        Level level = context.getLevel();
        Player player = context.getPlayer();
        CollisionContext collisionContext = player == null ? CollisionContext.empty() : CollisionContext.of(player);

        // 我们假设用 Dummy 方块的状态来进行碰撞模拟
        BlockState dummyState = ModBlocks.DOCKING_STATION_DUMMY.get().defaultBlockState();

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos targetPos = center.offset(x, 0, z);
                BlockState currentWorldState = level.getBlockState(targetPos);

                // A. 检查方块是否可替换 (例如草、水、空气可以，石头不行)
                if (!currentWorldState.canBeReplaced(context)) {
                    return false;
                }

                // B. 检查是否有实体阻挡 (利用原版 isUnobstructed 防止卡人)
                if (!level.isUnobstructed(dummyState, targetPos, collisionContext)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 客户端专用：渲染红框和错误提示
     * 参考了 LargeWaterWheelBlockItem 的实现
     */
    @OnlyIn(Dist.CLIENT)
    private void showBounds(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (!(context.getPlayer() instanceof LocalPlayer localPlayer))
            return;

        // 1. 发送 Action Bar 消息 (类似唱片机播放的消息位置)
        // 你可以在语言文件中添加 "create_cargo_rocket.station.not_enough_space": "空间不足"
        localPlayer.displayClientMessage(Component.translatable("create_cargo_rocket.station.not_enough_space")
                .withStyle(ChatFormatting.RED), true);

        // 2. 使用 Create 的 Outliner 画框
        AABB box = new AABB(pos).inflate(1, 0, 1);

        Outliner.getInstance().showAABB(Pair.of("docking_station_place", pos), box)
                .colored(0xFF_ff5d6c) // Create 风格的淡红色
                .lineWidth(1 / 16f);
    }
}