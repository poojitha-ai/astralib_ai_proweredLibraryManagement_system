
package com.astralib.backend;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class StudyActivityService {

    private final StudySessionSegmentRepository segmentRepository;

    public StudyActivityService(
            StudySessionSegmentRepository segmentRepository
    ) {
        this.segmentRepository = segmentRepository;
    }

    /**
     * Returns daily study activity for a user between two dates.
     * Actual active segment time is split across calendar days.
     */
    @Transactional
    public Map<String, Object> getDailyActivity(
            Long userId,
            LocalDate from,
            LocalDate to
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (from == null || to == null) {
            throw new IllegalArgumentException(
                    "Start date and end date are required"
            );
        }

        if (to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "End date cannot be before start date"
            );
        }

        if (Duration.between(
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay()
        ).toDays() > 366) {
            throw new IllegalArgumentException(
                    "Date range cannot exceed 366 days"
            );
        }

        LocalDateTime rangeStart = from.atStartOfDay();
        LocalDateTime rangeEnd = to.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        List<StudySessionSegment> segments =
                segmentRepository.findUserSegmentsWithinRange(
                        userId,
                        rangeStart,
                        rangeEnd
                );

        Map<LocalDate, Long> dailySeconds = new LinkedHashMap<>();

        // Initialize every date, including dates with zero study time.
        for (LocalDate date = from;
             !date.isAfter(to);
             date = date.plusDays(1)) {

            dailySeconds.put(date, 0L);
        }

        // Split each active segment across calendar dates.
        for (StudySessionSegment segment : segments) {

            LocalDateTime segmentStart =
                    segment.getSegmentStartedAt();

            if (segmentStart == null) {
                continue;
            }

            // An open segment belongs to a currently active session.
            LocalDateTime segmentEnd =
                    segment.getSegmentEndedAt() != null
                            ? segment.getSegmentEndedAt()
                            : now;

            // Ignore invalid or zero-length intervals.
            if (!segmentEnd.isAfter(segmentStart)) {
                continue;
            }

            // Clip the segment to the requested date range.
            LocalDateTime clippedStart =
                    segmentStart.isAfter(rangeStart)
                            ? segmentStart
                            : rangeStart;

            LocalDateTime clippedEnd =
                    segmentEnd.isBefore(rangeEnd)
                            ? segmentEnd
                            : rangeEnd;

            if (!clippedEnd.isAfter(clippedStart)) {
                continue;
            }

            LocalDate currentDate = clippedStart.toLocalDate();

            while (!currentDate.isAfter(
                    clippedEnd.toLocalDate()
            )) {

                LocalDateTime dayStart =
                        currentDate.atStartOfDay();

                LocalDateTime nextDayStart =
                        currentDate.plusDays(1).atStartOfDay();

                LocalDateTime overlapStart =
                        clippedStart.isAfter(dayStart)
                                ? clippedStart
                                : dayStart;

                LocalDateTime overlapEnd =
                        clippedEnd.isBefore(nextDayStart)
                                ? clippedEnd
                                : nextDayStart;

                if (overlapEnd.isAfter(overlapStart)) {

                    long seconds = Duration.between(
                            overlapStart,
                            overlapEnd
                    ).getSeconds();

                    dailySeconds.merge(
                            currentDate,
                            seconds,
                            Long::sum
                    );
                }

                currentDate = currentDate.plusDays(1);
            }
        }

        List<Map<String, Object>> days = new ArrayList<>();

        long totalSeconds = 0;
        int activeDays = 0;

        for (Map.Entry<LocalDate, Long> entry
                : dailySeconds.entrySet()) {

            LocalDate date = entry.getKey();
            long seconds = entry.getValue();

            Map<String, Object> day = new LinkedHashMap<>();

            day.put("date", date.toString());
            day.put("totalSeconds", seconds);
            day.put("totalMinutes", seconds / 60);
            day.put("active", seconds > 0);

            days.add(day);

            totalSeconds += seconds;

            if (seconds > 0) {
                activeDays++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("totalSeconds", totalSeconds);
        result.put("totalMinutes", totalSeconds / 60);
        result.put("activeDays", activeDays);
        result.put("days", days);

        return result;
    }
}
