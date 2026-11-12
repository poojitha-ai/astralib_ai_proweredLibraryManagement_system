
package com.astralib.backend;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class StudyPlanTaskService {

    private final StudyPlanTaskRepository taskRepository;
    private final StudyPlanRepository planRepository;

    public StudyPlanTaskService(
            StudyPlanTaskRepository taskRepository,
            StudyPlanRepository planRepository
    ) {
        this.taskRepository = taskRepository;
        this.planRepository = planRepository;
    }

    /**
     * Saves tasks after the student approves the generated timetable.
     */
    @Transactional
    public Map<String, Object> saveApprovedTasks(
            Long userId,
            Long planId,
            Map<String, Object> request
    ) {

        StudyPlan plan = findUserPlan(userId, planId);

        Object tasksObject = request.get("tasks");

        if (!(tasksObject instanceof List<?> taskList)
                || taskList.isEmpty()) {

            throw new IllegalArgumentException(
                    "At least one task is required."
            );
        }

        List<StudyPlanTask> tasksToSave = new ArrayList<>();

        Set<String> occupiedSlots = new HashSet<>();

        for (Object item : taskList) {

            if (!(item instanceof Map<?, ?> taskData)) {
                throw new IllegalArgumentException(
                        "Invalid task details."
                );
            }

            StudyPlanTask task = createTask(
                    planId,
                    taskData
            );

            validateTaskDates(task, plan);

            String slot = task.getScheduledDate() + "|" +
                    task.getStartTime() + "|" +
                    task.getEndTime();

            // Check overlaps with previously added tasks.
            for (StudyPlanTask existing : tasksToSave) {

                if (existing.getScheduledDate()
                        .equals(task.getScheduledDate())) {

                    boolean overlaps =
                            task.getStartTime().isBefore(
                                    existing.getEndTime()
                            )
                            &&
                            task.getEndTime().isAfter(
                                    existing.getStartTime()
                            );

                    if (overlaps) {
                        throw new IllegalArgumentException(
                                "Tasks cannot overlap on " +
                                task.getScheduledDate() + "."
                        );
                    }
                }
            }

            if (!occupiedSlots.add(slot)) {
                throw new IllegalArgumentException(
                        "Duplicate task time slot."
                );
            }

            tasksToSave.add(task);
        }

        List<StudyPlanTask> savedTasks =
                taskRepository.saveAll(tasksToSave);

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("message", "Study plan tasks saved successfully.");
        response.put("planId", planId);
        response.put("totalTasks", savedTasks.size());
        response.put("tasks", savedTasks.stream()
                .map(this::toTaskResponse)
                .toList());

        return response;
    }

    /**
     * Retrieves all tasks belonging to a student's plan.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTasks(
            Long userId,
            Long planId
    ) {

        findUserPlan(userId, planId);

        return taskRepository
                .findByPlanIdOrderByScheduledDateAscStartTimeAsc(planId)
                .stream()
                .map(this::toTaskResponse)
                .toList();
    }

    /**
     * Retrieves tasks for a particular date.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTasksByDate(
            Long userId,
            Long planId,
            LocalDate date
    ) {

        findUserPlan(userId, planId);

        return taskRepository
                .findByPlanIdAndScheduledDateOrderByStartTimeAsc(
                        planId,
                        date
                )
                .stream()
                .map(this::toTaskResponse)
                .toList();
    }

    /**
     * Updates a task's status.
     */
    @Transactional
    public Map<String, Object> updateTaskStatus(
            Long userId,
            Long planId,
            Long taskId,
            String newStatus
    ) {

        findUserPlan(userId, planId);

        if (newStatus == null || newStatus.isBlank()) {
            throw new IllegalArgumentException(
                    "Task status is required."
            );
        }

        String status = newStatus.trim().toUpperCase();

        if (!Set.of(
                "PENDING",
                "IN_PROGRESS",
                "COMPLETED"
        ).contains(status)) {

            throw new IllegalArgumentException(
                    "Invalid task status."
            );
        }

        StudyPlanTask task = taskRepository
                .findByIdAndPlanId(taskId, planId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Task not found."
                ));

        task.setStatus(status);

        if ("COMPLETED".equals(status)) {
            task.setCompletedAt(LocalDateTime.now());
        } else {
            task.setCompletedAt(null);
        }

        StudyPlanTask savedTask = taskRepository.save(task);

        return toTaskResponse(savedTask);
    }

    /**
     * Deletes all pending tasks from a plan.
     * Completed tasks are preserved.
     */
    @Transactional
    public Map<String, Object> deletePendingTasks(
            Long userId,
            Long planId
    ) {

        findUserPlan(userId, planId);

        List<StudyPlanTask> tasks =
                taskRepository.findByPlanIdOrderByScheduledDateAscStartTimeAsc(
                        planId
                );

        List<StudyPlanTask> pendingTasks = tasks.stream()
                .filter(task -> !"COMPLETED".equals(task.getStatus()))
                .toList();

        taskRepository.deleteAll(pendingTasks);

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("message", "Pending tasks deleted successfully.");
        response.put("deletedTasks", pendingTasks.size());

        return response;
    }

    /**
     * Converts approved preview data into a database entity.
     */
    private StudyPlanTask createTask(
            Long planId,
            Map<?, ?> data
    ) {

        StudyPlanTask task = new StudyPlanTask();

        task.setPlanId(planId);

        task.setTitle(
                requiredString(data.get("title"), "Task title")
        );

        task.setSubject(
                requiredString(data.get("subject"), "Subject")
        );

        task.setTopic(
                optionalString(data.get("topic"))
        );

        task.setDescription(
                optionalString(data.get("description"))
        );

        String taskType = requiredString(
                data.get("taskType"),
                "Task type"
        ).toUpperCase();

        if (!Set.of("TOPIC", "REVISION", "MOCK_TEST")
                .contains(taskType)) {

            throw new IllegalArgumentException(
                    "Invalid task type: " + taskType
            );
        }

        task.setTaskType(taskType);

        task.setScheduledDate(
                parseDate(data.get("scheduledDate"))
        );

        task.setStartTime(
                parseTime(data.get("startTime"))
        );

        task.setEndTime(
                parseTime(data.get("endTime"))
        );

        Object minutesObject = data.get("estimatedMinutes");

        if (!(minutesObject instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Estimated minutes must be a number."
            );
        }

        int estimatedMinutes = number.intValue();

        if (estimatedMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Estimated minutes must be greater than zero."
            );
        }

        long actualMinutes = Duration.between(
                task.getStartTime(),
                task.getEndTime()
        ).toMinutes();

        if (actualMinutes != estimatedMinutes) {
            throw new IllegalArgumentException(
                    "Estimated minutes must match the task duration."
            );
        }

        task.setEstimatedMinutes(estimatedMinutes);
        task.setStatus("PENDING");

        return task;
    }

    /**
     * Validates task dates against the parent study plan.
     */
    private void validateTaskDates(
            StudyPlanTask task,
            StudyPlan plan
    ) {

        if (task.getEndTime().isBefore(task.getStartTime())
                || task.getEndTime().equals(task.getStartTime())) {

            throw new IllegalArgumentException(
                    "Task end time must be after start time."
            );
        }

        if (task.getScheduledDate().isBefore(plan.getStartDate())
                || task.getScheduledDate().isAfter(plan.getEndDate())) {

            throw new IllegalArgumentException(
                    "Task date must be within the study plan dates."
            );
        }
    }

    /**
     * Ensures that the requested plan belongs to the logged-in user.
     */
    private StudyPlan findUserPlan(
            Long userId,
            Long planId
    ) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "User ID is required."
            );
        }

        return planRepository
                .findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Study plan not found."
                ));
    }

    private Map<String, Object> toTaskResponse(
            StudyPlanTask task
    ) {

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("id", task.getId());
        response.put("planId", task.getPlanId());
        response.put("title", task.getTitle());
        response.put("subject", task.getSubject());
        response.put("topic", task.getTopic());
        response.put("description", task.getDescription());
        response.put("taskType", task.getTaskType());
        response.put("scheduledDate", task.getScheduledDate());
        response.put("startTime", task.getStartTime());
        response.put("endTime", task.getEndTime());
        response.put("estimatedMinutes", task.getEstimatedMinutes());
        response.put("status", task.getStatus());
        response.put("completedAt", task.getCompletedAt());
        response.put("createdAt", task.getCreatedAt());

        return response;
    }

    private LocalDate parseDate(Object value) {

        try {
            return LocalDate.parse(
                    requiredString(value, "Scheduled date")
            );
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Invalid scheduled date. Use YYYY-MM-DD."
            );
        }
    }

    private LocalTime parseTime(Object value) {

        try {
            return LocalTime.parse(
                    requiredString(value, "Task time")
            );
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Invalid task time. Use HH:mm."
            );
        }
    }

    private String requiredString(
            Object value,
            String field
    ) {

        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(
                    field + " is required."
            );
        }

        return value.toString().trim();
    }

    private String optionalString(Object value) {

        if (value == null) {
            return null;
        }

        String result = value.toString().trim();

        return result.isEmpty() ? null : result;
    }
}
