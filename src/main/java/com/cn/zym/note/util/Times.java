package com.cn.zym.note.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class Times {

    private static final ZoneId Z = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(Z);

    public static String iso(LocalDateTime t) {
        if (t == null) {
            return null;
        }
        return FMT.format(t.atZone(Z));
    }

    private Times() {}
}
