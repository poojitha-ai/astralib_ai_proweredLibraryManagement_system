package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class AuthorController {

    private final AuthorRepository authorRepository;

    public AuthorController(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @GetMapping("/api/authors")
    public ResponseEntity<List<Author>> getAuthors() {
        return ResponseEntity.ok(authorRepository.findAll());
    }

    @GetMapping("/api/authors/{id}")
    public ResponseEntity<?> getAuthor(@PathVariable Long id) {
        return authorRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/authors")
    public ResponseEntity<?> addAuthor(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Name is required"));
        Author a = new Author(name, body.get("bio"));
        return ResponseEntity.ok(authorRepository.save(a));
    }

    @PutMapping("/api/authors/{id}")
    public ResponseEntity<?> updateAuthor(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return authorRepository.findById(id).map(a -> {
            if (body.get("name") != null) a.setName(body.get("name"));
            if (body.get("bio") != null) a.setBio(body.get("bio"));
            authorRepository.save(a);
            return ResponseEntity.ok(a);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/authors/{id}")
    public ResponseEntity<?> deleteAuthor(@PathVariable Long id) {
        return authorRepository.findById(id).map(a -> {
            authorRepository.delete(a);
            return ResponseEntity.ok(Map.of("message", "Deleted"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
