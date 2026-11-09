package com.dji.sample.common.util;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CommonUtil {
    public static List<Double> strToDoubleListWithCommons(String str) {
        // StringUtils.split 自动处理 null/空字符串，返回空数组
        String[] strArray = StringUtils.split(str, ",");
        return Arrays.stream(strArray)
                .map(String::trim)
                .map(Double::valueOf)
                .collect(Collectors.toList());
    }

    /**
     * 用 b 的非 null 字段覆盖更新 a（浅拷贝，支持基础字段）
     * @param a 目标对象（被更新）
     * @param b 源对象（提供非 null 值）
     * @param <T> 类泛型（a 和 b 必须是同一个类）
     * @throws IllegalAccessException 反射访问字段异常
     */
    public static <T> void mergeNonBlank(T a, T b) throws IllegalAccessException {
        // 校验参数非 null
        if (a == null || b == null) {
            throw new IllegalArgumentException("a 和 b 不能为 null");
        }
        // 校验 a 和 b 是同一个类（或子类）
        if (!a.getClass().isAssignableFrom(b.getClass())) {
            throw new IllegalArgumentException("a 和 b 必须是同一个类");
        }

        // 获取类的所有字段（包括父类字段，排除 static/final 字段）
        List<Field> fields = getAllFields(a.getClass());

        for (Field field : fields) {
            // 设置字段可访问（突破 private 限制）
            field.setAccessible(true);

            // 获取 b 的字段值
            Object bValue = field.get(b);

            // 仅当 b 的字段值非 null 时，覆盖到 a
            if (bValue != null) {
                field.set(a, bValue);
            }
        }
    }

    /**
     * 递归获取类的所有字段（包括父类）
     */
    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = Arrays.asList(clazz.getDeclaredFields());
        Class<?> superClass = clazz.getSuperclass();

        // 递归获取父类字段（直到 Object 类）
        if (superClass != null && superClass != Object.class) {
            fields.addAll(getAllFields(superClass));
        }
        return fields;
    }

    // 密码强度校验工具方法
    public static boolean isPasswordStrong(String password) {
        // 正则：长度>=8，包含大小写字母、数字、特殊字符
        String regex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";
        return password.matches(regex);
    }

    public static Long[] dealStartEndTime(Long startTime, Long endTime) {
        Instant now = Instant.now();
        if (Objects.isNull(endTime)) {
            endTime = now.toEpochMilli();
        }
        if (Objects.isNull(startTime)) {
            startTime = endTime - 31536000000L;
        }
        return new Long[]{startTime, endTime};
    }

    public static String randomString(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SecureRandom RANDOM = new SecureRandom();
        RANDOM.nextBytes(bytes);
        // 去掉=号，方便放到 header 里
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String getFileNamePrefix(String fileName) {
        // 1. 校验参数
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        // 2. 找到最后一个 . 的位置
        int lastDotIndex = fileName.lastIndexOf(".");
        // 3. 判断是否以 .zip 结尾（不区分大小写，兼容 .ZIP/.Zip）
        if (lastDotIndex != -1 && fileName.substring(lastDotIndex).equalsIgnoreCase(".zip")) {
            return fileName.substring(0, lastDotIndex);
        }
        // 若不是 .zip 后缀，返回原文件名（或根据需求抛异常）
        return fileName;
    }

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RAND = new SecureRandom();

    /**
     * 生成指定长度密码
     */
    public static String randomPwdGenerate(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CHARS.charAt(RAND.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
