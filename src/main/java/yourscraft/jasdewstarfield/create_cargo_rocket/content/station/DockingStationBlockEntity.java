package yourscraft.jasdewstarfield.create_cargo_rocket.content.station;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.rocket.CargoRocketEntity;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlockEntities;

import javax.annotation.Nullable;
import java.util.List;

public class DockingStationBlockEntity extends BlockEntity {

    // 缓存的代理处理器实例
    private final ProxyItemHandler itemHandler = new ProxyItemHandler();
    private String stationName = "";

    public DockingStationBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.DOCKING_STATION.get(), pos, blockState);
    }

    /**
     * 供 ModCapabilities 调用的方法，返回我们的代理处理器
     */
    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    public String getStationName() {
        return stationName;
    }

    /**
     * 尝试更新站点名称。
     *
     * @param newName 新名称
     * @param player  发起操作的玩家（用于发送反馈消息），如果为 null 则不发送反馈
     */
    public void updateName(String newName, @Nullable ServerPlayer player) {
        if (this.stationName.equals(newName)) return;

        // 只有服务端需要更新 GlobalStationData 和进行查重
        if (level instanceof ServerLevel serverLevel) {
            if (!newName.isEmpty()) {
                GlobalStationData data = GlobalStationData.get(serverLevel);

                // === 查重逻辑 ===
                if (data.hasStation(newName)) {
                    GlobalPos existingPos = data.getStationPos(newName);
                    // 如果名字存在，且对应的坐标不是当前方块
                    if (!existingPos.dimension().equals(level.dimension()) || !existingPos.pos().equals(worldPosition)) {
                        if (player != null) {
                            player.sendSystemMessage(Component.literal("Station name already taken!").withStyle(ChatFormatting.RED));
                        }
                        return; // 更新失败
                    }
                }

                data.addStation(newName, GlobalPos.of(level.dimension(), worldPosition));
            }
        }

        this.stationName = newName;
        this.setChanged();

        // 同步给客户端
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void updateRocketName(String newName, @Nullable ServerPlayer player) {
        CargoRocketEntity rocket = getRocket();

        // 如果没有火箭，直接返回
        if (rocket == null) return;

        // 逻辑只在服务端执行
        if (level instanceof ServerLevel serverLevel) {
            GlobalStationData data = GlobalStationData.get(serverLevel);
            String finalName = newName;

            // 如果名字确实改变了，才进行查重逻辑
            if (!newName.equals(rocket.getRocketName())) {
                finalName = data.getUniqueRocketName(newName);
            }

            // 执行改名
            rocket.setRocketName(finalName);

            // 发送反馈信息给玩家
            if (player != null) {
                if (!finalName.equals(newName)) {
                    // 名字被占用，自动重命名
                    player.sendSystemMessage(Component.literal("Name taken! Renamed to: " + finalName).withStyle(ChatFormatting.YELLOW));
                }
            }
        }
    }

    // === NBT & Sync ===

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("StationName", stationName);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        super.loadAdditional(tag, provider);
        this.stationName = tag.getString("StationName");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider provider) {
        return saveWithoutMetadata(provider);
    }

    // === Inventory Proxy ===

    /**
     * 尝试寻找停靠在正上方的火箭实体。
     * 为了性能，只在需要时扫描，并且范围限定在方块正上方。
     */
    @Nullable
    public CargoRocketEntity getRocket() {
        if (level == null) return null;

        // 定义扫描区域：方块正上方 1 格到 3 格高的地方
        // 稍微收缩 X 和 Z 轴以防止检测到相邻方块的实体
        AABB aabb = new AABB(worldPosition.above()).expandTowards(0, 2, 0).deflate(0.1);

        List<CargoRocketEntity> rockets = level.getEntitiesOfClass(CargoRocketEntity.class, aabb);
        if (rockets.isEmpty()) {
            return null;
        }

        // 返回找到的第一个火箭（理论上只能有一个）
        return rockets.getFirst();
    }

    /**
     * 内部类：代理物品处理器
     * 它的作用是把所有调用转发给火箭的 inventory
     */
    private class ProxyItemHandler implements IItemHandler {

        @Override
        public int getSlots() {
            CargoRocketEntity rocket = getRocket();
            return rocket == null ? 0 : rocket.inventory.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            CargoRocketEntity rocket = getRocket();
            return rocket == null ? ItemStack.EMPTY : rocket.inventory.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            CargoRocketEntity rocket = getRocket();
            // 如果没有火箭，或者火箭正在发射/飞行中（非 IDLE 状态，后续会加状态判断），则拒绝插入
            if (rocket == null) {
                return stack; // 返回原样，表示没插进去
            }
            return rocket.inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            CargoRocketEntity rocket = getRocket();
            if (rocket == null) {
                return ItemStack.EMPTY; // 没东西可取
            }
            return rocket.inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            CargoRocketEntity rocket = getRocket();
            return rocket == null ? 0 : rocket.inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            CargoRocketEntity rocket = getRocket();
            return rocket != null && rocket.inventory.isItemValid(slot, stack);
        }
    }
}
