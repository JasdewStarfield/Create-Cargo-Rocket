package yourscraft.jasdewstarfield.create_cargo_rocket.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.create_cargo_rocket.CreateCargoRocket;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.station.DockingStationBlockEntity;
import net.minecraft.resources.ResourceLocation;

public record PacketStationData(BlockPos pos, String name) implements CustomPacketPayload {

    public static final Type<PacketStationData> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCargoRocket.MODID, "station_data"));

    public static final StreamCodec<ByteBuf, PacketStationData> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PacketStationData::pos,
            ByteBufCodecs.STRING_UTF8, PacketStationData::name,
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
                    station.updateName(packet.name, serverPlayer);
                }
            }
        });
    }
}
