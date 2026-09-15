package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class ReadingHistoryController {

    private final ReadingHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final DigitalResourceRepository resourceRepository;

    public ReadingHistoryController(
            ReadingHistoryRepository historyRepository,
            UserRepository userRepository,
            BookRepository bookRepository,
            DigitalResourceRepository resourceRepository) {

        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.resourceRepository = resourceRepository;
    }

    // =========================================================
    // GET USER'S CONTINUE READING / HISTORY
    // =========================================================

    @GetMapping("/api/reading-history")
    public ResponseEntity<?> getHistory(
            Authentication authentication) {

        Long userId =
                (Long) authentication.getPrincipal();

        List<ReadingHistory> events =
                historyRepository
                        .findByUser_IdOrderByAccessTimeDesc(userId);

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (ReadingHistory event : events) {

            Map<String, Object> item =
                    new LinkedHashMap<>();

            item.put(
                    "id",
                    event.getId()
            );

            item.put(
                    "bookId",
                    event.getBook() != null
                            ? event.getBook().getId()
                            : null
            );

            item.put(
                    "bookTitle",
                    event.getBook() != null
                            ? event.getBook().getTitle()
                            : null
            );

            item.put(
                    "resourceId",
                    event.getResource() != null
                            ? event.getResource().getId()
                            : null
            );

            item.put(
                    "resourceFile",
                    event.getResource() != null
                            ? event.getResource().getFileName()
                            : null
            );

            item.put(
                    "accessTime",
                    event.getAccessTime()
            );

            item.put(
                    "progressPercent",
                    event.getProgressPercent() != null
                            ? event.getProgressPercent()
                            : 0
            );

            result.add(item);
        }

        return ResponseEntity.ok(result);
    }

    // =========================================================
    // SAVE / UPDATE READING PROGRESS
    // =========================================================

    @PostMapping("/api/reading-history/progress")
    public ResponseEntity<?> saveProgress(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        Long userId =
                (Long) authentication.getPrincipal();

        Object bookIdValue =
                request.get("bookId");

        Object resourceIdValue =
                request.get("resourceId");

        if (bookIdValue == null ||
                resourceIdValue == null) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    "bookId and resourceId are required"
                            )
                    );
        }

        Long bookId =
                Long.valueOf(
                        bookIdValue.toString()
                );

        Long resourceId =
                Long.valueOf(
                        resourceIdValue.toString()
                );

        Optional<User> userOpt =
                userRepository.findById(userId);

        Optional<Book> bookOpt =
                bookRepository.findById(bookId);

        Optional<DigitalResource> resourceOpt =
                resourceRepository.findById(resourceId);

        if (userOpt.isEmpty()) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    "User not found"
                            )
                    );
        }

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

        if (resourceOpt.isEmpty()) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    "Digital resource not found"
                            )
                    );
        }

        DigitalResource digitalResource =
                resourceOpt.get();

        // Prevent connecting progress to the wrong book.
        if (digitalResource.getBook() == null ||
                !digitalResource
                        .getBook()
                        .getId()
                        .equals(bookId)) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    "Digital resource does not belong to this book"
                            )
                    );
        }

        // -----------------------------------------------------
        // Find existing row instead of inserting duplicate
        // -----------------------------------------------------

        ReadingHistory history =
                historyRepository
                        .findByUser_IdAndBook_IdAndResource_Id(
                                userId,
                                bookId,
                                resourceId
                        )
                        .orElseGet(
                                () ->
                                        new ReadingHistory(
                                                userOpt.get(),
                                                bookOpt.get(),
                                                digitalResource
                                        )
                        );

        // -----------------------------------------------------
        // Progress %
        // -----------------------------------------------------

        int progress = 0;

        Object progressValue =
                request.get("progressPercent");

        if (progressValue != null) {

            try {

                progress =
                        Integer.parseInt(
                                progressValue.toString()
                        );

            } catch (NumberFormatException ignored) {
                progress = 0;
            }
        }

        progress =
                Math.max(
                        0,
                        Math.min(
                                100,
                                progress
                        )
                );

        history.setProgressPercent(progress);

        // -----------------------------------------------------
        // Exact EPUB position
        // -----------------------------------------------------

        Object cfiValue =
                request.get("lastLocationCfi");

        if (cfiValue != null) {

            history.setLastLocationCfi(
                    cfiValue.toString()
            );
        }

        // Every update means user read the book recently.
        history.setAccessTime(
                LocalDateTime.now()
        );

        ReadingHistory saved =
                historyRepository.save(history);

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "id",
                saved.getId()
        );

        response.put(
                "bookId",
                bookId
        );

        response.put(
                "resourceId",
                resourceId
        );

        response.put(
                "progressPercent",
                saved.getProgressPercent()
        );

        response.put(
                "lastLocationCfi",
                saved.getLastLocationCfi()
        );

        response.put(
                "accessTime",
                saved.getAccessTime()
        );

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // GET SAVED POSITION FOR ONE BOOK
    // =========================================================

    @GetMapping("/api/reading-history/progress/{bookId}")
    public ResponseEntity<?> getProgress(
            @PathVariable Long bookId,
            Authentication authentication) {

        Long userId =
                (Long) authentication.getPrincipal();

        Optional<ReadingHistory> historyOpt =
                historyRepository
                        .findFirstByUser_IdAndBook_IdOrderByAccessTimeDesc(
                                userId,
                                bookId
                        );

        if (historyOpt.isEmpty()) {

            return ResponseEntity.ok(
                    Map.of(
                            "bookId",
                            bookId,
                            "progressPercent",
                            0
                    )
            );
        }

        ReadingHistory history =
                historyOpt.get();

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "id",
                history.getId()
        );

        response.put(
                "bookId",
                bookId
        );

        response.put(
                "resourceId",
                history.getResource() != null
                        ? history.getResource().getId()
                        : null
        );

        response.put(
                "progressPercent",
                history.getProgressPercent() != null
                        ? history.getProgressPercent()
                        : 0
        );

        response.put(
                "lastLocationCfi",
                history.getLastLocationCfi()
        );

        response.put(
                "accessTime",
                history.getAccessTime()
        );

        return ResponseEntity.ok(response);
    }
}