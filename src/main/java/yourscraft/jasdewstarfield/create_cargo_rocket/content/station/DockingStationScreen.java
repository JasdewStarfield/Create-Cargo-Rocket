package yourscraft.jasdewstarfield.create_cargo_rocket.content.station;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import yourscraft.jasdewstarfield.create_cargo_rocket.content.rocket.CargoRocketEntity;
import yourscraft.jasdewstarfield.create_cargo_rocket.network.PacketStationData;
import yourscraft.jasdewstarfield.create_cargo_rocket.registry.ModMessages;

import java.lang.ref.WeakReference;
import java.util.UUID;

public class DockingStationScreen extends AbstractSimiScreen {

    protected AllGuiTextures background;
    private final BlockPos pos;
    private final String initialStationName;

    // 输入框
    private EditBox nameBox;
    private EditBox rocketNameBox;

    // 按钮
    private IconButton confirmButton;

    // 缓存火箭实体，用于显示
    private WeakReference<CargoRocketEntity> displayedRocket;
    private boolean rocketPresent;
    private UUID currentRocketId;

    public DockingStationScreen(BlockPos pos, String initialName) {
        super(Component.translatable("block.create_cargo_rocket.docking_station"));
        this.pos = pos;
        this.initialStationName = initialName;
        this.background = AllGuiTextures.STATION; // 复用 Create 的车站背景
        this.displayedRocket = new WeakReference<>(null);
    }

    @Override
    protected void init() {
        setWindowSize(background.getWidth(), background.getHeight());
        super.init();

        int x = guiLeft;
        int y = guiTop;

        // 1. 站台名称输入框 (顶部)
        // 模仿 StationScreen 的位置和样式
        nameBox = new EditBox(this.font, x + 23, y + 4, background.getWidth() - 20, 10, Component.literal(initialStationName));
        nameBox.setBordered(false);
        nameBox.setMaxLength(25);
        nameBox.setTextColor(0x592424); // Create 风格的深红色字体
        nameBox.setValue(initialStationName);
        nameBox.setFocused(false);
        nameBox.setResponder(s -> nameBox.setX(nameBoxX(s, nameBox)));
        nameBox.setX(nameBoxX(initialStationName, nameBox));
        addRenderableWidget(nameBox);

        // 2. 火箭名称输入框 (中间，默认隐藏/禁用)
        rocketNameBox = new EditBox(font, x + 23, y + 47, background.getWidth() - 75, 10, CommonComponents.EMPTY);
        rocketNameBox.setBordered(false);
        rocketNameBox.setMaxLength(35);
        rocketNameBox.setTextColor(0xC6C6C6); // 灰色字体
        rocketNameBox.setFocused(false);
        rocketNameBox.active = false;
        rocketNameBox.visible = false;
        rocketNameBox.setResponder(s -> rocketNameBox.setX(nameBoxX(s, rocketNameBox)));
        addRenderableWidget(rocketNameBox);

        // 3. 确认按钮 (右下角)
        confirmButton = new IconButton(x + background.getWidth() - 33, y + background.getHeight() - 24, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::confirmAndClose);
        addRenderableWidget(confirmButton);

        checkForRocket();
    }

    private int nameBoxX(String s, EditBox box) {
        int textWidth = Math.min(font.width(s), box.getWidth());
        return guiLeft + background.getWidth() / 2 - (textWidth + 10) / 2;
    }

    private void checkForRocket() {
        if (this.minecraft != null && this.minecraft.level != null) {
            DockingStationBlockEntity be = (DockingStationBlockEntity) this.minecraft.level.getBlockEntity(pos);
            if (be != null) {
                CargoRocketEntity rocket = be.getRocket(); // 复用你现有的 getRocket() 方法
                if (rocket != null) {
                    this.displayedRocket = new WeakReference<>(rocket);
                    this.rocketPresent = true;

                    if (!rocket.getUUID().equals(currentRocketId)) {
                        currentRocketId = rocket.getUUID();
                        rocketNameBox.setValue(rocket.getRocketName());
                        rocketNameBox.setX(nameBoxX(rocket.getRocketName(), rocketNameBox));
                    }

                    // 激活火箭名输入框
                    rocketNameBox.active = true;
                    rocketNameBox.visible = true;
                    return;
                }
            }
        }

        // 如果没有火箭
        this.rocketPresent = false;
        this.displayedRocket = new WeakReference<>(null);
        this.currentRocketId = null;
        rocketNameBox.active = false;
        rocketNameBox.visible = false;
    }

    @Override
    public void tick() {
        super.tick();
        // 每 tick 检查一下火箭还在不在 (防止玩家打开界面时火箭飞走了)
        checkForRocket();

        // 文本框光标逻辑
        if (getFocused() != nameBox) {
            nameBox.setCursorPosition(nameBox.getValue().length());
            nameBox.setHighlightPos(nameBox.getCursorPosition());
        }
        if (getFocused() != rocketNameBox) {
            rocketNameBox.setCursorPosition(rocketNameBox.getValue().length());
            rocketNameBox.setHighlightPos(rocketNameBox.getCursorPosition());
        }
    }

    @Override
    protected void renderWindow(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        // 1. 绘制背景
        background.render(graphics, x, y);

        // 2. 绘制站台名称框的装饰 (如果没聚焦，画笔形图标)
        String text = nameBox.getValue();
        if (!nameBox.isFocused()) {
            int boxX = nameBoxX(text, nameBox);
            AllGuiTextures.STATION_EDIT_NAME.render(graphics, boxX + font.width(text) + 5, y + 1);
        }

        // 3. 绘制火箭展示区域
        if (!rocketPresent) {
            // 如果没有火箭，显示 "Station Idle" 文本
            MutableComponent header = CreateLang.translateDirect("station.idle"); // 或者用 Component.literal("No Rocket")
            graphics.drawString(font, header, x + 97 - font.width(header) / 2, y + 47, 0x7A7A7A, false);
        } else {
            // 有火箭，绘制火箭模型
            CargoRocketEntity rocket = displayedRocket.get();
            if (rocket != null) {
                renderRocketEntity(graphics, x + background.getWidth() / 2, y + 40, 3, rocket);
            }

            // 绘制文本框背景装饰
            AllGuiTextures.STATION_TEXTBOX_TOP.render(graphics, x + 21, y + 42);
            UIRenderHelper.drawStretched(graphics, x + 21, y + 60, 150, 26, 0, AllGuiTextures.STATION_TEXTBOX_MIDDLE);
            AllGuiTextures.STATION_TEXTBOX_BOTTOM.render(graphics, x + 21, y + 86);

            // 语音气泡小箭头
            PoseStack ms = graphics.pose();
            ms.pushPose();
            ms.translate(80, 0, 0); // 调整位置指向中间
            AllGuiTextures.STATION_TEXTBOX_SPEECH.render(graphics, x, y + 38);
            ms.popPose();

            // 绘制火箭名编辑图标
            text = rocketNameBox.getValue();
            if (!rocketNameBox.isFocused()) {
                int buttonX = nameBoxX(text, rocketNameBox) + font.width(text) + 5;
                AllGuiTextures.STATION_EDIT_TRAIN_NAME.render(graphics, Math.min(buttonX, guiLeft + 156), y + 44);
            }
        }
    }

    /**
     * 在界面中央绘制一个旋转的火箭物品/模型
     */
    private void renderRocketEntity(GuiGraphics graphics, int x, int y, int scale, Entity entity) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        // 1. 移到指定位置
        poseStack.translate(x, y, 50.0F);
        // 2. 缩放 (注意 Y 轴反转)
        poseStack.scale((float) scale, (float) scale, (float) -scale);

        // 3. 旋转控制
        // 计算旋转角度 (每 5 tick 旋转一圈，慢慢转看起来比较高级)
        float rotation = (float)(System.currentTimeMillis() % 36000) / 100f;

        Quaternionf rotZ = Axis.ZP.rotationDegrees(180.0F); // 修正倒立
        Quaternionf rotY = Axis.YP.rotationDegrees(rotation + 135.0f); // 加上旋转动画
        rotZ.mul(rotY);
        poseStack.mulPose(rotZ);

        EntityRenderDispatcher entityrenderdispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        rotY.conjugate();
        entityrenderdispatcher.overrideCameraOrientation(rotY);
        entityrenderdispatcher.setRenderShadow(false);

        // 4. 渲染
        // 注意：火箭中心点可能在脚底，为了居中显示，可能需要微调 translateY
        // 这里假设火箭高 2 格，我们把原点下移一点让它居中
        RenderSystem.runAsFancy(() -> {
            entityrenderdispatcher.render(entity, 0.0D, -3.0D, 0.0D, 0.0F, 1.0F, poseStack, graphics.bufferSource(), 15728880);
        });

        graphics.flush();
        entityrenderdispatcher.setRenderShadow(true);
        poseStack.popPose();
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        // 点击非输入框区域聚焦的逻辑 (模仿 StationScreen)
        if (!nameBox.isFocused() && isHovering(nameBox, pMouseX, pMouseY)) {
            nameBox.setFocused(true);
            setFocused(nameBox);
            return true;
        }
        if (rocketNameBox.active && !rocketNameBox.isFocused() && isHovering(rocketNameBox, pMouseX, pMouseY)) {
            rocketNameBox.setFocused(true);
            setFocused(rocketNameBox);
            return true;
        }
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    private boolean isHovering(EditBox box, double mouseX, double mouseY) {
        return mouseX >= box.getX() && mouseX < box.getX() + box.getWidth() &&
                mouseY >= box.getY() && mouseY < box.getY() + box.getHeight();
    }

    private void confirmAndClose() {
        // 发送站台名
        ModMessages.sendToServer(new PacketStationData(pos, nameBox.getValue(), false));

        // 发送火箭名
        if (rocketPresent && !rocketNameBox.getValue().isEmpty()) {
            ModMessages.sendToServer(new PacketStationData(pos, rocketNameBox.getValue(), true));
        }
        this.onClose();
    }

    @Override
    public void removed() {
        super.removed();
    }
}
