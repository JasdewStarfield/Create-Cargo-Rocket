package yourscraft.jasdewstarfield.create_cargo_rocket.content.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.create_cargo_rocket.CreateCargoRocket;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 存储服务器上所有已命名的停机坪数据。
 * 这是实现跨维度导航的核心。
 */
public class GlobalStationData extends SavedData {
    private static final String DATA_NAME = CreateCargoRocket.MODID + "_stations";

    // 存储 站点名称 -> 全局坐标 的映射
    private final Map<String, GlobalPos> stations = new HashMap<>();
    // 反向查找：坐标 -> 站点名称 (用于防止重复放置或快速查找)
    private final Map<GlobalPos, String> posToName = new HashMap<>();

    public GlobalStationData() {}

    /**
     * 获取服务端的数据实例。
     * 数据总是存储在 Overworld (主世界) 的存储管理器中，以确保全服统一。
     */
    public static GlobalStationData get(ServerLevel level) {
        return Objects.requireNonNull(level.getServer().getLevel(Level.OVERWORLD)).getDataStorage()
                .computeIfAbsent(new Factory<>(GlobalStationData::new, GlobalStationData::load), DATA_NAME);
    }

    /**
     * 注册或更新一个站点
     */
    public void addStation(String name, GlobalPos pos) {
        CreateCargoRocket.LOGGER.debug("Adding station: {} at {}", name, pos);
        // 如果该位置已经有名字了，先移除旧名字
        if (posToName.containsKey(pos)) {
            String oldName = posToName.get(pos);
            stations.remove(oldName);
        }
        // 如果该名字已经被用了，覆盖它（或者你可以抛出异常拒绝）
        if (stations.containsKey(name)) {
            GlobalPos oldPos = stations.get(name);
            posToName.remove(oldPos);
        }

        stations.put(name, pos);
        posToName.put(pos, name);
        this.setDirty(); // 标记数据已更改，需要保存到硬盘
    }

    /**
     * 移除站点（当方块被破坏时调用）
     */
    public void removeStation(GlobalPos pos) {
        if (posToName.containsKey(pos)) {
            String name = posToName.get(pos);
            CreateCargoRocket.LOGGER.debug("Removing station: {} at {}", name, pos);
            stations.remove(name);
            posToName.remove(pos);
            this.setDirty();
        }
    }

    public boolean hasStation(String name) {
        return stations.containsKey(name);
    }

    public GlobalPos getStationPos(String name) {
        return stations.get(name);
    }

    public Map<String, GlobalPos> getStationMap() {
        return Collections.unmodifiableMap(stations);
    }


    // === NBT 序列化逻辑 ===

    public static GlobalStationData load(CompoundTag tag, HolderLookup.Provider provider) {
        CreateCargoRocket.LOGGER.info("Loading Rocket GlobalStationData from disk...");
        GlobalStationData data = new GlobalStationData();

        // 检查是否存在 Stations 列表，防止空数据
        if (tag.contains("Stations", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Stations", Tag.TAG_COMPOUND);
            CreateCargoRocket.LOGGER.info("Found {} stations in NBT.", list.size());

            for (Tag t : list) {
                try {
                    CompoundTag stationTag = (CompoundTag) t;
                    String name = stationTag.getString("Name");
                    String dimStr = stationTag.getString("Dimension");

                    ResourceLocation dimLoc = ResourceLocation.parse(dimStr);
                    ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimLoc);

                    // 更加稳健的 BlockPos 读取方式
                    // NbtUtils.readBlockPos 需要读取包含 X, Y, Z 的 CompoundTag
                    BlockPos pos = NbtUtils.readBlockPos(stationTag, "Pos").orElse(BlockPos.ZERO);

                    GlobalPos globalPos = GlobalPos.of(dim, pos);
                    data.stations.put(name, globalPos);
                    data.posToName.put(globalPos, name);

                    CreateCargoRocket.LOGGER.debug("Loaded station: {}", name);
                } catch (Exception e) {
                    CreateCargoRocket.LOGGER.error("Failed to load a station entry: ", e);
                }
            }
        } else {
            CreateCargoRocket.LOGGER.info("No 'Stations' tag found in SavedData.");
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        CreateCargoRocket.LOGGER.info("Saving Rocket GlobalStationData to disk... (Count: {})", stations.size());
        ListTag list = new ListTag();
        for (Map.Entry<String, GlobalPos> entry : stations.entrySet()) {
            try {
                CompoundTag stationTag = new CompoundTag();
                stationTag.putString("Name", entry.getKey());
                stationTag.putString("Dimension", entry.getValue().dimension().location().toString());

                // 写入 Pos 作为一个 CompoundTag
                stationTag.put("Pos", NbtUtils.writeBlockPos(entry.getValue().pos()));

                list.add(stationTag);
            } catch (Exception e) {
                CreateCargoRocket.LOGGER.error("Failed to save station: {}", entry.getKey(), e);
            }
        }
        tag.put("Stations", list);
        return tag;
    }
}
