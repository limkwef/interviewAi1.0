package org.backend.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 岗位常量统一管理
 *
 * 所有岗位相关的映射、名称转换统一在此处维护，
 * 新增岗位只需修改本类，无需改动其他 Service。
 */
public final class PositionConstants {

    private PositionConstants() {}

    /** 英文 code → 中文展示名（简短，用于仪表盘等场景） */
    private static final Map<String, String> CODE_TO_SHORT_NAME = new LinkedHashMap<>();
    static {
        CODE_TO_SHORT_NAME.put("java_backend", "Java后端");
        CODE_TO_SHORT_NAME.put("frontend", "前端开发");
        CODE_TO_SHORT_NAME.put("fullstack", "全栈开发");
        CODE_TO_SHORT_NAME.put("algorithm", "算法工程师");
        CODE_TO_SHORT_NAME.put("hr", "HR/软素质");
    }

    /** 英文 code → 中文全称（用于 AI prompt 等需要完整表述的场景） */
    private static final Map<String, String> CODE_TO_FULL_NAME = new LinkedHashMap<>();
    static {
        CODE_TO_FULL_NAME.put("java_backend", "Java后端开发");
        CODE_TO_FULL_NAME.put("frontend", "前端开发");
        CODE_TO_FULL_NAME.put("fullstack", "全栈开发");
        CODE_TO_FULL_NAME.put("algorithm", "算法工程师");
        CODE_TO_FULL_NAME.put("hr", "HR/软素质");
    }

    /** 旧版中文岗位名 → 英文 code（兼容历史数据） */
    private static final Map<String, String> LEGACY_TO_CODE = new LinkedHashMap<>();
    static {
        LEGACY_TO_CODE.put("Java后端开发", "java_backend");
        LEGACY_TO_CODE.put("Java后端", "java_backend");
        LEGACY_TO_CODE.put("前端开发", "frontend");
        LEGACY_TO_CODE.put("全栈开发", "fullstack");
        LEGACY_TO_CODE.put("数据分析", "algorithm");
        LEGACY_TO_CODE.put("算法工程师", "algorithm");
    }

    /**
     * 获取所有岗位 code → 简短中文名的映射（不可修改）
     */
    public static Map<String, String> allShortNames() {
        return Collections.unmodifiableMap(CODE_TO_SHORT_NAME);
    }

    /**
     * 英文 code → 简短中文名（如 "java_backend" → "Java后端"）
     * 找不到时返回 defaultName
     */
    public static String getShortName(String code, String defaultName) {
        return CODE_TO_SHORT_NAME.getOrDefault(code, defaultName);
    }

    /**
     * 英文 code → 简短中文名，找不到返回 "技术面试"
     */
    public static String getShortName(String code) {
        return getShortName(code, "技术面试");
    }

    /**
     * 英文 code → 中文全称（如 "java_backend" → "Java后端开发"）
     * 找不到时返回 defaultName
     */
    public static String getFullName(String code, String defaultName) {
        return CODE_TO_FULL_NAME.getOrDefault(code, defaultName);
    }

    /**
     * 英文 code → 中文全称，找不到返回 "技术"
     */
    public static String getFullName(String code) {
        return getFullName(code, "技术");
    }

    /**
     * 将岗位值统一为英文 code
     * 如果已经是英文 code 直接返回；如果是旧中文值，转换后返回
     */
    public static String normalize(String value) {
        if (value == null || value.isEmpty()) return "";
        if (CODE_TO_SHORT_NAME.containsKey(value)) return value;
        return LEGACY_TO_CODE.getOrDefault(value, value);
    }
}
