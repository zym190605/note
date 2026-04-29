package com.cn.zym.note.web;

import com.cn.zym.note.common.ApiBusinessException;
import com.cn.zym.note.security.JwtUserPrincipal;
import com.cn.zym.note.service.NoteExportService;
import com.cn.zym.note.service.NoteService;
import com.cn.zym.note.web.dto.Payloads;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteRestController {

    private static final Pattern IF_MATCH_DIGITS = Pattern.compile("^\\d+$");

    private final NoteService notes;
    private final NoteExportService export;

    @GetMapping
    public Payloads.NotesPage list(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestParam(required = false) String notebookId,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) Boolean favoriteOnly,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notes.list(principal.userId(), notebookId, tagIds, favoriteOnly, keyword, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Payloads.NoteDetail create(
            @AuthenticationPrincipal JwtUserPrincipal principal, @Valid @RequestBody Payloads.CreateNoteBody body) {
        return notes.create(principal.userId(), body);
    }

    @GetMapping("/{noteId}")
    public Payloads.NoteDetail detail(
            @AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable long noteId) {
        return notes.detail(principal.userId(), noteId);
    }

    @GetMapping("/{noteId}/export")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable long noteId,
            @RequestParam(name = "format", defaultValue = "md") String format) {
        var n = notes.requireActive(principal.userId(), noteId);
        String f = format.trim().toLowerCase(Locale.ROOT);
        byte[] data;
        MediaType mt;
        String ext;
        switch (f) {
            case "pdf" -> {
                data = export.pdf(n);
                mt = MediaType.APPLICATION_PDF;
                ext = ".pdf";
            }
            case "docx", "word" -> {
                data = export.docx(n);
                mt = MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                ext = ".docx";
            }
            case "md", "markdown" -> {
                data = export.markdown(n);
                mt = new MediaType("text", "markdown", StandardCharsets.UTF_8);
                ext = ".md";
            }
            default -> throw new ApiBusinessException(
                    HttpStatus.BAD_REQUEST.value(), "BAD_FORMAT", "format 仅支持 pdf、docx、md");
        }
        String filename = NoteExportService.safeFileStem(n.getTitle()) + ext;
        ContentDisposition cd = ContentDisposition.builder("attachment")
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(mt)
                .body(data);
    }

    @PutMapping("/{noteId}")
    public Payloads.NoteDetail put(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable long noteId,
            @Valid @RequestBody Payloads.PatchNoteBody body,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatchRaw) {

        OptionalInt ov = OptionalInt.empty();
        if (ifMatchRaw != null) {
            String s = ifMatchRaw.replace("\"", "").trim();
            if (IF_MATCH_DIGITS.matcher(s).matches()) {
                ov = OptionalInt.of(Integer.parseInt(s));
            }
        }

        java.util.Optional<Integer> ifMatchOpt =
                ov.isPresent() ? java.util.Optional.of(ov.getAsInt()) : java.util.Optional.empty();

        java.util.Optional<Integer> merged =
                java.util.Optional.ofNullable(body.version()).or(() -> ifMatchOpt);

        Payloads.PatchNoteBody patched = new Payloads.PatchNoteBody(
                body.title(),
                body.contentHtml(),
                body.notebookId(),
                body.tagIds(),
                merged.orElse(null));

        return notes.patch(principal.userId(), noteId, patched, java.util.Optional.empty());
    }

    @DeleteMapping("/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void del(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable long noteId) {
        notes.trash(principal.userId(), noteId);
    }

    @PostMapping("/{noteId}/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void move(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable long noteId,
            @Valid @RequestBody Payloads.MoveNoteBody body) {
        notes.move(principal.userId(), noteId, body.targetNotebookId());
    }

    @PatchMapping("/{noteId}/favorite")
    public Payloads.FavoriteBody fav(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable long noteId,
            @Valid @RequestBody Payloads.FavoriteBody body) {
        notes.fav(principal.userId(), noteId, body.favored());
        return body;
    }

    @PutMapping("/{noteId}/tags")
    public List<Payloads.TagView> tagPut(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable long noteId,
            @Valid @RequestBody Payloads.TagListBody ids) {
        return notes.rewriteTags(principal.userId(), noteId, ids.tagIds());
    }
}
