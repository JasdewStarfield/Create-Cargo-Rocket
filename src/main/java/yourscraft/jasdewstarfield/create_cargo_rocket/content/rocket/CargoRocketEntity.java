package yourscraft.jasdewstarfield.create_cargo_rocket.content.rocket;

import com.simibubi.create.AllDataComponents;
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
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.station.DockingStationBlock;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.station.GlobalStationData;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModBlocks;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModEntities;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModItems;

import javax.annotation.Nullable;
import java.util.Objects;

public class CargoRocketEntity extends Entity {

    //状态机枚举
    public enum RocketState {
        IDLE,       // 停靠/空闲
        LAUNCHING,  // 起飞（向上加速）
        FLIGHT,     // 飞行中（准备传送）
        ORBITING,   // 轨道待命（目标无效或被占用）
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
    private int orbitTicker = 0; // 轨道扫描计时器

    private GlobalPos targetStationPos; // 目标站点坐标缓存
    private String targetStationName;

    public final CargoRocketStatus status;

    // 区块加载管理 (Chunk Loading)
    private ChunkPos forcedChunk; // 当前火箭所在的强加载区块
    private GlobalPos forcedTargetChunk; // 远程强加载的目标区块（用于轨道待命/飞行时）

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
        if (compound.contains("TargetName")) {
            this.targetStationName = compound.getString("TargetName");
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
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);

        // 1. 扳手拆卸 (潜行 + 扳手)
        if (stack.is(Tags.Items.TOOLS_WRENCH)) {
            if (level().isClientSide) return InteractionResult.SUCCESS;

            if (player.isShiftKeyDown()) {
                dismantleRocket(player);
            } else {
                // （临时）调试信息
                player.sendSystemMessage(Component.literal("State: " + getRocketState() + " | Cooldown: " + cooldown));
                if (schedule != null) {
                    String next = getNextStationName();
                    player.sendSystemMessage(Component.literal("Next Station: " + (next == null ? "None" : next)));
                }
                player.sendSystemMessage(Component.literal("Chunk: " + (forcedChunk == null ? "None" : forcedChunk.toString())));
            }

            return InteractionResult.SUCCESS;
        }

        // 2. 如果处于 STUCK 状态，允许玩家右键重置
        if (getRocketState() == RocketState.STUCK) {
            if (level().isClientSide) return InteractionResult.SUCCESS;

            status.manualReset();
            setRocketState(RocketState.IDLE);
            this.cooldown = 0;
            return InteractionResult.SUCCESS;
        }

        // 只能在 idle 状态下编辑排班
        if (getRocketState() != RocketState.IDLE) {
            return InteractionResult.PASS;
        }

        // 3. 如果玩家拿着 Create 的时刻表
        if (AllItems.SCHEDULE.isIn(stack)) {
            if (level().isClientSide) return InteractionResult.SUCCESS;

            // 获取时刻表内的数据
            Schedule newSchedule = ScheduleItem.getSchedule(level().registryAccess(), stack);

            // 如果是空的
            if (newSchedule == null) {
                return InteractionResult.PASS;
            }

            // 如果时刻表没写指令
            if (newSchedule.entries.isEmpty()) {
                player.displayClientMessage(Component.translatable("create.schedule.no_stops").withStyle(ChatFormatting.RED), true);
                AllSoundEvents.DENY.playOnServer(level(), blockPosition(), 1, 1);
                return InteractionResult.SUCCESS;
            }

            // 取出原有时刻表
            if (this.schedule != null) {
                getScheduleCopy(player);
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

        // 4. 空手右键取出时刻表
        if (stack.isEmpty()) {
            // 如果火箭里有时刻表
            if (this.schedule != null) {
                if (level().isClientSide) return InteractionResult.SUCCESS;

                dropSchedule(player);
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    private void dropSchedule(Player player) {
        if (this.schedule == null) return;

        getScheduleCopy(player);

        AllSoundEvents.playItemPickup(player);
        player.displayClientMessage(Component.translatable("create.schedule.removed_from_train"), true);

        this.schedule = null;
        this.scheduleEntryIndex = 0;
    }

    private void dismantleRocket(Player player) {
        if (this.level().isClientSide || this.isRemoved()) return;

        // 掉落火箭物品
        ItemStack rocketStack = new ItemStack(ModItems.CARGO_ROCKET.get());
        if (player instanceof ServerPlayer sp) {
            if (!player.isCreative()) {
                sp.getInventory().placeItemBackInInventory(rocketStack);
            }
        } else {
            this.spawnAtLocation(rocketStack);
        }

        // 掉落时刻表
        if (this.schedule != null) {
            getScheduleCopy(player);
        }

        // 掉落库存
        SimpleContainer tempContainer = new SimpleContainer(inventory.getSlots());
        for (int i = 0; i < inventory.getSlots(); i++) {
            tempContainer.setItem(i, inventory.getStackInSlot(i));
        }
        Containers.dropContents(this.level(), this.blockPosition(), tempContainer);

        // 播放扳手音效
        AllSoundEvents.WRENCH_ROTATE.playOnServer(level(), blockPosition(), 1, 1);

        // 移除实体
        this.discard();
    }

    private void getScheduleCopy(Player player) {
        if (this.schedule == null) return;

        ItemStack stack = AllItems.SCHEDULE.asStack();
        this.schedule.savedProgress = this.scheduleEntryIndex;
        stack.set(AllDataComponents.TRAIN_SCHEDULE, this.schedule.write(level().registryAccess()));

        if (player instanceof ServerPlayer sp) {
            sp.getInventory().placeItemBackInInventory(stack);
        } else {
            spawnAtLocation(stack);
        }
    }

    @Override
    public void tick() {
        super.tick();

        // 如果不是 IDLE 状态，我们完全接管运动（不应用默认重力，因为我们在飞）
        // 如果是 IDLE 状态，我们施加一个简单的重力，确保它贴在地上
        if (!isNoGravity()) {
            RocketState state = getRocketState();
            if (state == RocketState.IDLE || state == RocketState.STUCK) {
                // 模拟标准实体重力 (约 0.08/tick)
                this.setDeltaMovement(this.getDeltaMovement().add(0, -0.08, 0));
            }else if (state == RocketState.ORBITING) {
                // 轨道悬停：保持垂直速度为0
                this.setDeltaMovement(0, 0, 0);
            }
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));

        if (!level().isClientSide) {
            manageChunkLoading();
            status.tick(level());
            // 状态机逻辑
            switch (getRocketState()) {
                case IDLE -> tickIdle();
                case LAUNCHING -> tickLaunching();
                case FLIGHT -> tickFlight();
                case ORBITING -> tickOrbiting();
                case LANDING -> tickLanding();
                case STUCK -> { /* 等待玩家操作 */ }
            }
        }
    }

    private void manageChunkLoading() {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        // 1. 强制加载当前所在的区块 (Self Loading)
        ChunkPos currentPos = this.chunkPosition();
        if (forcedChunk == null || !forcedChunk.equals(currentPos)) {
            // 释放旧的
            if (forcedChunk != null) {
                serverLevel.setChunkForced(forcedChunk.x, forcedChunk.z, false);
            }
            // 加载新的
            serverLevel.setChunkForced(currentPos.x, currentPos.z, true);
            forcedChunk = currentPos;
        }

        // 2. 强制加载目标区块 (Target Loading)
        // 只有在需要远程检测 (Flight/Orbiting) 或即将抵达 (Landing) 时才加载
        RocketState state = getRocketState();
        boolean needsTargetLoaded = (state == RocketState.FLIGHT || state == RocketState.ORBITING || state == RocketState.LANDING)
                && targetStationPos != null;

        if (needsTargetLoaded) {
            // 检查目标维度是否已加载
            ServerLevel targetLevel = serverLevel.getServer().getLevel(targetStationPos.dimension());
            if (targetLevel != null) {
                ChunkPos targetChunk = new ChunkPos(targetStationPos.pos());
                GlobalPos targetGlobal = GlobalPos.of(targetStationPos.dimension(), targetStationPos.pos());

                // 如果目标发生了变化（或者之前没加载），则更新
                if (forcedTargetChunk == null || !forcedTargetChunk.equals(targetGlobal)) {
                    // 释放旧目标的加载（如果存在且不仅是当前位置）
                    releaseTargetChunk();

                    // 加载新目标
                    targetLevel.setChunkForced(targetChunk.x, targetChunk.z, true);
                    forcedTargetChunk = targetGlobal;
                }
            }
        } else {
            // 如果不需要加载目标了（比如回到 IDLE），释放目标区块
            releaseTargetChunk();
        }
    }

    private void releaseTargetChunk() {
        if (forcedTargetChunk != null) {
            ServerLevel oldLevel = Objects.requireNonNull(level().getServer()).getLevel(forcedTargetChunk.dimension());
            if (oldLevel != null) {
                ChunkPos oldChunk = new ChunkPos(forcedTargetChunk.pos());
                oldLevel.setChunkForced(oldChunk.x, oldChunk.z, false);
            }
            forcedTargetChunk = null;
        }
    }

    private void releaseAllForcedChunks() {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        // 释放自身
        if (forcedChunk != null) {
            serverLevel.setChunkForced(forcedChunk.x, forcedChunk.z, false);
            forcedChunk = null;
        }
        // 释放目标
        releaseTargetChunk();
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

        BlockPos below = findStationBelow();
        if (below != null) {
            setStationOccupied(level(), below, true);
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

            // Check B: 头顶有无遮挡？
            if (isObstructed(level(), blockPosition().above())) {
                status.failedObstruction("Sky Blocked");
                this.setRocketState(RocketState.STUCK);
                return;
            }

            // Check C: 目标站点是否存在？
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
            this.targetStationName = stationName;
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
            // 释放下方的站台
            BlockPos stationPos = findStationBelow();
            if (stationPos != null) {
                setStationOccupied(level(), stationPos, false);
            }
            this.setRocketState(RocketState.FLIGHT);
        }
    }

    /**
     * FLIGHT: 瞬间移动逻辑 (跨维度传送)
     */
    private void tickFlight() {
        tickOrbiting();
    }

    /**
     * ORBITING: 轨道待命
     * 保持在高空，定期扫描目标。
     */
    private void tickOrbiting() {
        // 如果是纯 ORBITING 状态，我们加上延时以节省性能
        if (getRocketState() == RocketState.ORBITING) {
            orbitTicker++;
            if (orbitTicker < 20) return;
            orbitTicker = 0;
        }

        // 1. 基础数据检查
        if (targetStationPos == null) {
            handleTargetLost();
            return;
        }

        ServerLevel targetLevel = ((ServerLevel) level()).getServer().getLevel(targetStationPos.dimension());
        if (targetLevel == null) {
            this.setRocketState(RocketState.STUCK);
            status.failedObstruction("Unknown Dimension");
            return;
        }

        // 2. 检查方块是否有效
        BlockPos destPos = targetStationPos.pos();
        BlockState destState = targetLevel.getBlockState(destPos);

        if (!destState.is(ModBlocks.DOCKING_STATION.get())) {
            // 目标位置不是站台？尝试用名字重新搜索
            if (this.targetStationName != null && !this.targetStationName.isEmpty()) {
                GlobalStationData data = GlobalStationData.get(targetLevel);
                GlobalPos newPos = data.getStationPos(this.targetStationName);

                // 如果找到了新位置
                if (newPos != null && !newPos.equals(this.targetStationPos)) {
                    this.targetStationPos = newPos;
                    // 递归调用自己，立即检查新位置
                    tickOrbiting();
                    return;
                }
            }

            // 还是找不到，或者名字没变但方块没了
            if (getRocketState() != RocketState.ORBITING) this.setRocketState(RocketState.ORBITING);
            status.orbitingNoSignal(this.targetStationName);
            return;
        }

        // 3. 检查占用
        if (destState.getValue(DockingStationBlock.OCCUPIED)) {
            if (getRocketState() != RocketState.ORBITING) this.setRocketState(RocketState.ORBITING);
            status.orbitingOccupied(this.targetStationName);
            return;
        }

        // 4. 检查降落区是否有遮挡
        // 扫描目标站台上方的空间
        if (isObstructed(targetLevel, destPos.above())) {
            if (getRocketState() != RocketState.ORBITING) this.setRocketState(RocketState.ORBITING);
            status.failedObstruction("Landing Zone Blocked"); // Landing Zone Blocked
            return;
        }

        // === 一切正常，执行传送 ===
        status.orbitalClearanceGranted();
        setStationOccupied(targetLevel, destPos, true); // 预订
        performTeleport(targetLevel, destPos);
    }

    private void handleTargetLost() {
        this.setRocketState(RocketState.STUCK);
        status.failedObstruction("Target Lost");
    }

    private void performTeleport(ServerLevel targetLevel, BlockPos destPos) {
        // 设置新位置：目标正上方高空
        double destX = destPos.getX() + 0.5;
        double destY = targetLevel.getMaxBuildHeight() + 20;
        double destZ = destPos.getZ() + 0.5;

        Vec3 targetPos = new Vec3(destX, destY, destZ);
        ServerLevel currentLevel = (ServerLevel) level();

        // 如果跨维度
        if (targetLevel != currentLevel) {
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
            this.setRocketState(RocketState.LANDING);
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

    // === 辅助方法 ===

    // 扫描正下方寻找站台（适用于高空或者贴地）
    @Nullable
    private BlockPos findStationBelow() {
        BlockPos.MutableBlockPos p = this.blockPosition().mutable();
        // 从当前位置向下扫描直到世界底部
        for (int y = p.getY(); y >= level().getMinBuildHeight(); y--) {
            p.setY(y);
            if (level().getBlockState(p).is(ModBlocks.DOCKING_STATION.get())) {
                return p.immutable();
            }
        }
        return null;
    }

    /**
     * 遮挡检查,检查从 startPos 向上直到世界顶端是否有阻挡的方块
     */
    private boolean isObstructed(Level level, BlockPos startPos) {
        int maxY = level.getMaxBuildHeight();
        BlockPos.MutableBlockPos p = startPos.mutable();

        for (int y = startPos.getY(); y < maxY; y++) {
            p.setY(y);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // 修改站台 BlockState
    private void setStationOccupied(Level level, BlockPos pos, boolean occupied) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.DOCKING_STATION.get()) && state.hasProperty(DockingStationBlock.OCCUPIED)) {
            if (state.getValue(DockingStationBlock.OCCUPIED) != occupied) {
                level.setBlock(pos, state.setValue(DockingStationBlock.OCCUPIED, occupied), 3);
            }
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

        // 锁定脚下的站台
        BlockPos below = findStationBelow();
        if (below != null) {
            setStationOccupied(level(), below, true);
        }
    }

    // === 基础实体逻辑 ===
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        // 允许掉出世界死亡 (Void Damage)
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (!level().isClientSide) {
                this.discard();
            }
            return true;
        }

        // 允许创造模式玩家左键移除
        if (source.getEntity() instanceof Player player && player.isCreative()) {
            if (!level().isClientSide) {
                this.discard();
            }
            return true;
        }

        // 免疫其他所有伤害 (生存模式玩家、怪物等)
        return false;
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (!level().isClientSide) {
            // 1. 释放所有强制加载的区块
            releaseAllForcedChunks();

            // 2. 如果实体是被移除，而不是因为跨维度传送，则尝试解锁下方站台的占用状态
            if (reason != RemovalReason.CHANGED_DIMENSION) {
                BlockPos stationPos = findStationBelow();
                if (stationPos != null) {
                    setStationOccupied(level(), stationPos, false);
                }
            }
        }
        super.remove(reason);
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
