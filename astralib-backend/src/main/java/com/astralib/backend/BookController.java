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

    public BookController(BookRepository bookRepository, AuthorRepository authorRepository, CategoryRepository categoryRepository) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/api/books")
    public ResponseEntity<?> getBooks(@RequestParam(required = false) String title,
                                      @RequestParam(required = false) String author,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(required = false) Integer year,
                                      @RequestParam(required = false) String keyword) {
        List<Book> books = bookRepository.findAll();

        List<Book> filtered = new ArrayList<>();
        for (Book b : books) {
            if (title != null && (b.getTitle() == null || !b.getTitle().toLowerCase().contains(title.toLowerCase()))) continue;
            if (author != null && (b.getAuthor() == null || !b.getAuthor().getName().toLowerCase().contains(author.toLowerCase()))) continue;
            if (category != null && (b.getCategory() == null || !b.getCategory().getName().toLowerCase().contains(category.toLowerCase()))) continue;
            if (year != null && (b.getPublicationYear() == null || !b.getPublicationYear().equals(year))) continue;
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
