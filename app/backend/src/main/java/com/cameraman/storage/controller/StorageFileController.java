package com.cameraman.storage.controller;

import com.cameraman.storage.provider.StoredObject;
import com.cameraman.storage.service.SignedLinkService;
import com.cameraman.storage.service.StorageService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

/**
 * Serves files held in providers that have no native presigned URLs (Google Drive). Not public
 * access: the URL only exists because an authorised, authenticated request just asked the
 * application for it, it is HMAC-signed, and it expires. Anything invalid or expired is a plain
 * 404 so nothing is revealed about what exists.
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class StorageFileController {

    private final SignedLinkService signedLinks;
    private final StorageService storageService;

    @GetMapping("/{token}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String token) {
        Optional<String> key = signedLinks.verify(token);
        if (key.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        StoredObject object;
        try {
            object = storageService.open(key.get());
        } catch (Exception e) {
            log.warn("Signed file link could not be served: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        StreamingResponseBody body = (OutputStream out) -> {
            try (InputStream in = object.stream()) {
                in.transferTo(out);
            } catch (java.io.IOException e) {
                // Client navigated away or lost connection mid-download: not a server fault.
                log.debug("File stream ended early: {}", e.getMessage());
            }
        };

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(safeContentType(object.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=600")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline");
        if (object.length() >= 0) response.contentLength(object.length());
        return response.body(body);
    }

    /** Only render known-safe image/PDF types inline; everything else is an opaque download. */
    private static MediaType safeContentType(String type) {
        try {
            MediaType mt = MediaType.parseMediaType(type);
            if ("image".equals(mt.getType()) && !"svg+xml".equals(mt.getSubtype())) return mt;
            if (MediaType.APPLICATION_PDF.equalsTypeAndSubtype(mt)) return mt;
        } catch (Exception ignored) {
            // fall through
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
