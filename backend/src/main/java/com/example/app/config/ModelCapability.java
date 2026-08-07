package com.example.app.config;

public final class ModelCapability {

    // 输入能力
    public static final String TEXT_IN = "TEXT_IN";
    public static final String IMAGE_IN = "IMAGE_IN";
    public static final String AUDIO_IN = "AUDIO_IN";
    public static final String VIDEO_IN = "VIDEO_IN";

    // 输出能力
    public static final String TEXT_OUT = "TEXT_OUT";
    public static final String IMAGE_OUT = "IMAGE_OUT";
    public static final String AUDIO_OUT = "AUDIO_OUT";
    public static final String VIDEO_OUT = "VIDEO_OUT";

    // 旧能力名称兼容映射
    public static final String LEGACY_VISION = "VISION";
    public static final String LEGACY_IMAGE_GEN = "IMAGE_GEN";
    public static final String LEGACY_TTS = "TTS";
    public static final String LEGACY_VIDEO = "VIDEO";

    private ModelCapability() {
    }
}
