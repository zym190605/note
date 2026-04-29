package com.cn.zym.note.util;

import org.jsoup.Jsoup;

public final class HtmlTexts {

    public static String plainPreview(String html, int max) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String plain = Jsoup.parse(html).text();
        if (plain.length() <= max) {
            return plain;
        }
        return plain.substring(0, max);
    }

    private HtmlTexts() {}
}
