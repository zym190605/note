package com.cn.zym.note.service;

import com.cn.zym.note.common.ApiBusinessException;
import com.cn.zym.note.config.NoteExportProps;
import com.cn.zym.note.entity.NoteEntity;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NoteExportService {

    private static final Logger LOG = LoggerFactory.getLogger(NoteExportService.class);

    private static final Pattern SAFE_FILE = Pattern.compile("[\\\\/:*?\"<>|\\r\\n]");

    /** Markdown ATX 标题行：# ～ ###### 后接非空白 */
    private static final Pattern MD_ATX_LINE = Pattern.compile("^#{1,6}\\s+\\S.*");

    /** key：不含 &amp; ; 的实体名，例如 nbsp、middot */
    private record Replacement(String key, int cp) {}

    /**
     * HTML 命名实体 → XML 十进制数值引用（OpenHTMLToPDF / SAX 能解析）。可按需增补条目。
     */
    private static final Replacement[] ENTITY_NUMERIC_XML = {
        new Replacement("nbsp", 160),
        new Replacement("middot", 183),
        new Replacement("ndash", 8211),
        new Replacement("mdash", 8212),
        new Replacement("bull", 8226),
        new Replacement("hellip", 8230),
        new Replacement("copy", 169),
        new Replacement("reg", 174),
        new Replacement("trade", 8482),
        new Replacement("deg", 176),
        new Replacement("times", 215),
        new Replacement("divide", 247),
        new Replacement("permil", 8240),
        new Replacement("frac12", 189),
        new Replacement("laquo", 171),
        new Replacement("raquo", 187),
        new Replacement("lsquo", 8216),
        new Replacement("rsquo", 8217),
        new Replacement("ldquo", 8220),
        new Replacement("rdquo", 8221),
        new Replacement("eur", 8364),
        new Replacement("pound", 163),
        new Replacement("yen", 165),
        new Replacement("cent", 162),
        new Replacement("para", 182),
        new Replacement("sect", 167),
        new Replacement("micro", 181),
        new Replacement("minus", 8722),
    };

    private final NoteExportProps exportProps;

    public NoteExportService(NoteExportProps exportProps) {
        this.exportProps = exportProps;
    }

    public byte[] pdf(NoteEntity note) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFastMode();
            OptionalFont of = optionalFont();
            if (of.fontFile() != null) {
                b.useFont(of.fontFile(), "NoteFont");
            }
            String fontFam = of.fontFamilyCss();
            b.withHtmlContent(wrapPdfHtml(note, fontFam), null);
            b.toStream(os);
            b.run();
            return os.toByteArray();
        } catch (ApiBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiBusinessException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "PDF_EXPORT", "PDF 导出失败：" + e.getMessage());
        }
    }

    private OptionalFont optionalFont() {
        try {
            Path explicit = resolveConfiguredPdfFontPath();
            if (explicit != null) {
                return new OptionalFont(explicit.toFile(), "'NoteFont', sans-serif");
            }
            Path auto = autoDetectSystemCjkFont();
            if (auto != null) {
                LOG.info("PDF 导出未配置 note.export.pdf-font-path，已自动使用中文字体：{}", auto.toAbsolutePath());
                return new OptionalFont(auto.toFile(), "'NoteFont', sans-serif");
            }
            LOG.warn(
                    "PDF 导出未找到中文字体文件，中文等内容可能显示为 #；请配置 note.export.pdf-font-path 或环境变量 NOTE_PDF_FONT（指向 .ttf / .ttc / .otf）");
        } catch (Exception e) {
            LOG.warn("PDF 字体解析异常：{}", e.toString());
        }
        return new OptionalFont(null, "sans-serif");
    }

    /** 显式配置：application.yml の note.export.pdf-font-path 或 NOTE_PDF_FONT */
    private Path resolveConfiguredPdfFontPath() {
        try {
            String fp = exportProps.pdfFontPath();
            if (fp == null || fp.isBlank()) {
                return null;
            }
            Path path = Path.of(fp.trim());
            return Files.isRegularFile(path) ? path : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Windows/macOS/Linux 常见系统字体路径探测（优先级从高到低）。
     * OpenHTMLToPDF 未嵌入含 CJK 的字形时常将缺字渲染为 「#」，与「Markdown」无关。
     */
    private static Path autoDetectSystemCjkFont() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                String windir = System.getenv("WINDIR");
                if (windir == null || windir.isBlank()) {
                    windir = "C:\\Windows";
                }
                Path fonts = Path.of(windir).resolve("Fonts");
                String[] names = {"msyh.ttc", "msyhbd.ttc", "msyhl.ttc", "simhei.ttf", "simsun.ttc", "simsunb.ttf", "Deng.ttf", "msjh.ttc",
                        "Dengb.ttf"};
                return firstExisting(fonts, names);
            }
            if (os.contains("mac")) {
                Path[] abs = {
                    Path.of("/System/Library/Fonts/PingFang.ttc"),
                    Path.of("/System/Library/Fonts/STHeiti Light.ttc"),
                    Path.of("/Library/Fonts/Arial Unicode.ttf")
                };
                for (Path p : abs) {
                    if (Files.isRegularFile(p)) {
                        return p;
                    }
                }
                return null;
            }
            Path[] linux = {
                Path.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
                Path.of("/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttf"),
                Path.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Variable.otf"),
                Path.of("/usr/share/fonts/truetype/wqy/wqy-microhei.ttc"),
                Path.of("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc")
            };
            for (Path p : linux) {
                if (Files.isRegularFile(p)) {
                    return p;
                }
            }
        } catch (Exception ignored) {
            // ignored
        }
        return null;
    }

    private static Path firstExisting(Path fontsDir, String[] fileNames) {
        if (!Files.isDirectory(fontsDir)) {
            return null;
        }
        for (String name : fileNames) {
            Path candidate = fontsDir.resolve(name);
            try {
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // next
            }
        }
        return null;
    }

    private record OptionalFont(java.io.File fontFile, String fontFamilyCss) {}

    /**
     * Jsoup {@link Entities.EscapeMode#xhtml} 仍会写出 {@code &middot;}、{@code &nbsp;} 等<strong>命名</strong> HTML 实体，
     * OpenHTMLToPDF 按 XML SAX 解析时未声明则会失败。
     * {@link Entities.EscapeMode#base} 尽量直接输出 Unicode，另对整文档做{@link #sanitizeNamedEntitiesForXml(String)}兜底。
     */
    private static String toXmlCompatibleBodyHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = org.jsoup.Jsoup.parseBodyFragment(html);
        doc.outputSettings()
                .prettyPrint(false)
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.base);
        return doc.body().html();
    }

    /**
     * 将 HTML 命名实体改成十进制数值引用 {@code &#...;}（XML/SAX 永远可解析）。不触碰 {@code &amp;} {@code &lt;} 等五项内置实体。
     */
    private static String sanitizeNamedEntitiesForXml(String markup) {
        if (markup == null || markup.indexOf('&') < 0) {
            return markup != null ? markup : "";
        }
        String s = markup;
        for (Replacement r : ENTITY_NUMERIC_XML) {
            Matcher m =
                    Pattern.compile("&" + Pattern.quote(r.key()) + ";", Pattern.CASE_INSENSITIVE).matcher(s);
            // Matcher.replaceAll：替换串无 $ \\ 时需 quoteReplacement（此处为纯字面量）
            s = m.replaceAll(Matcher.quoteReplacement("&#" + r.cp() + ";"));
        }
        return s;
    }

    private static String wrapPdfHtml(NoteEntity note, String fontFamily) {
        String safeTitle = HtmlUtils.htmlEscape(note.getTitle() != null ? note.getTitle() : "");
        String prepared =
                prepareHtmlBodyForStructuredExport(note.getContentHtml() != null ? note.getContentHtml() : "");
        String body = toXmlCompatibleBodyHtml(prepared);
        String full =
                "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>body{font-family:"
                        + fontFamily
                        + ";margin:36px;line-height:1.6;color:#222} .content{font-size:12pt}a{color:#0366d6}</style></head><body>"
                        + "<h1 style=\"font-size:20pt\">"
                        + safeTitle
                        + "</h1><div class=\"content\">"
                        + body
                        + "</div></body></html>";
        return sanitizeNamedEntitiesForXml(full);
    }

    /**
     * 若正文里是「类似 Markdown」的写法（例如在 {@code &lt;p&gt;} 中写 {@code ## 标题}），直接当 HTML 渲染会只看到 # 符号。
     * 检测到 ATX 行后先把块拼成 Markdown，再用 Flexmark 转成语义化 HTML。
     */
    static String prepareHtmlBodyForStructuredExport(String contentHtml) {
        if (contentHtml == null || contentHtml.isBlank()) {
            return "";
        }
        Document probe = org.jsoup.Jsoup.parseBodyFragment(contentHtml);
        if (!hasAtxMarkdownInParagraphBlocks(probe)) {
            return contentHtml;
        }
        String markdown = flattenBodyToMarkdownSource(probe.body());
        boolean anyHeading = Arrays.stream(markdown.split("\\R"))
                .map(String::trim)
                .anyMatch(l -> MD_ATX_LINE.matcher(l).matches());
        if (!anyHeading || markdown.isBlank()) {
            return contentHtml;
        }
        try {
            MutableDataSet opts = new MutableDataSet();
            Parser parser = Parser.builder(opts).build();
            HtmlRenderer renderer = HtmlRenderer.builder(opts).build();
            Node ast = parser.parse(markdown);
            String out = renderer.render(ast);
            return out != null && !out.isBlank() ? out : contentHtml;
        } catch (Exception ignored) {
            return contentHtml;
        }
    }

    private static boolean hasAtxMarkdownInParagraphBlocks(Document doc) {
        return doc.select("p").stream().anyMatch(NoteExportService::paragraphLooksLikeMarkdownAtx);
    }

    private static boolean paragraphLooksLikeMarkdownAtx(Element p) {
        String wt = p.wholeText().replace('\u00A0', ' ');
        return Arrays.stream(wt.split("\\R"))
                .map(String::trim)
                .anyMatch(l -> MD_ATX_LINE.matcher(l).matches());
    }

    private static String flattenBodyToMarkdownSource(Element body) {
        StringBuilder sb = new StringBuilder();
        flattenBlocksToMarkdown(body, sb);
        return sb.toString().trim();
    }

    private static void flattenBlocksToMarkdown(Element scope, StringBuilder sb) {
        for (Element c : scope.children()) {
            String tag = c.tagName();
            if ("div".equals(tag) || "section".equals(tag) || "article".equals(tag)) {
                flattenBlocksToMarkdown(c, sb);
                continue;
            }
            switch (tag) {
                case "p" ->
                        sb.append(c.wholeText().replace('\u00A0', ' ').trim()).append("\n\n");
                case "blockquote" -> sb.append(c.wholeText().replace('\u00A0', ' ').trim()).append("\n\n");
                case "ul" -> {
                    for (Element li : c.children()) {
                        if ("li".equals(li.tagName())) {
                            sb.append("- ")
                                    .append(li.wholeText().replace('\u00A0', ' ').trim())
                                    .append('\n');
                        }
                    }
                    sb.append("\n");
                }
                case "ol" -> {
                    int n = 1;
                    for (Element li : c.children()) {
                        if ("li".equals(li.tagName())) {
                            sb.append(n++)
                                    .append(". ")
                                    .append(li.wholeText().replace('\u00A0', ' ').trim())
                                    .append('\n');
                        }
                    }
                    sb.append("\n");
                }
                case "hr" -> sb.append("---\n\n");
                default -> {
                    if (tag.matches("h[1-6]")) {
                        int level = Integer.parseInt(tag.substring(1));
                        sb.append("#".repeat(level))
                                .append(' ')
                                .append(c.text().trim())
                                .append("\n\n");
                    } else if ("pre".equals(tag)) {
                        sb.append(c.wholeText().trim()).append("\n\n");
                    } else {
                        sb.append(c.wholeText().replace('\u00A0', ' ').trim()).append("\n\n");
                    }
                }
            }
        }
    }

    public byte[] docx(NoteEntity note) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun tr = title.createRun();
            tr.setBold(true);
            tr.setFontSize(18);
            tr.setText(note.getTitle() != null ? note.getTitle() : "");

            appendHtmlToDoc(doc, prepareHtmlBodyForStructuredExport(
                            note.getContentHtml() != null ? note.getContentHtml() : ""));
            doc.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new ApiBusinessException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "DOCX_EXPORT", "Word 导出失败：" + e.getMessage());
        }
    }

    private static void appendHtmlToDoc(XWPFDocument doc, String html) {
        Document parsed = org.jsoup.Jsoup.parse(html);
        Element body = parsed.body();
        if (body.children().isEmpty()) {
            if (!body.text().isBlank()) {
                XWPFParagraph p = doc.createParagraph();
                p.createRun().setText(body.text());
            }
            return;
        }
        appendElements(doc, body);
    }

    private static void appendElements(XWPFDocument doc, Element parent) {
        for (Element el : parent.children()) {
            String tag = el.tagName();
            switch (tag) {
                case "div", "section", "article" -> appendElements(doc, el);
                case "p" -> {
                    XWPFParagraph p = doc.createParagraph();
                    p.createRun().setText(el.text());
                }
                case "br" -> doc.createParagraph();
                case "h1", "h2", "h3", "h4" -> {
                    XWPFParagraph p = doc.createParagraph();
                    XWPFRun r = p.createRun();
                    r.setBold(true);
                    int sz = tag.equals("h1") ? 18 : tag.equals("h2") ? 16 : 14;
                    r.setFontSize(sz);
                    r.setText(el.text());
                }
                case "ul", "ol" -> {
                    for (Element li : el.select("> li")) {
                        XWPFParagraph p = doc.createParagraph();
                        p.createRun().setText("• " + li.text());
                    }
                }
                case "blockquote" -> {
                    XWPFParagraph p = doc.createParagraph();
                    XWPFRun r = p.createRun();
                    r.setItalic(true);
                    r.setText(el.text());
                }
                case "pre" -> {
                    XWPFParagraph p = doc.createParagraph();
                    XWPFRun r = p.createRun();
                    r.setFontFamily("Courier New");
                    r.setText(el.text());
                }
                case "table" -> appendTable(doc, el);
                default -> {
                    if (el.children().isEmpty() && !el.text().isBlank()) {
                        XWPFParagraph p = doc.createParagraph();
                        p.createRun().setText(el.text());
                    } else if (!el.children().isEmpty()) {
                        appendElements(doc, el);
                    }
                }
            }
        }
    }

    private static void appendTable(XWPFDocument doc, Element table) {
        for (Element row : table.select("tr")) {
            StringBuilder line = new StringBuilder();
            for (Element cell : row.select("th, td")) {
                if (line.length() > 0) {
                    line.append("  |  ");
                }
                line.append(cell.text());
            }
            if (line.length() > 0) {
                XWPFParagraph p = doc.createParagraph();
                p.createRun().setText(line.toString());
            }
        }
    }

    public byte[] markdown(NoteEntity note) {
        String html = note.getContentHtml() != null ? note.getContentHtml() : "";
        MutableDataSet opts = new MutableDataSet();
        FlexmarkHtmlConverter conv = FlexmarkHtmlConverter.builder(opts).build();
        String mdBody = conv.convert(html);
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(note.getTitle() != null ? note.getTitle() : "").append("\n\n");
        sb.append(mdBody);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String safeFileStem(String title) {
        String t = title == null || title.isBlank() ? "note" : title.trim();
        t = SAFE_FILE.matcher(t).replaceAll("_");
        if (t.length() > 80) {
            t = t.substring(0, 80);
        }
        return t;
    }
}
