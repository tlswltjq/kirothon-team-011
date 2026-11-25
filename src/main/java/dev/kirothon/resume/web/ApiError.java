package dev.kirothon.resume.web;

import java.util.Map;
import java.util.TreeMap;

/** 모든 오류 응답: {code, message, fields}. */
public record ApiError(String code, String message, Map<String, String> fields) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of());
    }

    /** 필터처럼 MVC 밖에서 쓰는 JSON 문자열(값은 고정 문자열만 넣는다). */
    public static String json(String code, String message) {
        return "{\"code\":\"" + code + "\",\"message\":\"" + message.replace("\"", "\\\"") + "\",\"fields\":{}}";
    }

    public static Map<String, String> sorted(Map<String, String> fields) {
        return new TreeMap<>(fields);
    }
}
