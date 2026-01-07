package yourscraft.jasdewstarfield.create_cargo_rocket.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.create_cargo_rocket.CreateCargoRocket;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.rocket.CargoRocketEntity;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.station.DockingStationBlockEntity;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.station.GlobalStationData;

public record PacketStationData(BlockPos pos, String name, boolean isRocketName) implements CustomPacketPayload {

    public static final Type<PacketStationData> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCargoRocket.MODID, "station_data"));

    public static final StreamCodec<ByteBuf, PacketStationData> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PacketStationData::pos,
            ByteBufCodecs.STRING_UTF8, PacketStationData::name,
            ByteBufCodecs.BOOL, PacketStationData::isRocketName,
            PacketStationData::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketStationData packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                // 服务端逻辑：接收名字并更新方块实体
                ServerLevel level = serverPlayer.serverLevel();
                if (serverPlayer.distanceToSqr(packet.pos.getX(), packet.pos.getY(), packet.pos.getZ()) > 64.0) {
                    return;
                }
                BlockEntity be = level.getBlockEntity(packet.pos);
                if (be instanceof DockingStationBlockEntity station) {
                    if (packet.isRocketName) {
                        // 改火箭名
                        CargoRocketEntity rocket = station.getRocket();

                        if (rocket != null) {
                            GlobalStationData data = GlobalStationData.get(level);
                            String finalName = packet.name;
                            if (!packet.name.equals(rocket.getRocketName())) {
                                finalName = data.getUniqueRocketName(packet.name);
                            }

                            rocket.setRocketName(finalName);

                            if (!finalName.equals(packet.name)) {
                                serverPlayer.sendSystemMessage(Component.literal("Name taken! Renamed to: " + finalName).withStyle(ChatFormatting.YELLOW));
                            } else {
                                serverPlayer.sendSystemMessage(Component.literal("Rocket renamed to: " + finalName));
                            }
                        }

                    } else {
                        // 改站台名 (原有逻辑)
                        station.updateName(packet.name, serverPlayer);
                    }
                }
            }
        });
    }
}
