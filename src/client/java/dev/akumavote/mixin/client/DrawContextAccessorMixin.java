package dev.akumavote.mixin.client;

import dev.akumavote.accessor.DrawContextAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DrawContext.class)
public interface DrawContextAccessorMixin extends DrawContextAccessor {
    @Override
    @Accessor("state")
    GuiRenderState akumavote$getState();
}
