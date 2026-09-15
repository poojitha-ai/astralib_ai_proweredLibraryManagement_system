package com.astralib.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class DatasetAdminController {

    @Autowired
    private DatasetLoaderService datasetLoaderService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ScholarlyPaperRepository scholarlyPaperRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private ReadingHistoryRepository readingHistoryRepository;

    @Autowired
    private UserInteractionRepository userInteractionRepository;

    @PostMapping("/load-datasets")
    public ResponseEntity<?> loadDatasets() {
        datasetLoaderService.loadAllDatasets();
        return ResponseEntity.ok(Map.of(
            "message", "Datasets loaded successfully",
            "stats", getStatsInternal()
        ));
    }

    @GetMapping("/dataset-stats")
    public ResponseEntity<?> getDatasetStats() {
        return ResponseEntity.ok(getStatsInternal());
    }

    private Map<String, Object> getStatsInternal() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("booksCount", bookRepository.count());
        stats.put("authorsCount", authorRepository.count());
        stats.put("categoriesCount", categoryRepository.count());
        stats.put("scholarlyPapersCount", scholarlyPaperRepository.count());
        stats.put("usersCount", userRepository.count());
        stats.put("activitiesProcessedCount", userInteractionRepository.count());
        stats.put("favoritesCount", favoriteRepository.count());
        stats.put("readingHistoryCount", readingHistoryRepository.count());
        return stats;
    }
}
