package com.astralib.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class ResourceController {

    private final DigitalResourceRepository resourceRepository;
    private final BookRepository bookRepository;

    @Value("${astralib.data-root:../data}")
    private String dataRoot;

    @Value("${astralib.upload-root:uploads}")
    private String uploadRoot;

    public ResourceController(
            DigitalResourceRepository resourceRepository,
            BookRepository bookRepository) {

        this.resourceRepository = resourceRepository;
        this.bookRepository = bookRepository;
    }

    // =========================================================
    // 1. Upload a digital resource for a book
    // =========================================================
    @PostMapping("/api/resources")
    public ResponseEntity<?> uploadResource(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bookId") Long bookId,
            @RequestParam(
                    value = "resourceType",
                    required = false
            )
            String resourceType)
            throws Exception {

        if (file == null || file.isEmpty()) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    "File is required"
                            )
                    );
        }

        Optional<Book> bookOpt =
                bookRepository.findById(bookId);

        if (bookOpt.isEmpty()) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    "Book not found"
                            )
                    );
        }

        // =====================================================
        // Portable upload directory
        // =====================================================

        Path uploadsDirectory =
                getUploadRootPath();

        Files.createDirectories(
                uploadsDirectory
        );

        String originalName =
                file.getOriginalFilename();

        if (
                originalName == null ||
                originalName.isBlank()
        ) {

            originalName =
                    "resource";
        }

        originalName =
                StringUtils.cleanPath(
                        originalName
                );

        // Prevent names such as ../../file.pdf
        originalName =
                Paths.get(originalName)
                        .getFileName()
                        .toString();

        String filename =
                System.currentTimeMillis()
                        + "_"
                        + originalName;

        Path target =
                uploadsDirectory
                        .resolve(filename)
                        .normalize();

        if (
                !target.startsWith(
                        uploadsDirectory
                )
        ) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    "Invalid file path"
                            )
                    );
        }

        Files.copy(
                file.getInputStream(),
                target,
                StandardCopyOption.REPLACE_EXISTING
        );

        // =====================================================
        // Create DigitalResource database record
        // =====================================================

        DigitalResource dr =
                new DigitalResource();

        dr.setBook(
                bookOpt.get()
        );

        dr.setFileName(
                originalName
        );

        /*
         * IMPORTANT:
         *
         * Never store the computer's absolute path.
         *
         * Example stored value:
         *
         * uploads/1788089313593_AstraLib_SRS.pdf
         */
        dr.setFilePath(
                "uploads/"
                        + filename
        );

        String detectedType =
                Files.probeContentType(
                        target
                );

        // =====================================================
        // Windows may not detect EPUB correctly
        // =====================================================

        if (
                originalName
                        .toLowerCase()
                        .endsWith(".epub")
        ) {

            detectedType =
                    "application/epub+zip";
        }

        // =====================================================
        // PDF MIME type
        // =====================================================

        if (
                originalName
                        .toLowerCase()
                        .endsWith(".pdf")
        ) {

            detectedType =
                    "application/pdf";
        }

        if (detectedType == null) {

            detectedType =
                    "application/octet-stream";
        }

        dr.setFormat(
                detectedType
        );

        dr.setResourceType(
                resourceType == null
                        ? "BOOK"
                        : resourceType
        );

        DigitalResource saved =
                resourceRepository.save(dr);

        return ResponseEntity.ok(
                saved
        );
    }

    // =========================================================
    // 2. Get resource metadata by resource ID
    // =========================================================
    @GetMapping("/api/resources/{id}")
    public ResponseEntity<?> getResource(
            @PathVariable Long id) {

        Optional<DigitalResource> opt =
                resourceRepository.findById(id);

        if (opt.isEmpty()) {

            return ResponseEntity
                    .notFound()
                    .build();
        }

        return ResponseEntity.ok(
                opt.get()
        );
    }

    // =========================================================
    // 3. Get all digital resources belonging to a book
    // =========================================================
    @GetMapping("/api/books/{bookId}/resources")
    public ResponseEntity<?> getResourcesByBook(
            @PathVariable Long bookId) {

        Optional<Book> bookOpt =
                bookRepository.findById(bookId);

        if (bookOpt.isEmpty()) {

            return ResponseEntity
                    .notFound()
                    .build();
        }

        List<DigitalResource> resources =
                resourceRepository
                        .findByBook_Id(
                                bookId
                        );

        return ResponseEntity.ok(
                resources
        );
    }

    // =========================================================
    // 4. Serve EPUB / PDF / digital file
    // =========================================================
    @GetMapping("/api/resources/file/{id}")
    public ResponseEntity<?> getResourceFile(
            @PathVariable Long id)
            throws Exception {

        Optional<DigitalResource> opt =
                resourceRepository.findById(id);

        if (opt.isEmpty()) {

            return ResponseEntity
                    .notFound()
                    .build();
        }

        DigitalResource dr =
                opt.get();

        if (
                dr.getFilePath() == null ||
                dr.getFilePath().isBlank()
        ) {

            return ResponseEntity
                    .notFound()
                    .build();
        }

        File file =
                resolveDigitalResourceFile(
                        dr.getFilePath()
                );

        if (
                file == null ||
                !file.exists() ||
                !file.isFile()
        ) {

            return ResponseEntity
                    .notFound()
                    .build();
        }

        String contentType =
                dr.getFormat();

        // =====================================================
        // EPUB MIME type
        // =====================================================

        if (
                dr.getFileName() != null &&
                dr.getFileName()
                        .toLowerCase()
                        .endsWith(".epub")
        ) {

            contentType =
                    "application/epub+zip";
        }

        // =====================================================
        // PDF MIME type
        // =====================================================

        if (
                dr.getFileName() != null &&
                dr.getFileName()
                        .toLowerCase()
                        .endsWith(".pdf")
        ) {

            contentType =
                    "application/pdf";
        }

        if (
                contentType == null ||
                contentType.isBlank()
        ) {

            contentType =
                    "application/octet-stream";
        }

        InputStreamResource resource =
                new InputStreamResource(
                        new FileInputStream(
                                file
                        )
                );

        return ResponseEntity
                .ok()

                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\""
                                + dr.getFileName()
                                + "\""
                )

                .contentLength(
                        file.length()
                )

                .contentType(
                        MediaType.parseMediaType(
                                contentType
                        )
                )

                .body(
                        resource
                );
    }

    // =========================================================
    // Portable path helpers
    // =========================================================

    private Path getDataRootPath() {

        return Paths
                .get(dataRoot)
                .toAbsolutePath()
                .normalize();
    }

    private Path getUploadRootPath() {

        return Paths
                .get(uploadRoot)
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Resolves a DigitalResource database path into the
     * actual physical file on the current computer.
     *
     * Supported portable examples:
     *
     * fulltext/books/sherlock_holmes/sherlock_holmes.epub
     *
     * uploads/123456_AstraLib_SRS.pdf
     *
     * Old absolute paths are temporarily supported so that
     * existing records continue working during migration.
     */
    private File resolveDigitalResourceFile(
            String storedPath) {

        if (
                storedPath == null ||
                storedPath.isBlank()
        ) {

            return null;
        }

        String trimmed =
                storedPath.trim();

        // =====================================================
        // 1. Backward compatibility with an existing absolute
        //    path while we migrate old database rows.
        // =====================================================

        try {

            Path directPath =
                    Paths.get(trimmed)
                            .toAbsolutePath()
                            .normalize();

            Path originalPath =
                    Paths.get(trimmed);

            if (
                    originalPath.isAbsolute() &&
                    Files.exists(directPath) &&
                    Files.isRegularFile(directPath)
            ) {

                return directPath.toFile();
            }

        } catch (Exception ignored) {
        }

        // Use forward slashes internally.
        String normalized =
                trimmed
                        .replace('\\', '/');

        String lower =
                normalized
                        .toLowerCase();

        // =====================================================
        // 2. Convert an old absolute ".../data/..." path
        //    into a portable data-root relative path.
        // =====================================================

        int dataMarker =
                lower.indexOf(
                        "/data/"
                );

        if (dataMarker >= 0) {

            String relative =
                    normalized.substring(
                            dataMarker
                                    + "/data/".length()
                    );

            return resolveInsideRoot(
                    getDataRootPath(),
                    relative
            );
        }

        // =====================================================
        // 3. Convert an old absolute ".../uploads/..." path
        //    into a portable upload-root relative path.
        // =====================================================

        int uploadsMarker =
                lower.indexOf(
                        "/uploads/"
                );

        if (uploadsMarker >= 0) {

            String relative =
                    normalized.substring(
                            uploadsMarker
                                    + "/uploads/".length()
                    );

            return resolveInsideRoot(
                    getUploadRootPath(),
                    relative
            );
        }

        // =====================================================
        // 4. Portable upload path
        //
        //    uploads/example.pdf
        // =====================================================

        if (
                lower.startsWith(
                        "uploads/"
                )
        ) {

            String relative =
                    normalized.substring(
                            "uploads/".length()
                    );

            return resolveInsideRoot(
                    getUploadRootPath(),
                    relative
            );
        }

        // =====================================================
        // 5. Optional "data/" prefix
        //
        //    data/fulltext/books/...
        // =====================================================

        if (
                lower.startsWith(
                        "data/"
                )
        ) {

            normalized =
                    normalized.substring(
                            "data/".length()
                    );
        }

        // =====================================================
        // 6. Default:
        //
        //    treat relative paths as data-root paths.
        //
        //    Example:
        //
        //    fulltext/books/sherlock_holmes/...
        // =====================================================

        return resolveInsideRoot(
                getDataRootPath(),
                normalized
        );
    }

    /**
     * Resolve a relative path safely inside a configured root.
     */
    private File resolveInsideRoot(
            Path root,
            String relativePath) {

        try {

            String cleanRelative =
                    relativePath
                            .replace('\\', '/');

            Path resolved =
                    root
                            .resolve(
                                    cleanRelative
                            )
                            .normalize();

            /*
             * Prevent:
             *
             * ../../outside-project/file
             */
            if (
                    !resolved.startsWith(
                            root
                    )
            ) {

                return null;
            }

            return resolved.toFile();

        } catch (Exception e) {

            return null;
        }
    }
}