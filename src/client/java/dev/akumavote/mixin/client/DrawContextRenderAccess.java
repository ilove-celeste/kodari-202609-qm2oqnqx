package dev.akumavote.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class DrawContextRenderAccess {
    private DrawContextRenderAccess() {
    }

    public static void drawBrowserTexture(DrawContext context, GpuTextureView texture,
                                          int x1, int y1, int x2, int y2) {
        DrawContextAccessorMixin accessor = (DrawContextAccessorMixin) context;
        accessor.akumavote$getState().addSimpleElement(new TexturedQuadGuiElementRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureSetup.of(texture, RenderSystem.getSamplerCache().get(FilterMode.LINEAR)),
                new Matrix3x2f(),
                x1,
                y1,
                x2,
                y2,
                0.0F,
                1.0F,
                0.0F,
                1.0F,
                0xFFFFFFFF,
                null
        ));
    }
}