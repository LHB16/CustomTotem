package com.customtotem.model;

import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.item.ItemDisplayContext;
import org.joml.Vector3f;

public class TotemDisplaySettings {
    public enum Preset {
        VANILLA("Vanilla Default"),
        MINI_TOTEM("Mini Totem (Flat 2D)"),
        CUSTOM("Custom");

        private final String displayName;

        Preset(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private Preset preset = Preset.MINI_TOTEM;
    private boolean fullbright = true;

    // First person transforms (in model units, divided by 16 when applied)
    private float fpTranslationX = -1.5f;
    private float fpTranslationY = 4.5f;
    private float fpTranslationZ = 1.0f;
    private float fpScale = 0.45f;

    // Third person transforms
    private float tpTranslationX = 0.0f;
    private float tpTranslationY = 3.0f;
    private float tpTranslationZ = 1.0f;
    private float tpScale = 0.55f;

    public TotemDisplaySettings() {
    }

    public Preset getPreset() {
        return preset;
    }

    public void setPreset(Preset preset) {
        this.preset = preset;
        if (preset == Preset.MINI_TOTEM) {
            fpTranslationX = -1.5f;
            fpTranslationY = 4.5f;
            fpTranslationZ = 1.0f;
            fpScale = 0.45f;

            tpTranslationX = 0.0f;
            tpTranslationY = 3.0f;
            tpTranslationZ = 1.0f;
            tpScale = 0.55f;
        } else if (preset == Preset.VANILLA) {
            fpTranslationX = 1.13f;
            fpTranslationY = 3.2f;
            fpTranslationZ = 1.13f;
            fpScale = 0.68f;

            tpTranslationX = 0.0f;
            tpTranslationY = 3.0f;
            tpTranslationZ = 1.0f;
            tpScale = 0.55f;
        }
    }

    public boolean isFullbright() {
        return fullbright;
    }

    public void setFullbright(boolean fullbright) {
        this.fullbright = fullbright;
    }

    public float getFpTranslationX() {
        return fpTranslationX;
    }

    public void setFpTranslationX(float fpTranslationX) {
        this.fpTranslationX = fpTranslationX;
    }

    public float getFpTranslationY() {
        return fpTranslationY;
    }

    public void setFpTranslationY(float fpTranslationY) {
        this.fpTranslationY = fpTranslationY;
    }

    public float getFpTranslationZ() {
        return fpTranslationZ;
    }

    public void setFpTranslationZ(float fpTranslationZ) {
        this.fpTranslationZ = fpTranslationZ;
    }

    public float getFpScale() {
        return fpScale;
    }

    public void setFpScale(float fpScale) {
        this.fpScale = fpScale;
    }

    public float getTpTranslationX() {
        return tpTranslationX;
    }

    public void setTpTranslationX(float tpTranslationX) {
        this.tpTranslationX = tpTranslationX;
    }

    public float getTpTranslationY() {
        return tpTranslationY;
    }

    public void setTpTranslationY(float tpTranslationY) {
        this.tpTranslationY = tpTranslationY;
    }

    public float getTpTranslationZ() {
        return tpTranslationZ;
    }

    public void setTpTranslationZ(float tpTranslationZ) {
        this.tpTranslationZ = tpTranslationZ;
    }

    public float getTpScale() {
        return tpScale;
    }

    public void setTpScale(float tpScale) {
        this.tpScale = tpScale;
    }

    public Transformation getTransformation(ItemDisplayContext context) {
        if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            return new Transformation(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Vector3f(fpTranslationX * 0.0625f, fpTranslationY * 0.0625f, fpTranslationZ * 0.0625f),
                    new Vector3f(fpScale, fpScale, fpScale)
            );
        } else if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            return new Transformation(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Vector3f(tpTranslationX * 0.0625f, tpTranslationY * 0.0625f, tpTranslationZ * 0.0625f),
                    new Vector3f(tpScale, tpScale, tpScale)
            );
        } else if (context == ItemDisplayContext.GUI) {
            return new Transformation(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Vector3f(1.0f, 1.0f, 1.0f)
            );
        } else if (context == ItemDisplayContext.GROUND) {
            return new Transformation(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Vector3f(0.0f, 2.0f * 0.0625f, 0.0f),
                    new Vector3f(0.5f, 0.5f, 0.5f)
            );
        } else if (context == ItemDisplayContext.FIXED) {
            // Used for floating item pop
            return new Transformation(
                    new Vector3f(0.0f, 180.0f, 0.0f),
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Vector3f(1.0f, 1.0f, 1.0f)
            );
        }
        return Transformation.IDENTITY;
    }
}
