
package com.astralib.backend;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudyPlannerAIService {

    private static final int TOPIC_MINUTES = 45;
    private static final int REVISION_MINUTES = 45;
    private static final int MOCK_TEST_MINUTES = 60;

    /**
     * Generates a timetable preview.
     *
     * This method does not save anything to the database.
     */
    public Map<String, Object> generatePreview(Map<String, Object> request) {

        LocalDate startDate = parseDate(request, "startDate");
        LocalDate endDate = parseDate(request, "endDate");

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "End date cannot be before start date."
            );
        }

        int dailyTargetMinutes = getInteger(
                request,
                "dailyTargetMinutes"
        );

        if (dailyTargetMinutes <= 0 || dailyTargetMinutes > 1440) {
            throw new IllegalArgumentException(
                    "Daily target must be between 1 and 1440 minutes."
            );
        }

        LocalTime availableFrom = parseTime(
                request,
                "availableFrom"
        );

        LocalTime availableUntil = parseTime(
                request,
                "availableUntil"
        );

        if (!availableUntil.isAfter(availableFrom)) {
            throw new IllegalArgumentException(
                    "Available-until time must be after available-from time."
            );
        }

        int availableMinutes =
                (int) java.time.Duration.between(
                        availableFrom,
                        availableUntil
                ).toMinutes();

        int dailyCapacity = Math.min(
                dailyTargetMinutes,
                availableMinutes
        );

        if (dailyCapacity < TOPIC_MINUTES) {
            throw new IllegalArgumentException(
                    "Your available study time is too short. " +
                    "Please provide at least 45 minutes per day."
            );
        }

        List<SubjectInput> subjects = parseSubjects(
                request,
                startDate,
                endDate
        );

        List<TaskDraft> pendingTasks = createTasks(
                subjects,
                startDate
        );

        pendingTasks.sort(
                Comparator.comparing(TaskDraft::deadline)
                        .thenComparing(TaskDraft::preferredDate)
                        .thenComparing(TaskDraft::examDate)
        );

        Map<LocalDate, Integer> dailyUsage = new HashMap<>();

        List<Map<String, Object>> scheduledTasks = new ArrayList<>();

        for (TaskDraft task : pendingTasks) {

            LocalDate scheduledDate = findAvailableDate(
                    task,
                    dailyUsage,
                    dailyCapacity
            );

            if (scheduledDate == null) {
                throw new IllegalArgumentException(
                        "Not enough available study time to schedule all tasks. " +
                        "Please extend your study period, increase your daily " +
                        "availability, or reduce the number of topics."
                );
            }

            int usedMinutes = dailyUsage.getOrDefault(
                    scheduledDate,
                    0
            );

            LocalTime startTime = availableFrom.plusMinutes(
                    usedMinutes
            );

            LocalTime endTime = startTime.plusMinutes(
                    task.durationMinutes()
            );

            dailyUsage.put(
                    scheduledDate,
                    usedMinutes + task.durationMinutes()
            );

            Map<String, Object> taskResponse = new LinkedHashMap<>();

            taskResponse.put("title", task.title());
            taskResponse.put("subject", task.subject());
            taskResponse.put("topic", task.topic());
            taskResponse.put("description", task.description());
            taskResponse.put("taskType", task.taskType());
            taskResponse.put("scheduledDate", scheduledDate.toString());
            taskResponse.put("startTime", startTime.toString());
            taskResponse.put("endTime", endTime.toString());
            taskResponse.put("estimatedMinutes", task.durationMinutes());
            taskResponse.put("status", "PENDING");

            scheduledTasks.add(taskResponse);
        }

        scheduledTasks.sort(
        Comparator.comparing(
                (Map<String, Object> task) ->
                        LocalDate.parse(
                                (String) task.get("scheduledDate")
                        )
        ).thenComparing(
                task -> LocalTime.parse(
                        (String) task.get("startTime")
                )
        )
);

        int totalScheduledMinutes = scheduledTasks.stream()
                .mapToInt(task -> (Integer) task.get("estimatedMinutes"))
                .sum();

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("startDate", startDate.toString());
        response.put("endDate", endDate.toString());
        response.put("dailyTargetMinutes", dailyTargetMinutes);
        response.put("totalScheduledMinutes", totalScheduledMinutes);
        response.put("totalTasks", scheduledTasks.size());
        response.put("tasks", scheduledTasks);

        return response;
    }

    /**
     * Reads and validates subject details.
     */
    private List<SubjectInput> parseSubjects(
            Map<String, Object> request,
            LocalDate startDate,
            LocalDate endDate
    ) {

        Object subjectsObject = request.get("subjects");

        if (!(subjectsObject instanceof List<?> subjectList)
                || subjectList.isEmpty()) {

            throw new IllegalArgumentException(
                    "At least one subject is required."
            );
        }

        List<SubjectInput> subjects = new ArrayList<>();

        for (Object item : subjectList) {

            if (!(item instanceof Map<?, ?> subjectMap)) {
                throw new IllegalArgumentException(
                        "Invalid subject details."
                );
            }

            String name = requiredString(
                    subjectMap.get("name"),
                    "Subject name"
            );

            String examDateValue = requiredString(
                    subjectMap.get("examDate"),
                    "Exam date for " + name
            );

            LocalDate examDate;

            try {
                examDate = LocalDate.parse(examDateValue);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(
                        "Invalid exam date for " + name +
                        ". Use YYYY-MM-DD."
                );
            }

            if (examDate.isBefore(startDate)
                    || examDate.isAfter(endDate)) {

                throw new IllegalArgumentException(
                        "Exam date for " + name +
                        " must be within the study period."
                );
            }

            Object topicsObject = subjectMap.get("topics");

            if (!(topicsObject instanceof List<?> topicList)
                    || topicList.isEmpty()) {

                throw new IllegalArgumentException(
                        "Add at least one topic for " + name + "."
                );
            }

            List<String> topics = new ArrayList<>();

            for (Object topic : topicList) {

                String topicName = requiredString(
                        topic,
                        "Topic name"
                );

                topics.add(topicName);
            }

            subjects.add(
                    new SubjectInput(name, examDate, topics)
            );
        }

        // Subjects with earlier exams get higher priority.
        subjects.sort(
                Comparator.comparing(SubjectInput::examDate)
        );

        return subjects;
    }

    /**
     * Creates topic, revision and mock-test tasks.
     */
    private List<TaskDraft> createTasks(
            List<SubjectInput> subjects,
            LocalDate startDate
    ) {

        List<TaskDraft> tasks = new ArrayList<>();

        for (SubjectInput subject : subjects) {

            LocalDate examDate = subject.examDate();

            LocalDate topicEndDate = examDate.minusDays(3);

            // If the exam is close, use the remaining days
            // instead of creating an impossible topic deadline.
            if (topicEndDate.isBefore(startDate)) {
                topicEndDate = examDate.minusDays(1);
            }

            long topicStudyDays = Math.max(
                    1,
                    java.time.temporal.ChronoUnit.DAYS.between(
                            startDate,
                            topicEndDate
                    ) + 1
            );

            List<String> topics = subject.topics();

            for (int i = 0; i < topics.size(); i++) {

                String topic = topics.get(i);

                LocalDate preferredDate = startDate.plusDays(
                        (topicStudyDays * i) / topics.size()
                );

                if (preferredDate.isAfter(topicEndDate)) {
                    preferredDate = topicEndDate;
                }

                tasks.add(
                        new TaskDraft(
                                "Study: " + topic,
                                subject.name(),
                                topic,
                                "Study and understand the topic: " + topic,
                                "TOPIC",
                                preferredDate,
                                topicEndDate,
                                examDate,
                                TOPIC_MINUTES
                        )
                );
            }

            // Revision session: two days before the exam.
            LocalDate revisionDate = examDate.minusDays(2);

            if (revisionDate.isBefore(startDate)) {
                revisionDate = startDate;
            }

            tasks.add(
                    new TaskDraft(
                            "Revision: " + subject.name(),
                            subject.name(),
                            null,
                            "Revise important concepts, notes and " +
                            "previously studied topics.",
                            "REVISION",
                            revisionDate,
                            revisionDate,
                            examDate,
                            REVISION_MINUTES
                    )
            );

            // Mock test: one day before the exam.
            LocalDate mockDate = examDate.minusDays(1);

            if (mockDate.isBefore(startDate)) {
                mockDate = startDate;
            }

            tasks.add(
                    new TaskDraft(
                            "Mock Test: " + subject.name(),
                            subject.name(),
                            null,
                            "Attempt a practice test and review " +
                            "your answers and mistakes.",
                            "MOCK_TEST",
                            mockDate,
                            mockDate,
                            examDate,
                            MOCK_TEST_MINUTES
                    )
            );
        }

        return tasks;
    }

    /**
     * Finds a date where the task fits without exceeding
     * the daily target or overlapping another task.
     */
    private LocalDate findAvailableDate(
            TaskDraft task,
            Map<LocalDate, Integer> dailyUsage,
            int dailyCapacity
    ) {

        if (task.durationMinutes() > dailyCapacity) {
            return null;
        }

        LocalDate date = task.preferredDate();

        while (!date.isAfter(task.deadline())) {

            int usedMinutes = dailyUsage.getOrDefault(
                    date,
                    0
            );

            if (usedMinutes + task.durationMinutes()
                    <= dailyCapacity) {

                return date;
            }

            date = date.plusDays(1);
        }

        return null;
    }

    private LocalDate parseDate(
            Map<String, Object> request,
            String field
    ) {

        String value = requiredString(
                request.get(field),
                field
        );

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Invalid " + field + ". Use YYYY-MM-DD."
            );
        }
    }

    private LocalTime parseTime(
            Map<String, Object> request,
            String field
    ) {

        String value = requiredString(
                request.get(field),
                field
        );

        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Invalid " + field + ". Use HH:mm."
            );
        }
    }

    private int getInteger(
            Map<String, Object> request,
            String field
    ) {

        Object value = request.get(field);

        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    field + " must be a number."
            );
        }

        return number.intValue();
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

    private record SubjectInput(
            String name,
            LocalDate examDate,
            List<String> topics
    ) {}

    private record TaskDraft(
            String title,
            String subject,
            String topic,
            String description,
            String taskType,
            LocalDate preferredDate,
            LocalDate deadline,
            LocalDate examDate,
            int durationMinutes
    ) {}
}

