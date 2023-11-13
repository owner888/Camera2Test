/*
 * Copyright (C) 2018 CyberAgent, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.cyberagent.android.gpuimage.filter;

import android.graphics.Color;
import android.opengl.GLES20;

/**
 * Adjusts the shadowTintColor and highlightTintColor of an image
 * shadowTintIntensity: Increase to lighten shadowTintIntensity, from 0.0 to 1.0, with 0.0 as the default.
 * highlightTintIntensity: Decrease to darken highlightTintIntensity, from 0.0 to 1.0, with 1.0 as the default.
 * shadowTintColor: Set shadow tint color, with black as the default.
 * highlightTintColor: Set highlight tint color, with black as the default.
 */
public class GPUImageHighlightShadowTintFilter extends GPUImageFilter {
    public static final String HIGHLIGHT_SHADOW_TINT_FRAGMENT_SHADER = "" +
            " precision lowp float;\n" +
            " \n" +
            " varying highp vec2 textureCoordinate;\n" +
            " \n" +
            " uniform sampler2D inputImageTexture;\n" +
            " uniform lowp float shadowTintIntensity;\n" +
            " uniform lowp float highlightTintIntensity;\n" +
            " uniform highp vec3 shadowTintColor;\n" +
            " uniform highp vec3 highlightTintColor;\n" +
            " \n" +
            " const mediump vec3 luminanceWeighting = vec3(0.2125, 0.7154, 0.0721);\n" +
            " \n" +
            " void main()\n" +
            " {\n" +
            "    lowp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "    highp float luminance = dot(textureColor.rgb, luminanceWeighting);\n" +
            "     \n" +
            "    highp vec4 shadowResult = mix(textureColor, max(textureColor, vec4( mix(shadowTintColor, textureColor.rgb, luminance), textureColor.a)), shadowTintIntensity);\n" +
            "    highp vec4 highlightResult = mix(textureColor, min(shadowResult, vec4( mix(shadowResult.rgb, highlightTintColor, luminance), textureColor.a)), highlightTintIntensity);\n" +
            " \n" +
            "    gl_FragColor = vec4( mix(shadowResult.rgb, highlightResult.rgb, luminance), textureColor.a);\n" +
            " }";

    private int shadowTintIntensityLocation;
    private float shadowTintIntensity;
    private int highlightTintIntensityLocation;
    private float highlightTintIntensity;
    private int shadowTintColorLocation;
    private int shadowTintColor;
    private int highlightTintColorLocation;
    private int highlightTintColor;

    public GPUImageHighlightShadowTintFilter() {
        this(0.0f, 1.0f, Color.BLACK, Color.BLACK);
    }

    public GPUImageHighlightShadowTintFilter(final float shadowTintIntensity, final float highlightTintIntensity, final int shadowTintColor, final int highlightTintColor) {
        super(NO_FILTER_VERTEX_SHADER, HIGHLIGHT_SHADOW_TINT_FRAGMENT_SHADER);
        this.highlightTintIntensity = highlightTintIntensity;
        this.shadowTintIntensity = shadowTintIntensity;
        this.highlightTintColor = highlightTintColor;
        this.shadowTintColor = shadowTintColor;
    }

    @Override
    public void onInit() {
        super.onInit();
        highlightTintIntensityLocation = GLES20.glGetUniformLocation(getProgram(), "highlightTintIntensity");
        shadowTintIntensityLocation = GLES20.glGetUniformLocation(getProgram(), "shadowTintIntensity");
        highlightTintColorLocation = GLES20.glGetUniformLocation(getProgram(), "highlightTintColor");
        shadowTintColorLocation = GLES20.glGetUniformLocation(getProgram(), "shadowTintColor");
    }

    @Override
    public void onInitialized() {
        super.onInitialized();
        setHighlights(highlightTintIntensity);
        setShadows(shadowTintIntensity);
        setHighlightTintColor(highlightTintColor);
        setShadowTintColor(shadowTintColor);
    }

    public void setHighlights(final float highlightTintIntensity) {
        this.highlightTintIntensity = highlightTintIntensity;
        setFloat(highlightTintIntensityLocation, this.highlightTintIntensity);
    }

    public void setShadows(final float shadowTintIntensity) {
        this.shadowTintIntensity = shadowTintIntensity;
        setFloat(shadowTintIntensityLocation, this.shadowTintIntensity);
    }

    public void setHighlightTintColor(final int highlightTintColor) {
        this.highlightTintColor = highlightTintColor;
        setFloatVec3(highlightTintColorLocation, getColor(highlightTintColor));
    }

    public void setShadowTintColor(final int shadowTintColor) {
        this.shadowTintColor = shadowTintColor;
        setFloatVec3(shadowTintColorLocation, getColor(shadowTintColor));
    }

    private float[] getColor(int color) {
        return new float[]{Color.red(color), Color.green(color), Color.blue(color)};
    }
}
