package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversation_IdOrderByCreatedAtAscIdAsc(Long conversationId);

    @Query("""
            select count(m)
            from ChatMessage m
            where m.conversation.id = :conversationId
            """)
    long countInConversation(@Param("conversationId") Long conversationId);

    void deleteByConversation_Id(Long conversationId);
}
