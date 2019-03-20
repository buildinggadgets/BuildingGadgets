/**
 * This class was adapted from code written by Vazkii for the PSI mod: https://github.com/Vazkii/Psi
 * Psi is Open Source and distributed under the
 * Psi License: http://psi.vazkii.us/license.php
 */

package com.direwolf20.buildinggadgets.client.gui;

import com.direwolf20.buildinggadgets.client.KeyBindings;
import com.direwolf20.buildinggadgets.common.config.Config;
import com.direwolf20.buildinggadgets.common.items.gadgets.*;
import com.direwolf20.buildinggadgets.common.network.PacketHandler;
import com.direwolf20.buildinggadgets.common.network.packets.*;
import com.direwolf20.buildinggadgets.common.registry.objects.BGSound;
import com.direwolf20.buildinggadgets.common.utils.GadgetUtils;
import com.direwolf20.buildinggadgets.common.utils.ref.Reference;
import com.google.common.collect.ImmutableSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.opengl.GL11;

import javax.vecmath.Vector2f;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ModeRadialMenu extends GuiScreen {

    private static final ResourceLocation[] signsBuilding = new ResourceLocation[]{
        new ResourceLocation(Reference.MODID, "textures/gui/mode/build_to_me.png"),
        new ResourceLocation(Reference.MODID, "textures/gui/mode/vertical_column.png"),
        new ResourceLocation(Reference.MODID, "textures/gui/mode/horizontal_column.png"),
        new ResourceLocation(Reference.MODID, "textures/gui/mode/vertical_wall.png"),
        new ResourceLocation(Reference.MODID, "textures/gui/mode/horizontal_wall.png"),
        new ResourceLocation(Reference.MODID, "textures/gui/mode/stairs.png"),
        new ResourceLocation(Reference.MODID, "textures/gui/mode/grid.png"),
        new ResourceLocation(Reference.MODID, "textures/gui/mode/surface.png")
    };
    private static final ResourceLocation[] signsExchanger = new ResourceLocation[]{
        new ResourceLocation(Reference.MODID, "textures/gui/mode/surface.png"),
        new ResourceLocation(Reference.MODID, "textures/gui/mode/vertical_column.png"),
        new ResourceLocation(Reference.MODID, "textures/gui/mode/horizontal_column.png"),
        new ResourceLocation(Reference.MODID, "textures/gui/mode/grid.png")
    };
    private static final ResourceLocation[] signsCopyPaste = new ResourceLocation[]{
        new ResourceLocation(Reference.MODID, "textures/gui/mode/copy.png"),
        new ResourceLocation(Reference.MODID, "textures/gui/mode/paste.png")
    };
    private int timeIn = 0;
    private int slotSelected = -1;
    private int segments;
    private GuiSliderInt sliderRange;
    private final List<GuiButton> conditionalButtons = new ArrayList<>();

    public ModeRadialMenu(ItemStack stack) {
        mc = Minecraft.getInstance();
        if (stack.getItem() instanceof GadgetGeneric)
            setSocketable(stack);
    }

    public void setSocketable(ItemStack stack) {
        if (stack.getItem() instanceof GadgetBuilding)
            segments = GadgetBuilding.ToolMode.values().length;
        else if (stack.getItem() instanceof GadgetExchanger)
            segments = GadgetExchanger.ToolMode.values().length;
        else if (stack.getItem() instanceof GadgetCopyPaste)
            segments = GadgetCopyPaste.ToolMode.values().length;
    }

    @Override
    public void initGui() {
        conditionalButtons.clear();
        ItemStack tool = getGadget();
        boolean isDestruction = tool.getItem() instanceof GadgetDestruction;
        ScreenPosition right = isDestruction ? ScreenPosition.TOP : ScreenPosition.RIGHT;
        ScreenPosition left = isDestruction ? ScreenPosition.BOTTOM : ScreenPosition.LEFT;
        if (isDestruction) {
            addButton(new GuiButtonActionCallback("destroy.overlay", right, send -> {
                if (send)
                    PacketHandler.sendToServer(new PacketChangeRange());

                return GadgetDestruction.getOverlay(getGadget());
            }));
        } else {
            addButton(new GuiButtonActionCallback("rotate", left, send -> {
                if (send)
                    PacketHandler.sendToServer(new PacketRotateMirror(PacketRotateMirror.Operation.ROTATE));

                return false;
            }).setTogglable(false));
            addButton(new GuiButtonActionCallback("mirror", left, send -> {
                if (send)
                    PacketHandler.sendToServer(new PacketRotateMirror(PacketRotateMirror.Operation.MIRROR));

                return false;
            }).setTogglable(false));
        }
        if (!(tool.getItem() instanceof GadgetCopyPaste)) {
            if (!isDestruction || Config.GADGETS.GADGET_DESTRUCTION.nonFuzzyEnabled.get()) {
                GuiButton button = new GuiButtonActionCallback("fuzzy", right, send -> {
                    if (send)
                        PacketHandler.sendToServer(new PacketToggleFuzzy());

                    return GadgetGeneric.getFuzzy(getGadget());
                });
                addButton(button);
                conditionalButtons.add(button);
            }
            GuiButton button = new GuiButtonActionCallback("connected_" + (isDestruction ? "area" : "surface"), right, send -> {
                if (send)
                    PacketHandler.sendToServer(new PacketToggleConnectedArea());

                return GadgetGeneric.getConnectedArea(getGadget());
            });
            addButton(button);
            conditionalButtons.add(button);
            if (!isDestruction) {
                int widthSlider = 82;
                sliderRange = new GuiSliderInt(width / 2 - widthSlider / 2, height / 2 + 72, widthSlider, 14, "Range ", "", 1, Config.GADGETS.maxRange.get(),
                    GadgetUtils.getToolRange(tool), false, true, Color.DARK_GRAY, slider -> {
                        if (slider.getValueInt() != GadgetUtils.getToolRange(getGadget()))
                            PacketHandler.sendToServer(new PacketChangeRange(slider.getValueInt()));
                    }, (slider, amount) -> {
                        int value = slider.getValueInt();
                        int valueNew = MathHelper.clamp(value + amount, 1, Config.GADGETS.maxRange.get());
                        slider.setValue(valueNew);
                        slider.updateSlider();
                    });
                sliderRange.precision = 1;
                sliderRange.getComponents().forEach(component -> addButton(component));
            }
        }
        addButton(new GuiButtonActionCallback("raytrace_fluid", right, send -> {
            if (send)
                PacketHandler.sendToServer(new PacketToggleRayTraceFluid());

            return GadgetGeneric.shouldRayTraceFluid(getGadget());
        }));
        if (tool.getItem() instanceof GadgetBuilding) {
            addButton(new GuiButtonActionCallback("building.place_atop", right, send -> {
                if (send)
                    PacketHandler.sendToServer(new PacketToggleBlockPlacement());

                return GadgetBuilding.shouldPlaceAtop(getGadget());
            }));
        }
        addButton(new GuiButtonActionCallback("anchor", left, send -> {
            if (send)
                PacketHandler.sendToServer(new PacketAnchor());

            ItemStack stack = getGadget();
            if (stack.getItem() instanceof GadgetCopyPaste)
                return GadgetCopyPaste.getAnchor(stack) != null;
            else if (stack.getItem() instanceof GadgetDestruction)
                return GadgetDestruction.getAnchor(stack) != null;

            return !GadgetUtils.getAnchor(stack).isEmpty();
        }));
        if (!(tool.getItem() instanceof GadgetExchanger)) {
            addButton(new GuiButtonActionCallback("undo", left, send -> {
                if (send)
                    PacketHandler.sendToServer(new PacketUndo());

                return false;
            }).setTogglable(false));
        }
        updateButtons(tool);
    }

    private void updateButtons(ItemStack tool) {
        int posRight = 0;
        int posLeft = 0;
        int dim = 24;
        int padding = 10;
        boolean isDestruction = tool.getItem() instanceof GadgetDestruction;
        ScreenPosition right = isDestruction ? ScreenPosition.BOTTOM : ScreenPosition.RIGHT;
        for (int i = 0; i < buttons.size(); i++) {
            if (!(buttons.get(i) instanceof GuiButtonActionCallback))
                continue;

            GuiButtonActionCallback button = (GuiButtonActionCallback) buttons.get(i);
            SoundEvent sound = BGSound.BEEP.getSound();
            button.setSounds(sound, sound, 1F, 0.6F);
            if (!button.visible) continue;
            int offset;
            boolean isRight = button.getScreenPosition() == right;
            if (isRight) {
                posRight += dim + padding;
                offset = 70;
            } else {
                posLeft += dim + padding;
                offset = -70 - dim;
            }
            button.width = dim;
            button.height = dim;
            if (isDestruction)
                button.y = height / 2 + (isRight ? 10 : -button.height - 10);
            else
                button.x = width / 2 + offset;
        }
        posRight = resetPos(tool, padding, posRight);
        posLeft = resetPos(tool, padding, posLeft);
        for (int i = 0; i < buttons.size(); i++) {
            if (!(buttons.get(i) instanceof GuiButtonActionCallback))
                continue;

            GuiButtonActionCallback button = (GuiButtonActionCallback) buttons.get(i);
            if (!button.visible) continue;
            boolean isRight = button.getScreenPosition() == right;
            int pos = isRight ? posRight : posLeft;
            if (isDestruction)
                button.x = pos;
            else
                button.y = pos;

            if (isRight)
                posRight += dim + padding;
            else
                posLeft += dim + padding;
        }
    }

    private int resetPos(ItemStack tool, int padding, int pos) {
        return tool.getItem() instanceof GadgetDestruction ? width / 2 - (pos - padding) / 2 : height / 2 - (pos - padding) / 2;
    }

    private ItemStack getGadget() {
        return GadgetGeneric.getGadget(Minecraft.getInstance().player);
    }

    @Override
    public void render(int mx, int my, float partialTicks) {
        float stime = 5F;
        float fract = Math.min(stime, timeIn + partialTicks) / stime;
        int x = width / 2;
        int y = height / 2;

        int radiusMin = 26;
        int radiusMax = 60;
        double dist = new Vec3d(x, y, 0).distanceTo(new Vec3d(mx, my, 0));
        boolean inRange = false;
        if (segments != 0) {
            inRange = dist > radiusMin && dist < radiusMax;
            for (GuiButton button : buttons) {
                if (button instanceof GuiButtonActionCallback)
                    ((GuiButtonActionCallback) button).setFaded(inRange);
            }
        }
        GlStateManager.pushMatrix();
        GlStateManager.translatef((1 - fract) * x, (1 - fract) * y, 0);
        GlStateManager.scalef(fract, fract, fract);
        super.render(mx, my, partialTicks);
        GlStateManager.popMatrix();
        if (segments == 0) {
            renderHoverHelpText(mx, my);
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();

        float angle = mouseAngle(x, y, mx, my);

        int highlight = 5;

        GlStateManager.enableBlend();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        float totalDeg = 0;
        float degPer = 360F / segments;

        List<int[]> stringPositions = new ArrayList<>();

        ItemStack tool = getGadget();
        if (tool.isEmpty())
            return;

        slotSelected = -1;

        ResourceLocation[] signs;
        int modeIndex;
        if (tool.getItem() instanceof GadgetBuilding) {
            modeIndex = GadgetBuilding.getToolMode(tool).ordinal();
            signs = signsBuilding;
        } else if (tool.getItem() instanceof GadgetExchanger) {
            modeIndex = GadgetExchanger.getToolMode(tool).ordinal();
            signs = signsExchanger;
        } else {
            modeIndex = GadgetCopyPaste.getToolMode(tool).ordinal();
            signs = signsCopyPaste;
        }

        boolean shouldCenter = (segments + 2) % 4 == 0;
        int indexBottom = segments / 4;
        int indexTop = indexBottom + segments / 2;
        for (int seg = 0; seg < segments; seg++) {
            boolean mouseInSector = isCursorInSlice(angle, totalDeg, degPer, inRange);
            float radius = Math.max(0F, Math.min((timeIn + partialTicks - seg * 6F / segments) * 40F, radiusMax));

            GL11.glBegin(GL11.GL_TRIANGLE_STRIP);

            float gs = 0.25F;
            if (seg % 2 == 0)
                gs += 0.1F;
            float r = gs;
            float g = gs + (seg == modeIndex ? 1F : 0.0F);
            float b = gs;
            float a = 0.4F;
            if (mouseInSector) {
                slotSelected = seg;
                r = g = b = 1F;
            }

            GlStateManager.color4f(r, g, b, a);

            for (float i = degPer; i >= 0; i--) {
                float rad = (float) ((i + totalDeg) / 180F * Math.PI);
                double xp = x + Math.cos(rad) * radius;
                double yp = y + Math.sin(rad) * radius;
                if ((int) i == (int) (degPer / 2))
                    stringPositions.add(new int[]{(int) xp, (int) yp, mouseInSector ? 1 : 0, shouldCenter && (seg == indexBottom || seg == indexTop) ? 1 : 0});

                GL11.glVertex2d(x + Math.cos(rad) * radius / 2.3F, y + Math.sin(rad) * radius / 2.3F);
                GL11.glVertex2d(xp, yp);
            }
            totalDeg += degPer;

            GL11.glEnd();
            GlStateManager.color4f(1, 1, 1, 1);

            if (mouseInSector)
                radius -= highlight;
        }
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();

        for (int i = 0; i < stringPositions.size(); i++) {
            int[] pos = stringPositions.get(i);
            int xp = pos[0];
            int yp = pos[1];

            String name = "";
            if (tool.getItem() instanceof GadgetBuilding)
                name = GadgetBuilding.ToolMode.values()[i].toString();
            else if (tool.getItem() instanceof GadgetExchanger)
                name = GadgetExchanger.ToolMode.values()[i].toString();
            else
                name = GadgetCopyPaste.ToolMode.values()[i].toString();

            int xsp = xp - 4;
            int ysp = yp;
            int width = fontRenderer.getStringWidth(name);

            if (xsp < x)
                xsp -= width - 8;
            if (ysp < y)
                ysp -= 9;

            Color color = i == modeIndex ? Color.GREEN : Color.WHITE;
            if (pos[2] > 0)
                fontRenderer.drawStringWithShadow(name, xsp + (pos[3] > 0 ? width / 2 - 4 : 0), ysp, color.getRGB());

            double mod = 0.7;
            int xdp = (int) ((xp - x) * mod + x);
            int ydp = (int) ((yp - y) * mod + y);

            GlStateManager.color4f(color.getRed() / 255, color.getGreen() / 255, color.getBlue() / 255F, 1);
            mc.getTextureManager().bindTexture(signs[i]);
            drawModalRectWithCustomSizedTexture(xdp - 8, ydp - 8, 0, 0, 16, 16, 16, 16);
        }

        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        RenderHelper.enableGUIStandardItemLighting();

        float s = 2.25F * fract;
        GlStateManager.scalef(s, s, s);
        GlStateManager.translatef(x / s - (tool.getItem() instanceof GadgetCopyPaste ? 8F : 8.5F), y / s - 8, 0);
        mc.getItemRenderer().renderItemAndEffectIntoGUI(tool, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();

        GlStateManager.popMatrix();
        renderHoverHelpText(mx, my);
    }

    private boolean isCursorInSlice(float angle, float totalDeg, float degPer, boolean inRange) {
        return inRange && angle > totalDeg && angle < totalDeg + degPer;
    }

    private void renderHoverHelpText(int mx, int my) {
        buttons.forEach(button -> {
            if (!(button instanceof GuiButtonActionCallback))
                return;

            GuiButtonActionCallback helpTextProvider = (GuiButtonActionCallback) button;
            if (helpTextProvider.isHovered(mx, my)) {
                Color color = button instanceof GuiButtonSelect && ((GuiButtonSelect) button).isSelected() ? Color.GREEN : Color.WHITE;
                String text = helpTextProvider.getHoverHelpText();
                int x = helpTextProvider.getScreenPosition() == ScreenPosition.LEFT ? mx - fontRenderer.getStringWidth(text): mx;
                fontRenderer.drawStringWithShadow(text, x, my - fontRenderer.FONT_HEIGHT, color.getRGB());
            }
        });
    }

    private void changeMode() {
        if (slotSelected >= 0) {
            PacketHandler.sendToServer(new PacketToggleMode(slotSelected));
            BGSound.BEEP.playSound();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        changeMode();
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void tick() {
//    TODO 1.13 only works for bound keys; not bound mouse buttons
        if (!InputMappings.isKeyDown(KeyBindings.menuSettings.getKey().getKeyCode())) {
            close();
            changeMode();
        }

        ImmutableSet<KeyBinding> set = ImmutableSet.of(mc.gameSettings.keyBindForward, mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindBack, mc.gameSettings.keyBindRight, mc.gameSettings.keyBindSneak, mc.gameSettings.keyBindSprint, mc.gameSettings.keyBindJump);
        for (KeyBinding k : set)
            KeyBinding.setKeyBindState(k.getKey(), k.isKeyDown());

        timeIn++;
        ItemStack tool = getGadget();
        boolean builder = tool.getItem() instanceof GadgetBuilding;
        if (!builder && !(tool.getItem() instanceof GadgetExchanger))
            return;

        boolean curent;
        boolean changed = false;
        for (int i = 0; i < conditionalButtons.size(); i++) {
            GuiButton button = conditionalButtons.get(i);
            if (builder)
                curent = GadgetBuilding.getToolMode(tool) == GadgetBuilding.ToolMode.Surface;
            else
                curent = i == 0 || GadgetExchanger.getToolMode(tool) == GadgetExchanger.ToolMode.Surface;

            if (button.visible != curent) {
                button.visible = curent;
                changed = true;
            }
        }
        if (changed)
            updateButtons(tool);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static float mouseAngle(int x, int y, int mx, int my) {
        Vector2f baseVec = new Vector2f(1F, 0F);
        Vector2f mouseVec = new Vector2f(mx - x, my - y);

        float ang = (float) (Math.acos(baseVec.dot(mouseVec) / (baseVec.length() * mouseVec.length())) * (180F / Math.PI));
        return my < y ? 360F - ang : ang;
    }

    public static enum ScreenPosition {
        RIGHT, LEFT, BOTTOM, TOP;
    }
}
