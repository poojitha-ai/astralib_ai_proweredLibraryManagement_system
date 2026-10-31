package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class BookController {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final PathwayBookRepository pathwayBookRepository;

    public BookController(BookRepository bookRepository, AuthorRepository authorRepository, CategoryRepository categoryRepository, PathwayBookRepository pathwayBookRepository) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.categoryRepository = categoryRepository;
        this.pathwayBookRepository = pathwayBookRepository;
    }

    @GetMapping("/api/books")
    public ResponseEntity<?> getBooks(@RequestParam(required = false) String title,
                                      @RequestParam(required = false) String author,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(required = false) Integer year,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String genre,
                                      @RequestParam(required = false) String subject,
                                      @RequestParam(required = false) String level,
                                      @RequestParam(required = false) String pathway,
                                      @RequestParam(required = false) Boolean resourceOnly,
                                      @RequestParam(required = false) Boolean catalogueOnly,
                                      @RequestParam(required = false) String format) {

        /*
         * The public library catalogue asks for catalogueOnly=true: the
         * published collection, i.e. books with a readable file plus the
         * curated entries that are described and levelled but not yet
         * available to read. resourceOnly still means "readable now".
         */
        List<Book> books;

        if (Boolean.TRUE.equals(catalogueOnly)) {
            books = bookRepository.findCatalogueBooks();
        } else if (Boolean.TRUE.equals(resourceOnly)) {
            books = bookRepository.findWithStoredResource();
        } else {
            books = bookRepository.findAll();
        }

        /*
         * Level and pathway filters come from pathway membership.
         * Load the memberships once instead of querying per book.
         */
        Map<Long, List<PathwayBook>> memberships = new HashMap<>();

        if (level != null || pathway != null) {
            for (PathwayBook membership : pathwayBookRepository.findAll()) {
                memberships
                        .computeIfAbsent(membership.getBook().getId(), k -> new ArrayList<>())
                        .add(membership);
            }
        }

        String wantedLevel = level == null ? null : level.trim().toLowerCase();
        String wantedPathway = pathway == null ? null : pathway.trim().toLowerCase();

        List<Book> filtered = new ArrayList<>();
        for (Book b : books) {
            if (title != null && (b.getTitle() == null || !b.getTitle().toLowerCase().contains(title.toLowerCase()))) continue;
            if (author != null && (b.getAuthor() == null || !b.getAuthor().getName().toLowerCase().contains(author.toLowerCase()))) continue;
            if (category != null && (b.getCategory() == null || !b.getCategory().getName().toLowerCase().contains(category.toLowerCase()))) continue;
            if (year != null && (b.getPublicationYear() == null || !b.getPublicationYear().equals(year))) continue;
            if (genre != null) {
                /*
                 * Genre accepts a comma-separated list so a catalogue
                 * tile can cover closely related labels (for example
                 * "Science Fiction, Early Science Fiction").
                 */
                List<String> wanted = Arrays.stream(genre.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(value -> value.toLowerCase())
                        .toList();

                boolean matches = wanted.isEmpty() || b.getGenres().stream()
                        .anyMatch(value -> value != null && wanted.stream()
                                .anyMatch(g -> value.toLowerCase().contains(g)));
                if (!matches) continue;
            }

            if (format != null) {
                String f = format.trim().toLowerCase();
                boolean matches = (b.getResourceType() != null && b.getResourceType().toLowerCase().equals(f))
                        || b.getDigitalResources().stream()
                                .anyMatch(r -> r.getFormat() != null && r.getFormat().toLowerCase().equals(f));
                if (!matches) continue;
            }
            if (subject != null) {
                String s = subject.toLowerCase();
                boolean matches = b.getSubjects().stream()
                        .anyMatch(value -> value != null && value.toLowerCase().contains(s));
                if (!matches) continue;
            }
            if (wantedLevel != null || wantedPathway != null) {
                List<PathwayBook> bookMemberships = memberships.get(b.getId());
                if (bookMemberships == null || bookMemberships.isEmpty()) continue;

                boolean matches = bookMemberships.stream().anyMatch(m -> {
                    boolean levelOk = wantedLevel == null
                            || (m.getLevel() != null && m.getLevel().toLowerCase().equals(wantedLevel));
                    boolean pathwayOk = wantedPathway == null
                            || (m.getPathway().getSlug() != null && m.getPathway().getSlug().toLowerCase().equals(wantedPathway));
                    return levelOk && pathwayOk;
                });
                if (!matches) continue;
            }
            if (keyword != null) {
                String k = keyword.toLowerCase();
                boolean matches = (b.getTitle() != null && b.getTitle().toLowerCase().contains(k))
                        || (b.getDescription() != null && b.getDescription().toLowerCase().contains(k))
                        || (b.getIsbn() != null && b.getIsbn().toLowerCase().contains(k));
                if (!matches) continue;
            }
            filtered.add(b);
        }

        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/api/books/{id}")
    public ResponseEntity<?> getBookById(@PathVariable Long id) {
        return bookRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/books")
    public ResponseEntity<?> addBook(@RequestBody Map<String, Object> body, Authentication authentication) {
        String title = (String) body.get("title");
        if (title == null || title.trim().isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Title is required"));

        Long authorId = body.get("authorId") == null ? null : Long.valueOf(String.valueOf(body.get("authorId")));
        Long categoryId = body.get("categoryId") == null ? null : Long.valueOf(String.valueOf(body.get("categoryId")));

        if (authorId == null) return ResponseEntity.badRequest().body(Map.of("error", "authorId is required"));
        if (categoryId == null) return ResponseEntity.badRequest().body(Map.of("error", "categoryId is required"));

        Optional<Author> author = authorRepository.findById(authorId);
        Optional<Category> category = categoryRepository.findById(categoryId);
        if (author.isEmpty() || category.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Author or Category not found"));

        Book book = new Book();
        book.setTitle(title);
        book.setDescription((String) body.get("description"));
        book.setIsbn((String) body.get("isbn"));
        if (body.get("publicationYear") != null) {
            try { book.setPublicationYear(Integer.valueOf(String.valueOf(body.get("publicationYear")))); } catch (Exception ignored) {}
        }
        book.setPublisher((String) body.get("publisher"));
        book.setLanguage((String) body.get("language"));
        book.setCoverImage((String) body.get("coverImage"));
        book.setAuthor(author.get());
        book.setCategory(category.get());

        // Prevent duplicate ISBN
        if (book.getIsbn() != null && !book.getIsbn().isBlank()) {
            Optional<Book> existing = bookRepository.findByIsbn(book.getIsbn());
            if (existing.isPresent()) return ResponseEntity.status(409).body(Map.of("error", "Book with same ISBN already exists"));
        }

        Book saved = bookRepository.save(book);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/api/books/{id}")
    public ResponseEntity<?> updateBook(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return bookRepository.findById(id).map(b -> {
            if (body.get("title") != null) {
                String t = (String) body.get("title");
                if (t.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Title cannot be empty"));
                b.setTitle(t);
            }
            if (body.get("description") != null) b.setDescription((String) body.get("description"));
            if (body.get("isbn") != null) b.setIsbn((String) body.get("isbn"));
            if (body.get("publicationYear") != null) {
                try { b.setPublicationYear(Integer.valueOf(String.valueOf(body.get("publicationYear")))); } catch (Exception ignored) {}
            }
            if (body.get("authorId") != null) {
                Long aid = Long.valueOf(String.valueOf(body.get("authorId")));
                authorRepository.findById(aid).ifPresent(b::setAuthor);
            }
            if (body.get("categoryId") != null) {
                Long cid = Long.valueOf(String.valueOf(body.get("categoryId")));
                categoryRepository.findById(cid).ifPresent(b::setCategory);
            }

            bookRepository.save(b);
            return ResponseEntity.ok(b);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/books/{id}")
    public ResponseEntity<?> deleteBook(@PathVariable Long id) {
        return bookRepository.findById(id).map(b -> {
            bookRepository.delete(b);
            return ResponseEntity.ok(Map.of("message", "Deleted"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
