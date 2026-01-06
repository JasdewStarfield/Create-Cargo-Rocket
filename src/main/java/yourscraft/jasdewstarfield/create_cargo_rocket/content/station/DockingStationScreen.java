package yourscraft.jasdewstarfield.create_cargo_rocket.content.station;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.create_cargo_rocket.network.PacketStationData;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModMessages;

public class DockingStationScreen extends Screen {
    private final BlockPos pos;
    private final String initialName;
    private EditBox nameBox;

    public DockingStationScreen(BlockPos pos, String initialName) {
        super(Component.translatable("block.create_cargo_rocket.docking_station"));
        this.pos = pos;
        this.initialName = initialName;
    }

    @Override
    protected void init() {
        super.init();

        int x = this.width / 2;
        int y = this.height / 2;

        // 添加输入框
        this.nameBox = new EditBox(this.font, x - 60, y - 20, 120, 20, Component.literal("Station Name"));
        this.nameBox.setValue(initialName);
        this.nameBox.setMaxLength(32);
        this.addRenderableWidget(this.nameBox);

        // 添加确认按钮
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
            this.confirm();
        }).bounds(x - 40, y + 10, 80, 20).build());
    }

    private void confirm() {
        // 发送数据包到服务端
        ModMessages.sendToServer(new PacketStationData(pos, nameBox.getValue()));
        this.onClose();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 绘制标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 50, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
