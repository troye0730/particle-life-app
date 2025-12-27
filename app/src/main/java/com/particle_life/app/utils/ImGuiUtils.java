package com.particle_life.app.utils;

import imgui.ImGui;
import imgui.flag.ImGuiSliderFlags;

public final class ImGuiUtils {

    /**
     * Helper to display a little (?) mark which shows a tooltip when hovered.
     *
     * @param text will be displayed as tooltip when hovered
     */
    public static void helpMarker(String text) {
        helpMarker(" ? ", text);
    }

    /**
     * Helper to display disabled text which shows a tooltip when hovered.
     *
     * @param label will always be displayed
     * @param text  will be displayed as tooltip when hovered
     */
    public static void helpMarker(String label, String text) {
        ImGui.sameLine();
        ImGui.textDisabled(label);
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.pushTextWrapPos(ImGui.getFontSize() * 35.0f);
            ImGui.textUnformatted(text);
            ImGui.popTextWrapPos();
            ImGui.endTooltip();
        }
    }

    public interface NumberInputCallback {
        void onValueChanged(float value);
    }

    public static void numberInput(String label,
                                   float min, float max,
                                   float value,
                                   String format,
                                   NumberInputCallback callback) {
        numberInput(label, min, max, value, format, callback, true);
    }

    public static void numberInput(String label,
                                   float min, float max,
                                   float value,
                                   String format,
                                   NumberInputCallback callback,
                                   boolean logarithmicScale) {
        float[] valueBuffer = new float[]{value};
        int imGuiSliderFlags = ImGuiSliderFlags.NoRoundToFormat;
        if (logarithmicScale) imGuiSliderFlags |= ImGuiSliderFlags.Logarithmic;
        if (ImGui.sliderFloat(label, valueBuffer, min, max, format, imGuiSliderFlags)) {
            if (ImGui.isMouseDragging(0, 0.0f)
                    || ImGui.isItemDeactivatedAfterEdit() /* check if user pressed enter after text-edit */) {
                callback.onValueChanged(valueBuffer[0]);
            }
        }
    }

    public static void separator() {
        ImGui.dummy(0, 2);
        ImGui.separator();
        ImGui.dummy(0, 2);
    }
}
