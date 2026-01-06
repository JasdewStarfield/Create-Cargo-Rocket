package yourscraft.jasdewstarfield.create_cargo_rocket.content.rocket;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModEntities;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModItems;

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
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }

        // 在服务端处理死亡逻辑
        if (!this.level().isClientSide && !this.isRemoved()) {
            // 如果不是创造模式玩家，掉落火箭物品
            boolean isCreative = source.getEntity() instanceof Player p && p.isCreative();
            if (!isCreative) {
                this.spawnAtLocation(ModItems.CARGO_ROCKET.get());
            }

            // 掉落库存内的物品
            SimpleContainer tempContainer = new SimpleContainer(inventory.getSlots());
            for (int i = 0; i < inventory.getSlots(); i++) {
                tempContainer.setItem(i, inventory.getStackInSlot(i));
            }
            Containers.dropContents(this.level(), this.blockPosition(), tempContainer);

            // 移除实体
            this.discard();
            return true;
        }

        return true;
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            // 临时功能：打印库存内容到聊天栏，方便验证
            player.sendSystemMessage(Component.literal("=== 火箭库存检查 ==="));
            boolean empty = true;
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    player.sendSystemMessage(Component.literal("槽位 " + i + ": " + stack.getItem().getName(stack).getString() + " x" + stack.getCount()));
                    empty = false;
                }
            }
            if (empty) {
                player.sendSystemMessage(Component.literal("库存为空"));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // 允许被推动（可选）
    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket(@NotNull ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }
}
