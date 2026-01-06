package yourscraft.jasdewstarfield.create_cargo_rocket.content.station;

import net.minecraft.core.BlockPos;
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

    public DockingStationBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.DOCKING_STATION.get(), pos, blockState);
    }

    /**
     * 供 ModCapabilities 调用的方法，返回我们的代理处理器
     */
    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    /**
     * 尝试寻找停靠在正上方的火箭实体。
     * 为了性能，我们只在需要时扫描，并且范围限定在方块正上方。
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
