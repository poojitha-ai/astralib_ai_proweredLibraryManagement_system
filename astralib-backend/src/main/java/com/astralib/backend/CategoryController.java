package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping("/api/categories")
    public ResponseEntity<List<Category>> getCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    @PostMapping("/api/categories")
    public ResponseEntity<?> addCategory(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Name is required"));

        if (categoryRepository.findByName(name).isPresent()) return ResponseEntity.status(409).body(Map.of("error", "Category already exists"));

        Category c = new Category(name, body.get("description"));
        return ResponseEntity.ok(categoryRepository.save(c));
    }

    @PutMapping("/api/categories/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return categoryRepository.findById(id).map(c -> {
            if (body.get("name") != null) c.setName(body.get("name"));
            if (body.get("description") != null) c.setDescription(body.get("description"));
            categoryRepository.save(c);
            return ResponseEntity.ok(c);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        return categoryRepository.findById(id).map(c -> {
            categoryRepository.delete(c);
            return ResponseEntity.ok(Map.of("message", "Deleted"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
