package com.astralib.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SavedResearchService {

    @Autowired
    private SavedResearchRepository savedResearchRepository;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    // =========================================================
    // SAVE
    // =========================================================

    @Transactional
    public Map<String, Object> save(
            Long userId,
            Map<String, Object> request
    ) {

        User user =
                getCurrentUser(
                        userId
                );

        if (request == null) {
            throw new IllegalArgumentException(
                    "Request body is required"
            );
        }

        String question =
                readString(
                        request.get("question")
                );

        String answer =
                readString(
                        request.get("answer")
                );

        String title =
                readString(
                        request.get("title")
                );

        if (question.isBlank()) {
            throw new IllegalArgumentException(
                    "Question cannot be empty"
            );
        }

        if (answer.isBlank()) {
            throw new IllegalArgumentException(
                    "Answer cannot be empty"
            );
        }

        if (title.isBlank()) {
            title = createTitleFromQuestion(
                    question
            );
        }

        Object citations =
                request.get("citations");

        String citationsJson =
                writeCitations(
                        citations
                );

        SavedResearch savedResearch =
                new SavedResearch();

        savedResearch.setUser(
                user
        );

        savedResearch.setTitle(
                title
        );

        savedResearch.setQuestion(
                question
        );

        savedResearch.setAnswer(
                answer
        );

        savedResearch.setCitationsJson(
                citationsJson
        );

        SavedResearch saved =
                savedResearchRepository.save(
                        savedResearch
                );

        return toMap(
                saved
        );
    }

    // =========================================================
    // LIST CURRENT USER'S SAVED RESEARCH
    // =========================================================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAll(
            Long userId
    ) {

        User user =
                getCurrentUser(
                        userId
                );

        List<SavedResearch> records =
                savedResearchRepository
                        .findByUser_IdOrderByCreatedAtDesc(
                                user.getId()
                        );

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (SavedResearch record : records) {
            result.add(
                    toMap(
                            record
                    )
            );
        }

        return result;
    }

    // =========================================================
    // GET ONE
    // =========================================================

    @Transactional(readOnly = true)
    public Map<String, Object> getById(
            Long userId,
            Long id
    ) {

        User user =
                getCurrentUser(
                        userId
                );

        SavedResearch record =
                savedResearchRepository
                        .findByIdAndUser_Id(
                                id,
                                user.getId()
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Saved research not found"
                                        )
                        );

        return toMap(
                record
        );
    }

    // =========================================================
    // DELETE ONE
    // =========================================================

    @Transactional
    public void delete(
            Long userId,
            Long id
    ) {

        User user =
                getCurrentUser(
                        userId
                );

        SavedResearch record =
                savedResearchRepository
                        .findByIdAndUser_Id(
                                id,
                                user.getId()
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Saved research not found"
                                        )
                        );

        savedResearchRepository.delete(
                record
        );
    }

    // =========================================================
    // CURRENT LOGGED-IN USER
    // =========================================================

    private User getCurrentUser(
            Long userId
    ) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "Authenticated user is required"
            );
        }

        return userRepository
                .findById(
                        userId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Authenticated user not found"
                                )
                );
    }

    // =========================================================
    // RESPONSE
    // =========================================================

    private Map<String, Object> toMap(
            SavedResearch record
    ) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "id",
                record.getId()
        );

        response.put(
                "title",
                record.getTitle()
        );

        response.put(
                "question",
                record.getQuestion()
        );

        response.put(
                "answer",
                record.getAnswer()
        );

        response.put(
                "citations",
                readCitations(
                        record.getCitationsJson()
                )
        );

        response.put(
                "createdAt",
                record.getCreatedAt()
        );

        return response;
    }

    // =========================================================
    // CITATIONS JSON
    // =========================================================

    private String writeCitations(
            Object citations
    ) {

        try {

            if (citations == null) {
                return "[]";
            }

            return objectMapper
                    .writeValueAsString(
                            citations
                    );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Could not save citations",
                    e
            );
        }
    }

    private List<Map<String, Object>> readCitations(
            String json
    ) {

        if (
                json == null
                        || json.isBlank()
        ) {
            return new ArrayList<>();
        }

        try {

            return objectMapper.readValue(
                    json,
                    new TypeReference<
                            List<Map<String, Object>>
                            >() {
                    }
            );

        } catch (Exception e) {

            return new ArrayList<>();
        }
    }

    // =========================================================
    // SMALL HELPERS
    // =========================================================

    private String readString(
            Object value
    ) {

        return value == null
                ? ""
                : value.toString().trim();
    }

    private String createTitleFromQuestion(
            String question
    ) {

        String title =
                question.trim();

        if (title.length() > 80) {
            title =
                    title.substring(
                            0,
                            80
                    )
                            + "...";
        }

        return title;
    }
}
