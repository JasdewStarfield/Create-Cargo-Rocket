package yourscraft.jasdewstarfield.create_cargo_rocket.content.rocket;

import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleItem;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.station.GlobalStationData;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlocks;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModEntities;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModItems;

import javax.annotation.Nullable;

public class CargoRocketEntity extends Entity {

    //状态机枚举
    public enum RocketState {
        IDLE,       // 停靠/空闲
        LAUNCHING,  // 起飞（向上加速）
        FLIGHT,     // 飞行中（准备传送）
        LANDING,    // 降落（向下减速）
        STUCK       // 故障/被卡住 (等待玩家处理)
    }

    // === 同步数据 ===
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(CargoRocketEntity.class, EntityDataSerializers.INT);

    // 27格库存，类似潜影盒或单箱
    public final ItemStackHandler inventory = new ItemStackHandler(27) {};

    // === 调度系统字段 ===
    @Nullable
    private Schedule schedule;
    private int scheduleEntryIndex = 0; // 当前执行到第几条指令
    private boolean isAutoSchedule = false; // 是否是自动调度

    // 用于逻辑判断的临时变量
    private int cooldown = 0; // 冷却计时
    private GlobalPos targetStationPos; // 目标站点坐标缓存

    public final CargoRocketStatus status;

    public CargoRocketEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.status = new CargoRocketStatus(this);
    }

    public CargoRocketEntity(Level level, double x, double y, double z) {
        this(ModEntities.CARGO_ROCKET.get(), level);
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(STATE, RocketState.IDLE.ordinal());
    }

    // === Getter/Setter for State ===
    public RocketState getRocketState() {
        return RocketState.values()[this.entityData.get(STATE)];
    }

    public void setRocketState(RocketState state) {
        this.entityData.set(STATE, state.ordinal());
    }

    // === NBT 保存与读取 ===
    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.inventory.deserializeNBT(level().registryAccess(), compound.getCompound("Inventory"));

        if (compound.contains("Schedule")) {
            this.schedule = Schedule.fromTag(level().registryAccess(), compound.getCompound("Schedule"));
            this.scheduleEntryIndex = compound.getInt("ScheduleEntry");
            this.isAutoSchedule = compound.getBoolean("IsAutoSchedule");
        }

        this.cooldown = compound.getInt("Cooldown");
        setRocketState(RocketState.values()[compound.getInt("RocketState")]);

        // 如果在飞行中保存了，尝试恢复目标
        if (compound.contains("TargetDimension")) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(compound.getString("TargetDimension")));
            BlockPos pos = new BlockPos(compound.getInt("TargetX"), compound.getInt("TargetY"), compound.getInt("TargetZ"));
            this.targetStationPos = GlobalPos.of(dim, pos);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.put("Inventory", this.inventory.serializeNBT(level().registryAccess()));

        if (schedule != null) {
            compound.put("Schedule", schedule.write(level().registryAccess()));
            compound.putInt("ScheduleEntry", scheduleEntryIndex);
            compound.putBoolean("IsAutoSchedule", isAutoSchedule);
        }

        compound.putInt("Cooldown", cooldown);
        compound.putInt("RocketState", getRocketState().ordinal());

        if (targetStationPos != null) {
            compound.putString("TargetDimension", targetStationPos.dimension().location().toString());
            compound.putInt("TargetX", targetStationPos.pos().getX());
            compound.putInt("TargetY", targetStationPos.pos().getY());
            compound.putInt("TargetZ", targetStationPos.pos().getZ());
        }
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);

        // 0. 如果处于 STUCK 状态，允许玩家右键重置
        if (getRocketState() == RocketState.STUCK) {
            status.manualReset();
            setRocketState(RocketState.IDLE);
            this.cooldown = 0;
            return InteractionResult.SUCCESS;
        }

        // 1. 如果玩家拿着 Create 的时刻表
        if (AllItems.SCHEDULE.isIn(stack)) {
            if (level().isClientSide) return InteractionResult.SUCCESS;

            // 获取时刻表内的数据
            Schedule newSchedule = ScheduleItem.getSchedule(level().registryAccess(), stack);

            // 如果时刻表是空的（没数据）
            if (newSchedule == null) {
                // 如果火箭里有时刻表，玩家想拿下来
                if (this.schedule != null) {
                    dropSchedule(player);
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.PASS;
            }

            // 如果时刻表没写指令
            if (newSchedule.entries.isEmpty()) {
                player.displayClientMessage(Component.translatable("create.schedule.no_stops").withStyle(ChatFormatting.RED), true);
                AllSoundEvents.DENY.playOnServer(level(), blockPosition(), 1, 1);
                return InteractionResult.SUCCESS;
            }

            // 应用新时刻表
            this.schedule = newSchedule;
            this.scheduleEntryIndex = 0; // 重置进度
            this.isAutoSchedule = false; // 玩家手动放的
            this.cooldown = 0;
            this.setRocketState(RocketState.IDLE);

            player.displayClientMessage(Component.translatable("create.schedule.applied_to_train").withStyle(ChatFormatting.GREEN), true);
            AllSoundEvents.CONFIRM.playOnServer(level(), blockPosition(), 1, 1);

            // 消耗物品（如果不是创造模式）
            if (!player.isCreative()) {
                stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }

        // 2. 调试用
        if (hand == InteractionHand.MAIN_HAND && stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("State: " + getRocketState() + " | Cooldown: " + cooldown));
            if (schedule != null) {
                String next = getNextStationName();
                player.sendSystemMessage(Component.literal("Next Station: " + (next == null ? "None" : next)));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private void dropSchedule(Player player) {
        if (this.schedule == null) return;

        ItemStack stack = AllItems.SCHEDULE.asStack();
        this.schedule.savedProgress = this.scheduleEntryIndex;
        stack.set(com.simibubi.create.AllDataComponents.TRAIN_SCHEDULE, this.schedule.write(level().registryAccess()));

        if (player instanceof ServerPlayer sp) {
            sp.getInventory().placeItemBackInInventory(stack);
        } else {
            spawnAtLocation(stack);
        }

        AllSoundEvents.playItemPickup(player);
        player.displayClientMessage(Component.translatable("create.schedule.removed_from_train"), true);

        this.schedule = null;
        this.scheduleEntryIndex = 0;
    }

    @Override
    public void tick() {
        super.tick();

        // 如果不是 IDLE 状态，我们完全接管运动（不应用默认重力，因为我们在飞）
        // 如果是 IDLE 状态，我们施加一个简单的重力，确保它贴在地上
        if (!isNoGravity()) {
            if (getRocketState() == RocketState.IDLE || getRocketState() == RocketState.STUCK) {
                // 模拟标准实体重力 (约 0.08/tick)
                this.setDeltaMovement(this.getDeltaMovement().add(0, -0.08, 0));
            }
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));

        if (!level().isClientSide) {
            status.tick(level());
            // 状态机逻辑
            switch (getRocketState()) {
                case IDLE -> tickIdle();
                case LAUNCHING -> tickLaunching();
                case FLIGHT -> tickFlight();
                case LANDING -> tickLanding();
                case STUCK -> { /* 等待玩家操作 */ }
            }
        }
    }

    /**
     * IDLE: 停在地面，等待冷却结束，寻找下一个目标
     */
    private void tickIdle() {
        // 如果有冷却，倒计时
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // 寻找下一站
        String stationName = getNextStationName();
        if (stationName != null) {
            // === 发射前检查 (Pre-flight Checks) ===
            // Check A: 脚下是否是接驳站台？
            BlockPos stationPos = this.blockPosition().below();
            if (!level().getBlockState(stationPos).is(ModBlocks.DOCKING_STATION.get())) {
                status.failedNoStation();
                this.setRocketState(RocketState.STUCK);
                return;
            }

            // Check B: 目标站点是否存在？
            GlobalStationData data = GlobalStationData.get((ServerLevel) level());
            GlobalPos dest = data.getStationPos(stationName);
            if (dest == null) {
                status.failedNoTarget(stationName);
                this.setRocketState(RocketState.STUCK);
                return;
            }

            // === 检查通过，发射！ ===
            status.successfulLaunch();
            this.targetStationPos = dest;
            this.setRocketState(RocketState.LAUNCHING);

            // 播放起飞音效
            level().playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 1f, 0.5f);
        }
    }

    /**
     * LAUNCHING: 向上加速飞行，直到达到一定高度
     */
    private void tickLaunching() {
        // 向上加速
        this.setDeltaMovement(0, 0.8, 0);
        this.hasImpulse = true;

        // 如果飞得足够高 (例如 Y > 300 或 相对高度 +50)
        if (this.getY() > level().getMaxBuildHeight() + 20) {
            this.setRocketState(RocketState.FLIGHT);
        }
    }

    /**
     * FLIGHT: 瞬间移动逻辑 (跨维度传送)
     */
    private void tickFlight() {
        if (targetStationPos == null) {
            // 丢失目标
            this.setRocketState(RocketState.STUCK);
            status.failedObstruction("Target Pos Lost");
            return;
        }

        // 刷新站点位置
        GlobalStationData data = GlobalStationData.get((ServerLevel) level());
        ServerLevel currentLevel = (ServerLevel) level();
        ServerLevel targetLevel = currentLevel.getServer().getLevel(targetStationPos.dimension());

        if (targetLevel == null) {
            // 目标维度不存在
            this.setRocketState(RocketState.STUCK);
            status.failedObstruction("Unknown Dimension");
            return;
        }

        // 设置新位置：目标正上方高空
        double destX = targetStationPos.pos().getX() + 0.5;
        double destY = targetLevel.getMaxBuildHeight() + 20; // 从高空降落
        double destZ = targetStationPos.pos().getZ() + 0.5;

        Vec3 targetPos = new Vec3(destX, destY, destZ);

        // 如果跨维度
        if (targetLevel != currentLevel) {
            // 构建传送参数
            // 参数顺序: 目标世界, 目标位置, 目标速度(0), Y轴旋转, X轴旋转, 传送后回调
            DimensionTransition transition = new DimensionTransition(
                    targetLevel,
                    targetPos,
                    Vec3.ZERO,
                    this.getYRot(),
                    this.getXRot(),
                    DimensionTransition.DO_NOTHING
            );

            Entity newEntity = this.changeDimension(transition);
            if (newEntity instanceof CargoRocketEntity rocket) {
                rocket.setRocketState(RocketState.LANDING);
            }
        } else {
            // 同维度传送
            this.teleportTo(destX, destY, destZ);
            // 切换到降落状态
            this.setRocketState(RocketState.LANDING);
            // 清除速度
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    /**
     * LANDING: 向下飞行，直到触地
     */
    private void tickLanding() {
        // 向下加速
        this.setDeltaMovement(0, -1, 0);
        this.hasImpulse = true;

        // 检测着陆：如果垂直速度为0（撞地了）或者检测到下方有方块
        if (this.onGround()) {
            this.setRocketState(RocketState.IDLE);
            onArrival();
        }
    }

    // === 调度辅助方法 ===
    public String getNextStationName() {
        if (this.schedule == null || this.schedule.entries.isEmpty()) {
            return null;
        }

        int attempts = 0;
        int maxAttempts = this.schedule.entries.size();

        while (attempts < maxAttempts) {
            // 1. 获取当前的条目 (Entry)
            // 防止索引越界
            if (this.scheduleEntryIndex >= this.schedule.entries.size()) {
                if (this.schedule.cyclic) {
                    this.scheduleEntryIndex = 0; // 循环模式：回到第一站
                } else {
                    return null; // 非循环模式：跑完了，停下
                }
            }

            ScheduleEntry currentEntry = this.schedule.entries.get(this.scheduleEntryIndex);

            // 2. 只有当指令是“前往目的地”时，我们才处理
            if (currentEntry.instruction instanceof DestinationInstruction destination) {
                return destination.getFilter();
            }

            // 3. 如果这一条指令不是去某地（比如只是改个名），我们就跳过它，找下一条
            this.scheduleEntryIndex++;
            attempts++;
        }

        return null;
    }

    // 火箭到达目的地后调用的方法
    public void onArrival() {
        this.scheduleEntryIndex++;
        // 强制休息 200 tick (10秒)
        this.cooldown = 200;
    }

    // === 基础实体逻辑 (受伤、死亡等) ===
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
                if (this.schedule != null) {
                    ItemStack scheduleStack = AllItems.SCHEDULE.asStack();
                    scheduleStack.set(com.simibubi.create.AllDataComponents.TRAIN_SCHEDULE, this.schedule.write(level().registryAccess()));
                    this.spawnAtLocation(scheduleStack);
                }
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
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
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
