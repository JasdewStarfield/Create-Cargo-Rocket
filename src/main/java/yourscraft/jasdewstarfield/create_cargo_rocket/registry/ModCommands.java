package yourscraft.jasdewstarfield.create_cargo_rocket.registry;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import yourscraft.jasdewstarfield.create_cargo_rocket.CreateCargoRocket;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.station.GlobalStationData;

import java.util.Map;

@EventBusSubscriber(modid = CreateCargoRocket.MODID)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("cargorocket")
                        .then(Commands.literal("allstations")
                                .requires(s -> s.hasPermission(2))
                                .executes(ModCommands::listStations)
                        )
                        .then(Commands.literal("status")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ModCommands::queryRocketStatus)
                                )
                        )
        );
    }

    private static int listStations(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        GlobalStationData data = GlobalStationData.get(level);
        Map<String, GlobalPos> map = data.getStationMap();

        if (map.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("当前没有已注册的站点。").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("=== 已注册货运站点 (" + map.size() + ") ===").withStyle(ChatFormatting.GOLD), false);

        for (Map.Entry<String, GlobalPos> entry : map.entrySet()) {
            String name = entry.getKey();
            GlobalPos pos = entry.getValue();

            // 格式化坐标文本： [维度] x, y, z
            String posText = String.format("[%s] %d, %d, %d",
                    pos.dimension().location(),
                    pos.pos().getX(), pos.pos().getY(), pos.pos().getZ());

            Component message = Component.literal("- ")
                    .append(Component.literal(name).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(": "))
                    .append(Component.literal(posText).withStyle(ChatFormatting.GRAY))
                    // 添加点击传送功能（可选，方便调试）
                    .withStyle(style -> style
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击传送")))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                    "/execute in " + pos.dimension().location() + " run tp @s " + pos.pos().getX() + " " + (pos.pos().getY() + 1) + " " + pos.pos().getZ()))
                    );

            context.getSource().sendSuccess(() -> message, false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int queryRocketStatus(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        ServerLevel level = context.getSource().getLevel();
        GlobalStationData data = GlobalStationData.get(level);

        GlobalPos pos = data.getRocketPos(name);
        if (pos == null) {
            context.getSource().sendFailure(Component.literal("Rocket not found: " + name));
            return 0;
        }

        // 找到了火箭位置
        context.getSource().sendSuccess(() -> Component.literal("Rocket '" + name + "' is at " +
                pos.dimension().location() + " " + pos.pos().toShortString()), false);
        return 1;
    }
}