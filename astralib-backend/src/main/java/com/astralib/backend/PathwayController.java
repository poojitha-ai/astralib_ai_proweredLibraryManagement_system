package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Read-only pathway browsing. Pathways are public like the catalogue,
 * so learners can follow a progression without signing in.
 */
@RestController
@RequestMapping("/api/pathways")
public class PathwayController {

    private final PathwayRepository pathwayRepository;
    private final PathwayBookRepository pathwayBookRepository;
    private final VectorRecordRepository vectorRecordRepository;

    public PathwayController(
            PathwayRepository pathwayRepository,
            PathwayBookRepository pathwayBookRepository,
            VectorRecordRepository vectorRecordRepository
    ) {
        this.pathwayRepository = pathwayRepository;
        this.pathwayBookRepository = pathwayBookRepository;
        this.vectorRecordRepository = vectorRecordRepository;
    }

    // =========================================================
    // ALL PATHWAYS
    // =========================================================

    @GetMapping
    public ResponseEntity<?> list() {

        List<Map<String, Object>> result = new ArrayList<>();

        for (Pathway pathway : pathwayRepository.findAllByOrderByTypeAscNameAsc()) {

            List<PathwayBook> entries =
                    pathwayBookRepository.findByPathway_IdOrderByPositionAsc(pathway.getId());

            Map<String, Integer> levels = new LinkedHashMap<>();

            for (PathwayBook entry : entries) {
                levels.merge(entry.getLevel(), 1, Integer::sum);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", pathway.getId());
            item.put("name", pathway.getName());
            item.put("slug", pathway.getSlug());
            item.put("type", pathway.getType());
            item.put("subject", pathway.getSubject());
            item.put("audience", pathway.getAudience());
            item.put("description", pathway.getDescription());
            item.put("bookCount", entries.size());
            item.put("levels", levels);

            result.add(item);
        }

        return ResponseEntity.ok(result);
    }

    // =========================================================
    // COMPACT MEMBERSHIP LIST
    //
    // Used by the catalogue to show each book's pathway and level
    // without one request per book.
    // =========================================================

    @GetMapping("/memberships")
    public ResponseEntity<?> memberships() {

        List<Map<String, Object>> result = new ArrayList<>();

        for (PathwayBook entry : pathwayBookRepository.findAll()) {

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bookId", entry.getBook().getId());
            item.put("pathwaySlug", entry.getPathway().getSlug());
            item.put("pathwayName", entry.getPathway().getName());
            item.put("type", entry.getPathway().getType());
            item.put("level", entry.getLevel());
            item.put("position", entry.getPosition());

            result.add(item);
        }

        return ResponseEntity.ok(result);
    }

    // =========================================================
    // ONE PATHWAY WITH ITS ORDERED BOOKS
    // =========================================================

    @GetMapping("/{slug}")
    public ResponseEntity<?> detail(@PathVariable String slug) {

        Optional<Pathway> found = pathwayRepository.findBySlug(slug);

        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Pathway pathway = found.get();

        List<Map<String, Object>> books = new ArrayList<>();

        for (PathwayBook entry : pathwayBookRepository
                .findByPathway_IdOrderByPositionAsc(pathway.getId())) {

            Book book = entry.getBook();

            if (book == null) {
                continue;
            }

            long chunks = chunkCount(book);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bookId", book.getId());
            item.put("title", book.getTitle());
            item.put("author", book.getAuthor() == null ? null : book.getAuthor().getName());
            item.put("category", book.getCategory() == null ? null : book.getCategory().getName());
            item.put("level", entry.getLevel());
            item.put("position", entry.getPosition());
            item.put("rationale", entry.getRationale());
            item.put("learningOutcome", entry.getLearningOutcome());
            item.put("chunkCount", chunks);
            item.put("indexed", chunks > 0);
            item.put("resourceAvailable", hasResource(book));
            item.put("sourceUrl", book.getSourceUrl());
            item.put("licence", book.getLicence());
            item.put("edition", book.getEdition());

            if (entry.getPrerequisiteBook() != null) {
                Map<String, Object> prereq = new LinkedHashMap<>();
                prereq.put("bookId", entry.getPrerequisiteBook().getId());
                prereq.put("title", entry.getPrerequisiteBook().getTitle());
                item.put("prerequisite", prereq);
            } else {
                item.put("prerequisite", null);
            }

            books.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", pathway.getId());
        result.put("name", pathway.getName());
        result.put("slug", pathway.getSlug());
        result.put("type", pathway.getType());
        result.put("subject", pathway.getSubject());
        result.put("audience", pathway.getAudience());
        result.put("description", pathway.getDescription());
        result.put("bookCount", books.size());
        result.put("books", books);

        return ResponseEntity.ok(result);
    }

    // =========================================================
    // PATHWAYS THAT CONTAIN ONE BOOK
    // =========================================================

    @GetMapping("/by-book/{bookId}")
    public ResponseEntity<?> forBook(@PathVariable Long bookId) {

        List<Map<String, Object>> result = new ArrayList<>();

        for (PathwayBook entry : pathwayBookRepository.findByBook_Id(bookId)) {

            Pathway pathway = entry.getPathway();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("pathwayId", pathway.getId());
            item.put("name", pathway.getName());
            item.put("slug", pathway.getSlug());
            item.put("type", pathway.getType());
            item.put("level", entry.getLevel());
            item.put("position", entry.getPosition());
            item.put("rationale", entry.getRationale());

            result.add(item);
        }

        return ResponseEntity.ok(result);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    static String documentIdFor(Book book) {
        if (book.getResourceId() != null && !book.getResourceId().isBlank()) {
            return book.getResourceId().trim();
        }
        return "BOOK-ID-" + book.getId();
    }

    private long chunkCount(Book book) {
        return vectorRecordRepository.countByDocumentId(documentIdFor(book));
    }

    private boolean hasResource(Book book) {
        if (book.getDigitalResources() == null) {
            return false;
        }
        return book.getDigitalResources().stream()
                .anyMatch(r -> r != null
                        && r.getFilePath() != null
                        && !r.getFilePath().isBlank());
    }
}
