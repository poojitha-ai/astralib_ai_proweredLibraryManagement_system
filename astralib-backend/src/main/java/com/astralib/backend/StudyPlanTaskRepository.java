
package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudyPlanTaskRepository
        extends JpaRepository<StudyPlanTask, Long> {

    List<StudyPlanTask> findByPlanIdOrderByScheduledDateAscStartTimeAsc(
        Long planId
    );

    List<StudyPlanTask> findByPlanIdAndScheduledDateOrderByStartTimeAsc(
        Long planId,
        LocalDate scheduledDate
    );

    Optional<StudyPlanTask> findByIdAndPlanId(
        Long id,
        Long planId
    );

    long countByPlanIdAndStatus(Long planId, String status);
}
