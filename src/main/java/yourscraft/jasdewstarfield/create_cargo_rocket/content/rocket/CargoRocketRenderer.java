package yourscraft.jasdewstarfield.create_cargo_rocket.content.rocket;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;

public class CargoRocketRenderer extends EntityRenderer<CargoRocketEntity> {
    public CargoRocketRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull CargoRocketEntity entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);

        poseStack.pushPose();

        // 临时渲染：把火箭渲染成 2x6 的一堆铁块，方便调试位置
        // 向上移动，因为实体坐标通常在脚底
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));

        // 简单的方块渲染占位符
        poseStack.translate(-0.5, 0, -0.5); // 居中
        var blockRenderer = Minecraft.getInstance().getBlockRenderer();

        // 渲染一个两格高的方块塔作为“火箭”
        for (int i = 0; i < 6; i++) {
            blockRenderer.renderSingleBlock(Blocks.IRON_BLOCK.defaultBlockState(), poseStack, buffer, packedLight, 0);
            poseStack.translate(0, 1, 0);
        }

        poseStack.popPose();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull CargoRocketEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
