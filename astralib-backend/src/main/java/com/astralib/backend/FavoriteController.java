package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class FavoriteController {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;

    public FavoriteController(FavoriteRepository favoriteRepository, UserRepository userRepository, BookRepository bookRepository) {
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
    }

    @PostMapping("/api/favorites/{bookId}")
    public ResponseEntity<?> addFavorite(@PathVariable Long bookId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        var userOpt = userRepository.findById(userId);
        var bookOpt = bookRepository.findById(bookId);
        if (userOpt.isEmpty() || bookOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "User or Book not found"));

        if (favoriteRepository.findByUser_IdAndBook_Id(userId, bookId).isPresent()) return ResponseEntity.status(409).body(Map.of("error", "Already favorited"));

        Favorite fav = new Favorite(userOpt.get(), bookOpt.get());
        favoriteRepository.save(fav);
        return ResponseEntity.ok(Map.of("message", "Added to favorites"));
    }

    @DeleteMapping("/api/favorites/{bookId}")
    public ResponseEntity<?> removeFavorite(@PathVariable Long bookId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        var opt = favoriteRepository.findByUser_IdAndBook_Id(userId, bookId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        favoriteRepository.delete(opt.get());
        return ResponseEntity.ok(Map.of("message", "Removed"));
    }

    @GetMapping("/api/favorites")
    public ResponseEntity<?> listFavorites(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<Favorite> favs = favoriteRepository.findByUser_Id(userId);
        var result = favs.stream().map(f -> Map.of(
                "id", f.getId(),
                "bookId", f.getBook().getId(),
                "title", f.getBook().getTitle(),
                "createdAt", f.getCreatedAt()
        )).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
