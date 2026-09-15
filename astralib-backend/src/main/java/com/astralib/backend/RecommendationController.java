package com.astralib.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class RecommendationController {

    @Autowired
    private RecommendationService recommendationService;

    @GetMapping({"/api/recommendations", "/api/user/recommendations"})
    public ResponseEntity<?> getMyRecommendations(
            Authentication authentication,
            @RequestParam(defaultValue = "10") int limit) {
        
        Long userId = (Long) authentication.getPrincipal();
        List<RecommendationService.RecommendationDTO> recommendations = 
            recommendationService.getRecommendationsForUser(userId, limit);
        
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/api/users/{userId}/recommendations")
    public ResponseEntity<?> getUserRecommendations(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        
        List<RecommendationService.RecommendationDTO> recommendations = 
            recommendationService.getRecommendationsForUser(userId, limit);
        
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/api/users/{userId}/score/{bookId}")
    public ResponseEntity<?> getUserBookScore(
            @PathVariable Long userId,
            @PathVariable Long bookId) {
        
        double score = recommendationService.calculateUserBookScore(userId, bookId);
        
        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "bookId", bookId,
            "score", score
        ));
    }
}
