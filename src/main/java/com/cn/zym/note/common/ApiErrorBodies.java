package com.cn.zym.note.common;

public final class ApiErrorBodies {

    public record Problem(String code, String message, Object details) {}

    private ApiErrorBodies() {}
}
