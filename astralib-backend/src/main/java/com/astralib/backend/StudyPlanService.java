
package com.astralib.backend;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudyPlanService {

    private final StudyPlanRepository studyPlanRepository;
    private final UserRepository userRepository;

    public StudyPlanService(
            StudyPlanRepository studyPlanRepository,
            UserRepository userRepository
    ) {
        this.studyPlanRepository = studyPlanRepository;
        this.userRepository = userRepository;
    }

    // =========================================================
    // CREATE STUDY PLAN
    // =========================================================

    @Transactional
    public Map<String, Object> createPlan(
            Long userId,
            Map<String, Object> request
    ) {

        validateUser(userId);

        if (request == null) {
            throw new IllegalArgumentException(
                    "Study plan details are required"
            );
        }

        String title = getString(request, "title");
        String description = getString(request, "description");

        if (title.isBlank()) {
            throw new IllegalArgumentException(
                    "Study plan title is required"
            );
        }

        LocalDate startDate = getDate(request, "startDate");
        LocalDate endDate = getDate(request, "endDate");

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException(
                    "Start date and end date are required"
            );
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "End date cannot be before start date"
            );
        }

        Integer dailyTargetMinutes =
                getInteger(request, "dailyTargetMinutes");

        if (
                dailyTargetMinutes == null
                        || dailyTargetMinutes <= 0
                        || dailyTargetMinutes > 1440
        ) {
            throw new IllegalArgumentException(
                    "Daily target must be between 1 and 1440 minutes"
            );
        }

        StudyPlan plan = new StudyPlan();

        plan.setUserId(userId);
        plan.setTitle(title);
        plan.setDescription(description);
        plan.setStartDate(startDate);
        plan.setEndDate(endDate);
        plan.setDailyTargetMinutes(dailyTargetMinutes);
        plan.setStatus("ACTIVE");

        StudyPlan savedPlan =
                studyPlanRepository.save(plan);

        return toPlanResponse(savedPlan);
    }

    // =========================================================
    // GET ALL PLANS FOR CURRENT USER
    // =========================================================

    public List<Map<String, Object>> getMyPlans(Long userId) {

        validateUser(userId);

        List<StudyPlan> plans =
                studyPlanRepository
                        .findByUserIdOrderByCreatedAtDesc(userId);

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (StudyPlan plan : plans) {
            result.add(toPlanResponse(plan));
        }

        return result;
    }

    // =========================================================
    // GET ONE PLAN
    // =========================================================

    public Map<String, Object> getPlanById(
            Long planId,
            Long userId
    ) {

        validateUser(userId);

        StudyPlan plan =
                findUserPlan(planId, userId);

        return toPlanResponse(plan);
    }

    // =========================================================
    // COMPLETE STUDY PLAN
    // =========================================================

    @Transactional
    public Map<String, Object> completePlan(
            Long planId,
            Long userId
    ) {

        validateUser(userId);

        StudyPlan plan =
                findUserPlan(planId, userId);

        if ("COMPLETED".equals(plan.getStatus())) {
            throw new IllegalArgumentException(
                    "Study plan is already completed"
            );
        }

        plan.setStatus("COMPLETED");

        StudyPlan savedPlan =
                studyPlanRepository.save(plan);

        return toPlanResponse(savedPlan);
    }

    // =========================================================
    // FIND PLAN OWNED BY USER
    // =========================================================

    private StudyPlan findUserPlan(
            Long planId,
            Long userId
    ) {

        return studyPlanRepository
                .findByIdAndUserId(planId, userId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Study plan not found"
                        )
                );
    }

    // =========================================================
    // PLAN RESPONSE
    // =========================================================

    private Map<String, Object> toPlanResponse(
            StudyPlan plan
    ) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put("id", plan.getId());
        response.put("title", plan.getTitle());
        response.put("description", plan.getDescription());
        response.put("startDate", plan.getStartDate());
        response.put("endDate", plan.getEndDate());
        response.put(
                "dailyTargetMinutes",
                plan.getDailyTargetMinutes()
        );
        response.put("status", plan.getStatus());
        response.put("createdAt", plan.getCreatedAt());
        response.put("updatedAt", plan.getUpdatedAt());

        return response;
    }

    // =========================================================
    // USER VALIDATION
    // =========================================================

    private void validateUser(Long userId) {

        if (
                userId == null
                        || !userRepository.existsById(userId)
        ) {
            throw new IllegalArgumentException(
                    "Authenticated user not found"
            );
        }
    }

    // =========================================================
    // REQUEST HELPERS
    // =========================================================

    private String getString(
            Map<String, Object> request,
            String key
    ) {

        Object value = request.get(key);

        return value == null
                ? ""
                : value.toString().trim();
    }

    private LocalDate getDate(
            Map<String, Object> request,
            String key
    ) {

        Object value = request.get(key);

        if (value == null || value.toString().isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value.toString().trim());
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Invalid date format for " + key
                            + ". Expected YYYY-MM-DD"
            );
        }
    }

    private Integer getInteger(
            Map<String, Object> request,
            String key
    ) {

        Object value = request.get(key);

        if (value == null) {
            return null;
        }

        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid number for " + key
            );
        }
    }
}
