package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUser_IdOrderByUpdatedAtDesc(Long userId);

    /** Ownership is part of the lookup, never checked afterwards. */
    Optional<Conversation> findByIdAndUser_Id(Long id, Long userId);
}
