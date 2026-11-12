
package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/api/study-plans")
public class StudyPlannerController {

    private final StudyPlanService studyPlanService;
    private final StudyPlanTaskService studyPlanTaskService;
    private final StudyPlannerAIService studyPlannerAIService;

    public StudyPlannerController(
            StudyPlanService studyPlanService,
            StudyPlanTaskService studyPlanTaskService,
            StudyPlannerAIService studyPlannerAIService
    ) {
        this.studyPlanService = studyPlanService;
        this.studyPlanTaskService = studyPlanTaskService;
        this.studyPlannerAIService = studyPlannerAIService;
    }

    // 1. GENERATE TIMETABLE PREVIEW
    // Does not save anything to the database.

    @PostMapping("/preview")
    public ResponseEntity<?> generatePreview(
            Authentication authentication,
            @RequestBody Map<String, Object> request
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            return ResponseEntity.ok(
                    studyPlannerAIService.generatePreview(request)
            );
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    // 2. CREATE STUDY PLAN

    @PostMapping
    public ResponseEntity<?> createPlan(
            Authentication authentication,
            @RequestBody Map<String, Object> request
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            return ResponseEntity.ok(
                    studyPlanService.createPlan(userId, request)
            );
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    // 3. GET ALL STUDY PLANS

    @GetMapping
    public ResponseEntity<?> getMyPlans(
            Authentication authentication
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        return ResponseEntity.ok(
                studyPlanService.getMyPlans(userId)
        );
    }

    // 4. GET ONE STUDY PLAN

    @GetMapping("/{planId}")
    public ResponseEntity<?> getPlan(
            Authentication authentication,
            @PathVariable Long planId
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            return ResponseEntity.ok(
                    studyPlanService.getPlanById(planId, userId)
            );
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    // 5. COMPLETE STUDY PLAN

    @PatchMapping("/{planId}/complete")
    public ResponseEntity<?> completePlan(
            Authentication authentication,
            @PathVariable Long planId
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            return ResponseEntity.ok(
                    studyPlanService.completePlan(planId, userId)
            );
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    // 6. APPROVE AND SAVE GENERATED TASKS

    @PostMapping("/{planId}/tasks/approve")
    public ResponseEntity<?> approveTasks(
            Authentication authentication,
            @PathVariable Long planId,
            @RequestBody Map<String, Object> request
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            return ResponseEntity.ok(
                    studyPlanTaskService.saveApprovedTasks(
                            userId,
                            planId,
                            request
                    )
            );
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    // 7. GET ALL TASKS OR TASKS FOR A PARTICULAR DATE

    @GetMapping("/{planId}/tasks")
    public ResponseEntity<?> getTasks(
            Authentication authentication,
            @PathVariable Long planId,
            @RequestParam(required = false) String date
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            if (date != null && !date.isBlank()) {
                LocalDate scheduledDate = LocalDate.parse(date);

                return ResponseEntity.ok(
                        studyPlanTaskService.getTasksByDate(
                                userId,
                                planId,
                                scheduledDate
                        )
                );
            }

            return ResponseEntity.ok(
                    studyPlanTaskService.getTasks(userId, planId)
            );

        } catch (DateTimeParseException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error",
                            "Invalid date. Use YYYY-MM-DD."
                    ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    // 8. UPDATE TASK STATUS

    @PatchMapping("/{planId}/tasks/{taskId}/status")
    public ResponseEntity<?> updateTaskStatus(
            Authentication authentication,
            @PathVariable Long planId,
            @PathVariable Long taskId,
            @RequestBody Map<String, Object> request
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            String status = request == null
                    ? null
                    : (String) request.get("status");

            return ResponseEntity.ok(
                    studyPlanTaskService.updateTaskStatus(
                            userId,
                            planId,
                            taskId,
                            status
                    )
            );
        } catch (ClassCastException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Status must be a string."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    // 9. DELETE PENDING TASKS
    // Completed tasks are preserved.

    @DeleteMapping("/{planId}/tasks/pending")
    public ResponseEntity<?> deletePendingTasks(
            Authentication authentication,
            @PathVariable Long planId
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            return ResponseEntity.ok(
                    studyPlanTaskService.deletePendingTasks(
                            userId,
                            planId
                    )
            );
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", exception.getMessage()));
        }
    }
}

