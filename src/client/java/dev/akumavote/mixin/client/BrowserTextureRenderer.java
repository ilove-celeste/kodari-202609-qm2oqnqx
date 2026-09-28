package dev.akumavote.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class BrowserTextureRenderer {
    private BrowserTextureRenderer() {
    }

    public static void draw(DrawContext context, GpuTextureView texture, int x, int y, int width, int height) {
        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        DrawContextAccessorMixin accessor = (DrawContextAccessorMixin) (Object) context;
        accessor.akumavote$getState().addSimpleElement(new TexturedQuadGuiElementRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureSetup.of(texture, sampler),
                new Matrix3x2f(context.getMatrices()),
                x,
                y,
                x + width,
                y + height,
                0.0F,
                1.0F,
                0.0F,
                1.0F,
                0xFFFFFFFF,
                null
        ));
    }
}