package yourscraft.jasdewstarfield.create_cargo_rocket.registry;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import yourscraft.jasdewstarfield.create_cargo_rocket.CreateCargoRocket;
import yourscraft.jasdewstarfield.create_cargo_rocket.network.PacketStationData;

@EventBusSubscriber(modid = CreateCargoRocket.MODID)
public class ModMessages {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // 注册站台数据包 (客户端 -> 服务端)
        registrar.playBidirectional(
                PacketStationData.TYPE,
                PacketStationData.STREAM_CODEC,
                PacketStationData::handle
        );
    }

    public static void sendToServer(Object message) {
        if (message instanceof PacketStationData packet) {
            PacketDistributor.sendToServer(packet);
        }
    }

    public static void sendToPlayer(Object message, ServerPlayer player) {
        if (message instanceof PacketStationData packet) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }
}