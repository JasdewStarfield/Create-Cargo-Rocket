package yourscraft.jasdewstarfield.create_cargo_rocket.content.rocket;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import yourscraft.jasdewstarfield.create_cargo_rocket.CreateCargoRocket;

import java.util.ArrayList;
import java.util.List;

public class CargoRocketStatus {

    private final CargoRocketEntity rocket;

    // === 状态标记 (防刷屏用) ===
    // 如果为 true，说明当前已经处于该错误状态，不再重复广播
    public boolean missingStation;
    public boolean missingTarget;
    public boolean obstruction;
    public boolean orbitingNoSignal;
    public boolean orbitingOccupied;

    // 消息队列
    private final List<Component> queued = new ArrayList<>();

    public CargoRocketStatus(CargoRocketEntity rocket) {
        this.rocket = rocket;
    }

    // === 核心逻辑: 状态报告 ===

    /**
     * 报告：下方没有有效的接驳站台
     */
    public void failedNoStation() {
        if (missingStation) return; // 如果已经报过错，跳过
        displayInformation("no_station", false);
        missingStation = true;
    }

    /**
     * 报告：找不到目标站点
     */
    public void failedNoTarget(String targetName) {
        if (missingTarget) return;
        displayInformation("no_target", false, targetName);
        missingTarget = true;
    }

    /**
     * 报告：进入轨道待命（信号丢失）
     */
    public void orbitingNoSignal(String targetName) {
        if (orbitingNoSignal) return;
        displayInformation("orbiting_no_signal", false, targetName);
        orbitingNoSignal = true;
        orbitingOccupied = false;
    }

    /**
     * 报告：进入轨道待命（目标拥堵）
     */
    public void orbitingOccupied(String targetName) {
        if (orbitingOccupied) return;
        displayInformation("orbiting_occupied", false, targetName);
        orbitingOccupied = true;
        orbitingNoSignal = false;
    }

    /**
     * 报告：发生通用阻挡/故障
     */
    public void failedObstruction(String reason) {
        if (obstruction) return;
        displayInformation("obstruction", false, reason);
        obstruction = true;
    }

    /**
     * 状态恢复：成功发射
     * 清除所有错误标记，并可选择性广播成功消息
     */
    public void successfulLaunch() {
        if (!missingStation && !missingTarget && !obstruction) return;

        // 如果之前有错误，现在恢复了，广播一条“恢复正常”的消息
        displayInformation("launch_success", true);

        // 重置所有标记
        missingStation = false;
        missingTarget = false;
        obstruction = false;
        orbitingNoSignal = false;
        orbitingOccupied = false;
    }

    /**
     * 状态恢复：结束轨道待命，准备降落/传送
     */
    public void orbitalClearanceGranted() {
        if (orbitingNoSignal || orbitingOccupied) {
            displayInformation("orbit_cleared", true);
            orbitingNoSignal = false;
            orbitingOccupied = false;
        }
    }

    /**
     * 状态恢复：手动重置
     */
    public void manualReset() {
        missingStation = false;
        missingTarget = false;
        obstruction = false;
        orbitingNoSignal = false;
        orbitingOccupied = false;
        displayInformation("manual_reset", true);
    }

    // === 消息处理 ===

    /**
     * 添加一条格式化消息到队列
     * @param key 语言文件中的后缀 (status.create_cargo_rocket.xxx)
     * @param isGood 消息类型 (true=成功/绿色, false=失败/橙色)
     * @param args 翻译参数
     */
    private void displayInformation(String key, boolean isGood, Object... args) {
        // 增加参数非空检查，将 null 替换为 "Unknown" 防止编码错误
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    args[i] = "Unknown";
                }
            }
        }

        if (args != null) {
            MutableComponent component = Component.literal(" - ").withStyle(ChatFormatting.GRAY) // 复制 Create 的配色
                    .append(Component.translatable(CreateCargoRocket.MODID + ".status." + key, args)
                            .withStyle(style -> style.withColor(isGood ? 0xD5ECC2 : 0xFFD3B4)));
            queued.add(component);
        }
    }

    public void tick(Level level) {
        if (queued.isEmpty()) return;

        // 向全服广播消息
        if (level.getServer() != null) {
            // 构造标题头: [Cargo Rocket] <Rocket Name>
            MutableComponent header = Component.literal("[Cargo Rocket] ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(rocket.getDisplayName().copy().withStyle(ChatFormatting.WHITE));

            // 先发标题
            level.getServer().getPlayerList().broadcastSystemMessage(header, false);

            // 再发所有堆积的具体原因
            for (Component message : queued) {
                level.getServer().getPlayerList().broadcastSystemMessage(message, false);
            }
        }
        queued.clear();
    }
}
