
package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StudyGroupDocumentRepository
        extends JpaRepository<StudyGroupDocument, Long> {

    List<StudyGroupDocument> findByStudyGroupIdOrderByCreatedAtDesc(
            Long studyGroupId
    );

    Optional<StudyGroupDocument> findByIdAndStudyGroupId(
            Long id,
            Long studyGroupId
    );
}
