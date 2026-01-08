package yourscraft.jasdewstarfield.create_cargo_rocket.content.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlockEntities;

import javax.annotation.Nullable;

public class DockingStationDummyBlockEntity extends BlockEntity {
    private BlockPos masterPos;

    public DockingStationDummyBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.DOCKING_STATION_DUMMY.get(), pos, blockState);
    }

    public void setMasterPos(BlockPos masterPos) {
        this.masterPos = masterPos;
        setChanged();
    }

    public BlockPos getMasterPos() {
        return masterPos;
    }

    // 获取主方块实体
    public DockingStationBlockEntity getMasterBE() {
        if (level != null && masterPos != null) {
            if (level.getBlockEntity(masterPos) instanceof DockingStationBlockEntity be) {
                return be;
            }
        }
        return null;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        super.saveAdditional(tag, provider);
        if (masterPos != null) {
            tag.put("MasterPos", NbtUtils.writeBlockPos(masterPos));
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("MasterPos")) {
            masterPos = NbtUtils.readBlockPos(tag, "MasterPos").orElse(null);
        }
    }

    // === 新增：数据同步方法 ===

    // 1. 告诉游戏这个方块实体需要同步 UpdateTag
    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider provider) {
        // 将保存的数据（包含 MasterPos）发送给客户端
        return saveWithoutMetadata(provider);
    }

    // 2. 告诉游戏更新时发送什么包
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
