package dev.akumavote.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DrawContext.class)
interface DrawContextAccessorMixin {
    @Accessor("state")
    GuiRenderState akumavote$getState();
}