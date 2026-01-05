package yourscraft.jasdewstarfield.create_cargo_rocket.content.rocket;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModEntities;

public class CargoRocketEntity extends Entity {
    // 27格库存，类似潜影盒或单箱
    public final ItemStackHandler inventory = new ItemStackHandler(27) {
        @Override
        protected void onContentsChanged(int slot) {
            // 当物品变动时，标记需要保存数据
            // 目前先留空
        }
    };

    public CargoRocketEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    public CargoRocketEntity(Level level, double x, double y, double z) {
        this(ModEntities.CARGO_ROCKET.get(), level);
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        // 将来这里同步火箭的状态（IDLE, LAUNCHING等）
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.inventory.deserializeNBT(level().registryAccess(), compound.getCompound("Inventory"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.put("Inventory", this.inventory.serializeNBT(level().registryAccess()));
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        if (!this.level().isClientSide) {
            // 临时测试：右键点击打印库存信息或打开GUI
            // 后续我们会在这里添加打开GUI的逻辑
            System.out.println("Interacted with Cargo Rocket!");
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // 允许被推动（可选）
    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket(@NotNull ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }
}
