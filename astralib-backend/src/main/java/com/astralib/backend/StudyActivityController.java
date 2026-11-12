
package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/api/study-activity")
public class StudyActivityController {

    private final StudyActivityService activityService;

    public StudyActivityController(
            StudyActivityService activityService
    ) {
        this.activityService = activityService;
    }

    @GetMapping("/daily")
    public ResponseEntity<?> getDailyActivity(
            Authentication authentication,
            @RequestParam String from,
            @RequestParam String to
    ) {
        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            LocalDate startDate = LocalDate.parse(from);
            LocalDate endDate = LocalDate.parse(to);

            return ResponseEntity.ok(
                    activityService.getDailyActivity(
                            userId,
                            startDate,
                            endDate
                    )
            );

        } catch (DateTimeParseException exception) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "Dates must use YYYY-MM-DD format"
                    )
            );

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", exception.getMessage())
            );
        }
    }
}
