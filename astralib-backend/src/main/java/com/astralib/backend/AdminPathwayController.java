package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Pathway management for administrators.
 *
 * Pathways are a curation tool: they give the catalogue a documented
 * order, level and prerequisite chain, and they never duplicate a book
 * record. Readers browse books and see a difficulty label; only an
 * administrator sees the pathway itself.
 *
 * Everything here lives under /api/admin/**, which SecurityConfig
 * restricts to the ADMIN role, so the protection does not depend on the
 * frontend hiding a link.
 */
@RestController
@RequestMapping("/api/admin/pathways")
public class AdminPathwayController {

    private static final List<String> LEARNING_LEVELS = List.of(
            PathwayBook.LEVEL_BEGINNER,
            PathwayBook.LEVEL_INTERMEDIATE,
            PathwayBook.LEVEL_ADVANCED
    );

    private static final List<String> LEISURE_LEVELS = List.of(
            PathwayBook.LEVEL_ACCESSIBLE,
            PathwayBook.LEVEL_STANDARD,
            PathwayBook.LEVEL_COMPLEX
    );

    private final PathwayRepository pathwayRepository;
    private final PathwayBookRepository pathwayBookRepository;
    private final BookRepository bookRepository;
    private final VectorRecordRepository vectorRecordRepository;

    public AdminPathwayController(
            PathwayRepository pathwayRepository,
            PathwayBookRepository pathwayBookRepository,
            BookRepository bookRepository,
            VectorRecordRepository vectorRecordRepository
    ) {
        this.pathwayRepository = pathwayRepository;
        this.pathwayBookRepository = pathwayBookRepository;
        this.bookRepository = bookRepository;
        this.vectorRecordRepository = vectorRecordRepository;
    }

    // =========================================================
    // PATHWAYS
    // =========================================================

    @GetMapping
    public ResponseEntity<?> list() {

        List<Map<String, Object>> result = new ArrayList<>();

        for (Pathway pathway : pathwayRepository.findAllByOrderByTypeAscNameAsc()) {
            result.add(describe(pathway));
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {

        String name = text(body.get("name"));
        if (name == null) {
            return badRequest("name is required");
        }

        String type = normalizeType(text(body.get("type")));
        if (type == null) {
            return badRequest("type must be LEARNING or LEISURE");
        }

        Pathway pathway = new Pathway();
        pathway.setName(name);
        pathway.setSlug(uniqueSlug(text(body.get("slug")), name, null));
        pathway.setType(type);
        pathway.setSubject(text(body.get("subject")));
        pathway.setAudience(text(body.get("audience")));
        pathway.setDescription(text(body.get("description")));

        Pathway saved = pathwayRepository.save(pathway);

        return ResponseEntity.status(201).body(describe(saved));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {

        Optional<Pathway> found = pathwayRepository.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Pathway pathway = found.get();

        List<PathwayBook> existing =
                pathwayBookRepository.findByPathway_IdOrderByPositionAsc(pathway.getId());

        if (body.containsKey("name")) {
            String name = text(body.get("name"));
            if (name == null) {
                return badRequest("name cannot be empty");
            }
            pathway.setName(name);
            if (!body.containsKey("slug")) {
                pathway.setSlug(uniqueSlug(null, name, pathway.getId()));
            }
        }

        if (body.containsKey("type")) {
            String type = normalizeType(text(body.get("type")));
            if (type == null) {
                return badRequest("type must be LEARNING or LEISURE");
            }

            /*
             * Changing the kind of pathway would leave every existing
             * level meaningless (BEGINNER has no meaning in a leisure
             * pathway), so the move is refused until the memberships
             * are changed deliberately.
             */
            if (!type.equals(pathway.getType()) && !existing.isEmpty()) {
                return ResponseEntity.status(409).body(Map.of(
                        "error",
                        "A pathway with members keeps its type. Remove the books first."
                ));
            }
            pathway.setType(type);
        }

        if (body.containsKey("slug")) {
            pathway.setSlug(uniqueSlug(text(body.get("slug")), pathway.getName(), pathway.getId()));
        }

        if (body.containsKey("subject")) pathway.setSubject(text(body.get("subject")));
        if (body.containsKey("audience")) pathway.setAudience(text(body.get("audience")));
        if (body.containsKey("description")) pathway.setDescription(text(body.get("description")));

        Pathway saved = pathwayRepository.save(pathway);

        return ResponseEntity.ok(describe(saved));
    }

    // =========================================================
    // MEMBERSHIP
    // =========================================================

    @PostMapping("/{id}/books")
    @Transactional
    public ResponseEntity<?> addBook(@PathVariable Long id, @RequestBody Map<String, Object> body) {

        Optional<Pathway> found = pathwayRepository.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Pathway pathway = found.get();

        Long bookId = asLong(body.get("bookId"));
        if (bookId == null) {
            return badRequest("bookId is required");
        }

        Optional<Book> maybeBook = bookRepository.findById(bookId);
        if (maybeBook.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Book not found"));
        }

        List<PathwayBook> entries = pathwayBookRepository
                .findByPathway_IdOrderByPositionAsc(pathway.getId());

        for (PathwayBook existing : entries) {
            if (existing.getBook() != null && existing.getBook().getId().equals(bookId)) {
                return ResponseEntity.status(409).body(Map.of(
                        "error",
                        "That book is already in this pathway. Edit its position instead of adding it twice."
                ));
            }
        }

        Draft draft = parseDraft(pathway, body, null, entries.size() + 1, entries, bookId);
        if (draft.error() != null) {
            return badRequest(draft.error());
        }

        List<Slot> slots = slotsOf(entries);
        slots.add(new Slot(bookId, draft.position(), draft.prerequisiteBookId()));

        String chainProblem = chainProblem(slots);
        if (chainProblem != null) {
            return badRequest(chainProblem);
        }

        PathwayBook entry = new PathwayBook();
        entry.setPathway(pathway);
        entry.setBook(maybeBook.get());
        entry.setLevel(draft.level());
        entry.setPosition(draft.position());
        entry.setRationale(draft.rationale());
        entry.setLearningOutcome(draft.learningOutcome());
        entry.setPrerequisiteBook(resolvePrerequisite(entries, draft.prerequisiteBookId()));

        pathwayBookRepository.save(entry);

        return ResponseEntity.status(201).body(describe(pathway));
    }

    @PutMapping("/{id}/books/{entryId}")
    @Transactional
    public ResponseEntity<?> updateBook(
            @PathVariable Long id,
            @PathVariable Long entryId,
            @RequestBody Map<String, Object> body
    ) {

        Optional<Pathway> found = pathwayRepository.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Pathway pathway = found.get();

        List<PathwayBook> entries = pathwayBookRepository
                .findByPathway_IdOrderByPositionAsc(pathway.getId());

        PathwayBook entry = entries.stream()
                .filter(candidate -> candidate.getId().equals(entryId))
                .findFirst()
                .orElse(null);

        if (entry == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Membership not found in this pathway"));
        }

        Long bookId = entry.getBook() == null ? null : entry.getBook().getId();

        Draft draft = parseDraft(
                pathway,
                body,
                entry,
                entry.getPosition() == null ? 1 : entry.getPosition(),
                entries,
                bookId
        );

        if (draft.error() != null) {
            return badRequest(draft.error());
        }

        /*
         * The change is checked on a copy of the sequence first, so a
         * request that would break the chain leaves the stored rows
         * exactly as they were.
         */
        List<Slot> slots = new ArrayList<>();

        for (PathwayBook candidate : entries) {
            Long candidateBookId = candidate.getBook() == null ? null : candidate.getBook().getId();

            if (candidate.getId().equals(entryId)) {
                slots.add(new Slot(bookId, draft.position(), draft.prerequisiteBookId()));
            } else {
                slots.add(new Slot(
                        candidateBookId,
                        candidate.getPosition(),
                        candidate.getPrerequisiteBook() == null
                                ? null
                                : candidate.getPrerequisiteBook().getId()
                ));
            }
        }

        String chainProblem = chainProblem(slots);
        if (chainProblem != null) {
            return badRequest(chainProblem);
        }

        entry.setLevel(draft.level());
        entry.setPosition(draft.position());
        entry.setRationale(draft.rationale());
        entry.setLearningOutcome(draft.learningOutcome());
        entry.setPrerequisiteBook(resolvePrerequisite(entries, draft.prerequisiteBookId()));

        pathwayBookRepository.save(entry);

        return ResponseEntity.ok(describe(pathway));
    }

    /**
     * Removes one membership. The book, its resource and its vectors are
     * untouched: it simply stops being part of this sequence.
     */
    @DeleteMapping("/{id}/books/{entryId}")
    @Transactional
    public ResponseEntity<?> removeBook(@PathVariable Long id, @PathVariable Long entryId) {

        Optional<Pathway> found = pathwayRepository.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Pathway pathway = found.get();

        List<PathwayBook> entries = pathwayBookRepository
                .findByPathway_IdOrderByPositionAsc(pathway.getId());

        PathwayBook entry = entries.stream()
                .filter(candidate -> candidate.getId().equals(entryId))
                .findFirst()
                .orElse(null);

        if (entry == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Membership not found in this pathway"));
        }

        Long removedBookId = entry.getBook() == null ? null : entry.getBook().getId();

        List<String> dependents = new ArrayList<>();

        for (PathwayBook other : entries) {
            if (other.getId().equals(entryId)) continue;
            if (other.getPrerequisiteBook() != null
                    && removedBookId != null
                    && removedBookId.equals(other.getPrerequisiteBook().getId())) {
                dependents.add(other.getBook() == null
                        ? ("#" + other.getId())
                        : other.getBook().getTitle());
            }
        }

        if (!dependents.isEmpty()) {
            return ResponseEntity.status(409).body(Map.of(
                    "error",
                    "Change the prerequisite of " + String.join(", ", dependents)
                            + " before removing this book."
            ));
        }

        pathwayBookRepository.delete(entry);

        return ResponseEntity.ok(describe(pathway));
    }

    // =========================================================
    // METADATA GAPS
    //
    // Books that are in the library or in a pathway but are missing
    // something a reader would notice: no author, no category, no
    // description, no file, no embeddings or no attribution.
    // =========================================================

    @GetMapping("/metadata-gaps")
    public ResponseEntity<?> metadataGaps() {

        Map<Long, Book> candidates = new LinkedHashMap<>();

        for (Book book : bookRepository.findWithStoredResource()) {
            candidates.put(book.getId(), book);
        }

        for (PathwayBook entry : pathwayBookRepository.findAll()) {
            if (entry.getBook() != null) {
                candidates.put(entry.getBook().getId(), entry.getBook());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (Book book : candidates.values()) {

            List<String> missing = new ArrayList<>();

            if (book.getAuthor() == null) missing.add("author");
            if (book.getCategory() == null) missing.add("category");
            if (book.getDescription() == null || book.getDescription().isBlank()) missing.add("description");
            if (book.getPublicationYear() == null) missing.add("publication year");
            if (book.getGenres().isEmpty()) missing.add("genre");
            if (book.getLicence() == null || book.getLicence().isBlank()) missing.add("licence");
            if (book.getSourceUrl() == null || book.getSourceUrl().isBlank()) missing.add("source");
            if (!hasResource(book)) missing.add("EPUB file");
            if (chunkCount(book) == 0) missing.add("indexed text");

            if (missing.isEmpty()) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bookId", book.getId());
            item.put("title", book.getTitle());
            item.put("author", book.getAuthor() == null ? null : book.getAuthor().getName());
            item.put("category", book.getCategory() == null ? null : book.getCategory().getName());
            item.put("missing", missing);

            result.add(item);
        }

        return ResponseEntity.ok(result);
    }

    // =========================================================
    // DRAFT VALIDATION
    // =========================================================

    /** A membership request once its values have been resolved. */
    private record Draft(
            String level,
            int position,
            Long prerequisiteBookId,
            String rationale,
            String learningOutcome,
            String error
    ) {}

    /** One place in a sequence, used to check the prerequisite chain. */
    private record Slot(Long bookId, Integer position, Long prerequisiteBookId) {}

    /**
     * Resolves a request body into a membership without touching any
     * managed entity, so a rejected request cannot be flushed.
     */
    private Draft parseDraft(
            Pathway pathway,
            Map<String, Object> body,
            PathwayBook current,
            int fallbackPosition,
            List<PathwayBook> siblings,
            Long bookId
    ) {

        String level = text(body.get("level"));

        if (level == null && current != null) {
            level = current.getLevel();
        }

        if (level == null) {
            return fail("level is required");
        }

        level = level.toUpperCase(Locale.ROOT);

        List<String> allowed = allowedLevels(pathway.getType());

        if (!allowed.contains(level)) {
            return fail("A " + pathway.getType() + " pathway uses "
                    + String.join(" / ", allowed) + " levels, not " + level);
        }

        Integer position = asInt(body.get("position"));

        if (position == null) {
            position = current != null && current.getPosition() != null
                    ? current.getPosition()
                    : fallbackPosition;
        }

        if (position < 1) {
            return fail("position must be 1 or greater");
        }

        for (PathwayBook sibling : siblings) {
            if (current != null && sibling.getId().equals(current.getId())) continue;

            if (sibling.getPosition() != null && sibling.getPosition().equals(position)) {
                return fail("Position " + position + " is already taken by "
                        + (sibling.getBook() == null ? "another book" : sibling.getBook().getTitle()));
            }
        }

        Long prerequisiteId;

        if (body.containsKey("prerequisiteBookId")) {
            prerequisiteId = asLong(body.get("prerequisiteBookId"));
        } else {
            prerequisiteId = current == null || current.getPrerequisiteBook() == null
                    ? null
                    : current.getPrerequisiteBook().getId();
        }

        if (prerequisiteId != null && prerequisiteId.equals(bookId)) {
            return fail("A book cannot be its own prerequisite");
        }

        String rationale = body.containsKey("rationale")
                ? text(body.get("rationale"))
                : (current == null ? null : current.getRationale());

        String learningOutcome = body.containsKey("learningOutcome")
                ? text(body.get("learningOutcome"))
                : (current == null ? null : current.getLearningOutcome());

        return new Draft(level, position, prerequisiteId, rationale, learningOutcome, null);
    }

    private static Draft fail(String message) {
        return new Draft(null, 0, null, null, null, message);
    }

    private static List<Slot> slotsOf(List<PathwayBook> entries) {

        List<Slot> slots = new ArrayList<>();

        for (PathwayBook entry : entries) {
            slots.add(new Slot(
                    entry.getBook() == null ? null : entry.getBook().getId(),
                    entry.getPosition(),
                    entry.getPrerequisiteBook() == null ? null : entry.getPrerequisiteBook().getId()
            ));
        }

        return slots;
    }

    /**
     * Every prerequisite must be an earlier book in the same pathway.
     * Because a prerequisite always sits lower in the sequence, a cycle
     * cannot be formed.
     */
    private String chainProblem(List<Slot> slots) {

        for (Slot slot : slots) {

            if (slot.prerequisiteBookId() == null) continue;

            Slot prerequisite = null;

            for (Slot candidate : slots) {
                if (candidate.bookId() != null
                        && candidate.bookId().equals(slot.prerequisiteBookId())) {
                    prerequisite = candidate;
                    break;
                }
            }

            if (prerequisite == null) {
                return "Book #" + slot.prerequisiteBookId()
                        + " must be part of this pathway before it can be a prerequisite";
            }

            if (prerequisite.position() >= slot.position()) {
                return "A prerequisite must come earlier in the sequence: book #"
                        + slot.prerequisiteBookId() + " is at position "
                        + prerequisite.position() + ", this book is at " + slot.position();
            }
        }

        return null;
    }

    private Book resolvePrerequisite(List<PathwayBook> entries, Long prerequisiteBookId) {

        if (prerequisiteBookId == null) {
            return null;
        }

        for (PathwayBook entry : entries) {
            if (entry.getBook() != null && entry.getBook().getId().equals(prerequisiteBookId)) {
                return entry.getBook();
            }
        }

        return null;
    }

    // =========================================================
    // SERIALISATION
    // =========================================================

    private Map<String, Object> describe(Pathway pathway) {

        List<PathwayBook> entries = pathwayBookRepository
                .findByPathway_IdOrderByPositionAsc(pathway.getId());

        Map<String, Integer> levels = new LinkedHashMap<>();
        List<Map<String, Object>> books = new ArrayList<>();

        for (PathwayBook entry : entries) {

            Book book = entry.getBook();
            if (book == null) continue;

            levels.merge(entry.getLevel(), 1, Integer::sum);

            long chunks = chunkCount(book);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entryId", entry.getId());
            item.put("bookId", book.getId());
            item.put("title", book.getTitle());
            item.put("author", book.getAuthor() == null ? null : book.getAuthor().getName());
            item.put("category", book.getCategory() == null ? null : book.getCategory().getName());
            item.put("position", entry.getPosition());
            item.put("level", entry.getLevel());
            item.put("rationale", entry.getRationale());
            item.put("learningOutcome", entry.getLearningOutcome());
            item.put("prerequisiteBookId",
                    entry.getPrerequisiteBook() == null ? null : entry.getPrerequisiteBook().getId());
            item.put("prerequisiteTitle",
                    entry.getPrerequisiteBook() == null ? null : entry.getPrerequisiteBook().getTitle());
            item.put("chunkCount", chunks);
            item.put("indexed", chunks > 0);
            item.put("resourceAvailable", hasResource(book));

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
        result.put("levels", levels);
        result.put("allowedLevels", allowedLevels(pathway.getType()));
        result.put("books", books);

        return result;
    }

    // =========================================================
    // SMALL HELPERS
    // =========================================================

    private static List<String> allowedLevels(String type) {
        return Pathway.TYPE_LEISURE.equalsIgnoreCase(type) ? LEISURE_LEVELS : LEARNING_LEVELS;
    }

    private static String normalizeType(String value) {
        if (value == null) return null;
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (Pathway.TYPE_LEARNING.equals(upper) || Pathway.TYPE_LEISURE.equals(upper)) {
            return upper;
        }
        return null;
    }

    private String uniqueSlug(String requested, String name, Long ignoreId) {

        String base = slugify(requested == null || requested.isBlank() ? name : requested);
        if (base.isEmpty()) base = "pathway";

        String candidate = base;
        int suffix = 2;

        while (true) {
            Optional<Pathway> existing = pathwayRepository.findBySlug(candidate);
            if (existing.isEmpty() || Objects.equals(existing.get().getId(), ignoreId)) {
                return candidate;
            }
            candidate = base + "-" + suffix++;
        }
    }

    private static String slugify(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Long asLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer asInt(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long chunkCount(Book book) {
        return vectorRecordRepository.countByDocumentId(PathwayController.documentIdFor(book));
    }

    private boolean hasResource(Book book) {
        return book.getDigitalResources().stream()
                .anyMatch(r -> r != null && r.getFilePath() != null && !r.getFilePath().isBlank());
    }

    private static ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
