package com.astralib.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private static final Logger logger = LoggerFactory.getLogger(RecommendationService.class);

    @Autowired
    private BookRepository bookRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private FavoriteRepository favoriteRepository;
    
    @Autowired
    private ReadingHistoryRepository readingHistoryRepository;
    
    @Autowired
    private UserInteractionRepository userInteractionRepository;
    
    @Autowired
    private InteractionScoreConfig scoreConfig;

    public double calculateUserBookScore(Long userId, Long bookId) {
        Optional<User> userOpt = userRepository.findById(userId);
        Optional<Book> bookOpt = bookRepository.findById(bookId);
        if (userOpt.isEmpty() || bookOpt.isEmpty()) {
            return 0.0;
        }

        Book book = bookOpt.get();
        double totalScore = 0.0;

        // Add scores from user_interactions table
        if (book.getResourceId() != null) {
            List<UserInteraction> interactions = userInteractionRepository.findByUser_IdAndResourceId(userId, book.getResourceId());
            for (UserInteraction interaction : interactions) {
                totalScore += scoreConfig.getScoreForAction(interaction.getAction());
            }
        }

        // Add score if favorited
        if (favoriteRepository.findByUser_IdAndBook_Id(userId, bookId).isPresent()) {
            totalScore += scoreConfig.getFavoriteScore();
        }

        // Add score from reading history
        List<ReadingHistory> historyList = readingHistoryRepository.findByUser_IdOrderByAccessTimeDesc(userId);
        for (ReadingHistory history : historyList) {
            if (history.getBook() != null && history.getBook().getId().equals(bookId)) {
                totalScore += scoreConfig.getReadScore();
            }
        }

        return totalScore;
    }

    public List<RecommendationDTO> getRecommendationsForUser(Long userId, int limit) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return Collections.emptyList();
        }

        User user = userOpt.get();
        Map<Long, Double> bookScores = new HashMap<>();
        
        addFavoriteScores(userId, bookScores);
        addReadingHistoryScores(userId, bookScores);
        addInteractionScores(userId, bookScores);

        List<RecommendationDTO> directRecommendations = bookScores.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
            .limit(limit)
            .map(entry -> {
                Optional<Book> bookOpt = bookRepository.findById(entry.getKey());
                if (bookOpt.isEmpty()) return null;
                Book book = bookOpt.get();
                return new RecommendationDTO(
                    book.getId(),
                    book.getTitle(),
                    book.getAuthor() != null ? book.getAuthor().getName() : null,
                    book.getCategory() != null ? book.getCategory().getName() : null,
                    entry.getValue()
                );
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (directRecommendations.size() < limit) {
            int needed = limit - directRecommendations.size();
            List<RecommendationDTO> contentRecs = getContentBasedRecommendations(user, needed, bookScores.keySet());
            directRecommendations.addAll(contentRecs);
        }

        return directRecommendations;
    }

    private void addFavoriteScores(Long userId, Map<Long, Double> bookScores) {
        List<Favorite> favorites = favoriteRepository.findByUser_Id(userId);
        for (Favorite favorite : favorites) {
            if (favorite.getBook() != null) {
                Long bookId = favorite.getBook().getId();
                bookScores.put(bookId, bookScores.getOrDefault(bookId, 0.0) + scoreConfig.getFavoriteScore());
            }
        }
    }

    private void addReadingHistoryScores(Long userId, Map<Long, Double> bookScores) {
        List<ReadingHistory> history = readingHistoryRepository.findByUser_IdOrderByAccessTimeDesc(userId);
        for (ReadingHistory entry : history) {
            if (entry.getBook() != null) {
                Long bookId = entry.getBook().getId();
                bookScores.put(bookId, bookScores.getOrDefault(bookId, 0.0) + scoreConfig.getReadScore());
            }
        }
    }

    private void addInteractionScores(Long userId, Map<Long, Double> bookScores) {
        List<UserInteraction> interactions = userInteractionRepository.findByUser_IdOrderByTimestampDesc(userId);
        for (UserInteraction interaction : interactions) {
            String resourceId = interaction.getResourceId();
            if (resourceId != null && "book".equalsIgnoreCase(interaction.getResourceType())) {
                Optional<Book> bookOpt = bookRepository.findByResourceId(resourceId);
                if (bookOpt.isPresent()) {
                    Long bookId = bookOpt.get().getId();
                    int actionScore = scoreConfig.getScoreForAction(interaction.getAction());
                    bookScores.put(bookId, bookScores.getOrDefault(bookId, 0.0) + actionScore);
                }
            }
        }
    }

    private List<RecommendationDTO> getContentBasedRecommendations(User user, int limit, Set<Long> excludeIds) {
        List<Favorite> favorites = favoriteRepository.findByUser_Id(user.getId());
        List<ReadingHistory> history = readingHistoryRepository.findByUser_IdOrderByAccessTimeDesc(user.getId());
        
        Set<String> preferredCategories = new HashSet<>();
        Set<String> preferredAuthors = new HashSet<>();
        Set<String> preferredSubjects = new HashSet<>();

        for (Favorite favorite : favorites) {
            if (favorite.getBook() != null) {
                if (favorite.getBook().getCategory() != null) {
                    preferredCategories.add(favorite.getBook().getCategory().getName());
                }
                if (favorite.getBook().getAuthor() != null) {
                    preferredAuthors.add(favorite.getBook().getAuthor().getName());
                }
                preferredSubjects.addAll(favorite.getBook().getSubjects());
            }
        }

        for (ReadingHistory entry : history) {
            if (entry.getBook() != null) {
                if (entry.getBook().getCategory() != null) {
                    preferredCategories.add(entry.getBook().getCategory().getName());
                }
                if (entry.getBook().getAuthor() != null) {
                    preferredAuthors.add(entry.getBook().getAuthor().getName());
                }
                preferredSubjects.addAll(entry.getBook().getSubjects());
            }
        }

        List<Book> allBooks = bookRepository.findAll();
        Map<Long, Double> contentScores = new HashMap<>();

        for (Book book : allBooks) {
            if (excludeIds.contains(book.getId())) continue;

            double score = 0.0;

            if (book.getCategory() != null && preferredCategories.contains(book.getCategory().getName())) {
                score += 2.0;
            }

            if (book.getAuthor() != null && preferredAuthors.contains(book.getAuthor().getName())) {
                score += 3.0;
            }

            for (String subject : book.getSubjects()) {
                if (preferredSubjects.contains(subject)) {
                    score += 1.5;
                }
            }

            if (score > 0) {
                contentScores.put(book.getId(), score);
            }
        }

        return contentScores.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
            .limit(limit)
            .map(entry -> {
                Optional<Book> bookOpt = bookRepository.findById(entry.getKey());
                if (bookOpt.isEmpty()) return null;
                Book book = bookOpt.get();
                return new RecommendationDTO(
                    book.getId(),
                    book.getTitle(),
                    book.getAuthor() != null ? book.getAuthor().getName() : null,
                    book.getCategory() != null ? book.getCategory().getName() : null,
                    entry.getValue()
                );
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public static class RecommendationDTO {
        private Long bookId;
        private String title;
        private String author;
        private String category;
        private Double score;

        public RecommendationDTO() {}

        public RecommendationDTO(Long bookId, String title, String author, String category, Double score) {
            this.bookId = bookId;
            this.title = title;
            this.author = author;
            this.category = category;
            this.score = score;
        }

        public Long getBookId() { return bookId; }
        public void setBookId(Long bookId) { this.bookId = bookId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
    }
}
