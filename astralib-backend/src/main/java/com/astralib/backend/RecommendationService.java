
package com.astralib.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private static final Logger logger =
            LoggerFactory.getLogger(RecommendationService.class);

    private static final int MAX_RECOMMENDATIONS = 6;
    private static final double MINIMUM_MATCH_SCORE = 3.0;

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

    /**
     * Calculates the interaction score for a particular user and book.
     * Retained for compatibility with existing functionality.
     */
    public double calculateUserBookScore(Long userId, Long bookId) {

        Optional<User> userOpt = userRepository.findById(userId);
        Optional<Book> bookOpt = bookRepository.findById(bookId);

        if (userOpt.isEmpty() || bookOpt.isEmpty()) {
            return 0.0;
        }

        Book book = bookOpt.get();
        double totalScore = 0.0;

        // User interaction scores
        if (book.getResourceId() != null) {

            List<UserInteraction> interactions =
                    userInteractionRepository.findByUser_IdAndResourceId(
                            userId,
                            book.getResourceId()
                    );

            for (UserInteraction interaction : interactions) {
                totalScore += scoreConfig.getScoreForAction(
                        interaction.getAction()
                );
            }
        }

        // Favorite score
        if (favoriteRepository.findByUser_IdAndBook_Id(
                userId, bookId).isPresent()) {

            totalScore += scoreConfig.getFavoriteScore();
        }

        // Reading history score
        List<ReadingHistory> historyList =
                readingHistoryRepository.findByUser_IdOrderByAccessTimeDesc(
                        userId
                );

        for (ReadingHistory history : historyList) {

            if (history.getBook() != null
                    && history.getBook().getId().equals(bookId)) {

                totalScore += scoreConfig.getReadScore();
            }
        }

        return totalScore;
    }

    /**
     * Generates personalized recommendations.
     *
     * Books already present in reading history or favorites are excluded
     * from the recommendation results. They are still used to understand
     * the user's interests.
     */
    public List<RecommendationDTO> getRecommendationsForUser(
            Long userId, int limit) {

        Optional<User> userOpt = userRepository.findById(userId);

        if (userOpt.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        User user = userOpt.get();

        int recommendationLimit = Math.min(limit, MAX_RECOMMENDATIONS);

        Set<Long> excludedBookIds = new HashSet<>();

        // Exclude books already read or opened.
        List<ReadingHistory> history =
                readingHistoryRepository.findByUser_IdOrderByAccessTimeDesc(
                        userId
                );

        for (ReadingHistory entry : history) {

            if (entry.getBook() != null) {
                excludedBookIds.add(entry.getBook().getId());
            }
        }

        // Exclude favorites, but retain them as interest signals.
        List<Favorite> favorites =
                favoriteRepository.findByUser_Id(userId);

        for (Favorite favorite : favorites) {

            if (favorite.getBook() != null) {
                excludedBookIds.add(favorite.getBook().getId());
            }
        }

        logger.info(
                "Generating recommendations for user {}. Excluding {} existing books.",
                userId,
                excludedBookIds.size()
        );

        return getContentBasedRecommendations(
                user,
                recommendationLimit,
                excludedBookIds
        );
    }

    /**
     * Generates recommendations using the user's reading interests.
     */
    private List<RecommendationDTO> getContentBasedRecommendations(
            User user,
            int limit,
            Set<Long> excludeIds) {

        List<Favorite> favorites =
                favoriteRepository.findByUser_Id(user.getId());

        List<ReadingHistory> history =
                readingHistoryRepository.findByUser_IdOrderByAccessTimeDesc(
                        user.getId()
                );

        Set<String> preferredCategories = new HashSet<>();
        Set<String> preferredAuthors = new HashSet<>();
        Set<String> preferredSubjects = new HashSet<>();

        // Collect interests from favorites.
        for (Favorite favorite : favorites) {

            Book book = favorite.getBook();

            if (book == null) {
                continue;
            }

            if (book.getCategory() != null
                    && book.getCategory().getName() != null) {

                preferredCategories.add(
                        book.getCategory().getName()
                );
            }

            if (book.getAuthor() != null
                    && book.getAuthor().getName() != null) {

                preferredAuthors.add(
                        book.getAuthor().getName()
                );
            }

            if (book.getSubjects() != null) {
                preferredSubjects.addAll(book.getSubjects());
            }
        }

        // Collect interests from reading history.
        for (ReadingHistory entry : history) {

            Book book = entry.getBook();

            if (book == null) {
                continue;
            }

            if (book.getCategory() != null
                    && book.getCategory().getName() != null) {

                preferredCategories.add(
                        book.getCategory().getName()
                );
            }

            if (book.getAuthor() != null
                    && book.getAuthor().getName() != null) {

                preferredAuthors.add(
                        book.getAuthor().getName()
                );
            }

            if (book.getSubjects() != null) {
                preferredSubjects.addAll(book.getSubjects());
            }
        }

        List<RecommendationDTO> recommendations = new ArrayList<>();

        List<Book> allBooks = bookRepository.findAll();

        for (Book book : allBooks) {

            if (book.getId() == null || excludeIds.contains(book.getId())) {
                continue;
            }

            double score = 0.0;

            List<String> reasons = new ArrayList<>();

            // Author match
            if (book.getAuthor() != null
                    && book.getAuthor().getName() != null
                    && preferredAuthors.contains(
                            book.getAuthor().getName())) {

                score += 3.0;

                reasons.add(
                        "Written by an author whose work you've explored"
                );
            }

            // Category match
            if (book.getCategory() != null
                    && book.getCategory().getName() != null
                    && preferredCategories.contains(
                            book.getCategory().getName())) {

                score += 1.0;

                reasons.add(
                        "Matches a category you've previously explored"
                );
            }

            // Subject matches
            Set<String> matchingSubjects = new LinkedHashSet<>();

            if (book.getSubjects() != null) {

                for (String subject : book.getSubjects()) {

                    if (subject != null
                            && preferredSubjects.contains(subject)) {

                        matchingSubjects.add(subject);
                    }
                }
            }

            if (!matchingSubjects.isEmpty()) {

                score += matchingSubjects.size() * 2.0;

                reasons.add(
                        "Related subjects: "
                                + String.join(", ", matchingSubjects)
                );
            }

            // Reject weak matches.
            if (score < MINIMUM_MATCH_SCORE) {
                continue;
            }

            String reason = String.join(". ", reasons) + ".";

            RecommendationDTO dto = new RecommendationDTO(
                    book.getId(),
                    book.getTitle(),
                    book.getAuthor() != null
                            ? book.getAuthor().getName() : null,
                    book.getCategory() != null
                            ? book.getCategory().getName() : null,
                    score
            );

            dto.setReason(reason);

            recommendations.add(dto);
        }

        // Highest relevance first.
        recommendations.sort(
                Comparator.comparing(
                        RecommendationDTO::getScore,
                        Comparator.nullsLast(
                                Comparator.reverseOrder()
                        )
                )
        );

        // Return only the requested number of recommendations.
        return recommendations.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Recommendation response object.
     */
    public static class RecommendationDTO {

        private Long bookId;
        private String title;
        private String author;
        private String category;
        private Double score;
        private String reason;

        public RecommendationDTO() {
        }

        public RecommendationDTO(
                Long bookId,
                String title,
                String author,
                String category,
                Double score) {

            this.bookId = bookId;
            this.title = title;
            this.author = author;
            this.category = category;
            this.score = score;
        }

        public Long getBookId() {
            return bookId;
        }

        public void setBookId(Long bookId) {
            this.bookId = bookId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
