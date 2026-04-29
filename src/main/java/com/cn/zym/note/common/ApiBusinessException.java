package com.cn.zym.note.common;

import lombok.Getter;

@Getter
public class ApiBusinessException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public ApiBusinessException(int httpStatus, String code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }
}
