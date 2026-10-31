package com.astralib.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The conversational academic companion.
 *
 * This is the one place that decides what a student's message needs:
 * ordinary conversation, encouragement and guidance, a catalogue
 * recommendation, a concept explanation, or an answer grounded in indexed
 * library passages. It reuses the existing retrieval, embedding, catalogue
 * and LLM services; nothing here creates a second search stack.
 *
 * A message is only ever presented as library-grounded when real passages
 * were retrieved and passed to the model. Everything else is stated as
 * general knowledge, so a citation in a chat is always a citation that
 * exists.
 */
@Service
public class ConversationService {

    private static final Logger logger =
            LoggerFactory.getLogger(ConversationService.class);

    /** Passages must clear the same relevance floor the RAG endpoint uses. */
    private static final double MIN_PASSAGE_SCORE = 0.55;

    /** How many prior turns are sent verbatim to the model. */
    private static final int CONTEXT_WINDOW = 12;

    /** Messages kept in full before older ones are folded into a digest. */
    private static final int DIGEST_THRESHOLD = 20;

    private static final int MAX_RECOMMENDATIONS = 4;

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QueryUnderstandingService queryUnderstandingService;

    @Autowired
    private CatalogueRecommendationService catalogueRecommendationService;

    @Autowired
    private SemanticSearchService semanticSearchService;

    @Autowired
    private LlmService llmService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /* =========================================================
     * DTOs
     * ========================================================= */

    public record Citation(
            String chunkId,
            String documentId,
            Long bookId,
            String title,
            String author,
            String section,
            Integer chunkIndex,
            double score
    ) {
    }

    public record MessageView(
            Long id,
            String role,
            String content,
            String intent,
            boolean grounded,
            List<Citation> citations,
            List<CatalogueRecommendationService.BookRecommendation> recommendations,
            String createdAt
    ) {
    }

    public record ConversationView(
            Long id,
            String title,
            String preview,
            long messageCount,
            String updatedAt
    ) {
    }

    public record ConversationDetail(
            Long id,
            String title,
            List<MessageView> messages
    ) {
    }

    public record ChatResult(
            Long conversationId,
            String title,
            String intent,
            boolean grounded,
            MessageView userMessage,
            MessageView assistantMessage
    ) {
    }

    /* =========================================================
     * CONVERSATIONS
     * ========================================================= */

    @Transactional
    public ConversationView create(Long userId) {

        Conversation conversation = new Conversation();
        conversation.setUser(userRepository.getReferenceById(userId));
        conversation.setTitle("New chat");

        conversationRepository.save(conversation);

        return new ConversationView(
                conversation.getId(),
                conversation.getTitle(),
                "",
                0,
                format(conversation.getUpdatedAt())
        );
    }

    @Transactional(readOnly = true)
    public List<ConversationView> list(Long userId) {

        List<ConversationView> views = new ArrayList<>();

        for (Conversation conversation :
                conversationRepository.findByUser_IdOrderByUpdatedAtDesc(userId)) {

            List<ChatMessage> messages =
                    chatMessageRepository.findByConversation_IdOrderByCreatedAtAscIdAsc(
                            conversation.getId()
                    );

            String preview = "";

            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage message = messages.get(i);
                if (message.isUserMessage()) {
                    preview = truncate(message.getContent(), 90);
                    break;
                }
            }

            views.add(
                    new ConversationView(
                            conversation.getId(),
                            conversation.getTitle(),
                            preview,
                            messages.size(),
                            format(conversation.getUpdatedAt())
                    )
            );
        }

        return views;
    }

    @Transactional(readOnly = true)
    public ConversationDetail get(Long userId, Long conversationId) {

        Conversation conversation = owned(userId, conversationId);

        List<MessageView> messages = new ArrayList<>();

        for (ChatMessage message :
                chatMessageRepository.findByConversation_IdOrderByCreatedAtAscIdAsc(
                        conversation.getId()
                )) {
            messages.add(toView(message));
        }

        return new ConversationDetail(
                conversation.getId(),
                conversation.getTitle(),
                messages
        );
    }

    @Transactional
    public void delete(Long userId, Long conversationId) {

        Conversation conversation = owned(userId, conversationId);

        chatMessageRepository.deleteByConversation_Id(conversation.getId());
        conversationRepository.delete(conversation);
    }

    /* =========================================================
     * SEND A MESSAGE
     * ========================================================= */

    @Transactional
    public ChatResult send(Long userId, Long conversationId, String text) {

        String message = text == null ? "" : text.trim();

        if (message.isEmpty()) {
            throw new IllegalArgumentException("Message cannot be empty.");
        }

        if (message.length() > 4000) {
            message = message.substring(0, 4000);
        }

        Conversation conversation;

        if (conversationId == null) {
            conversation = new Conversation();
            conversation.setUser(userRepository.getReferenceById(userId));
            conversation.setTitle(titleFrom(message));
            conversationRepository.save(conversation);
        } else {
            conversation = owned(userId, conversationId);
            if (conversation.getTitle() == null
                    || conversation.getTitle().isBlank()
                    || "New chat".equals(conversation.getTitle())) {
                conversation.setTitle(titleFrom(message));
            }
        }

        ChatMessage userMessage = new ChatMessage();
        userMessage.setConversation(conversation);
        userMessage.setRole(ChatMessage.ROLE_USER);
        userMessage.setContent(message);
        chatMessageRepository.save(userMessage);

        List<ChatMessage> history =
                chatMessageRepository.findByConversation_IdOrderByCreatedAtAscIdAsc(
                        conversation.getId()
                );

        List<ChatMessage> prior = new ArrayList<>(history);
        prior.removeIf(entry -> Objects.equals(entry.getId(), userMessage.getId()));

        Reply reply = compose(conversation, prior, message);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setConversation(conversation);
        assistantMessage.setRole(ChatMessage.ROLE_ASSISTANT);
        assistantMessage.setContent(reply.text());
        assistantMessage.setIntent(reply.intent());
        assistantMessage.setCitationsJson(write(reply.citations()));
        assistantMessage.setRecommendationsJson(write(reply.recommendations()));
        chatMessageRepository.save(assistantMessage);

        conversation.setUpdatedAt(LocalDateTime.now());
        refreshDigest(conversation, history, assistantMessage);
        conversationRepository.save(conversation);

        return new ChatResult(
                conversation.getId(),
                conversation.getTitle(),
                reply.intent(),
                !reply.citations().isEmpty(),
                toView(userMessage),
                toView(assistantMessage)
        );
    }

    /* =========================================================
     * ORCHESTRATION
     * ========================================================= */

    private record Reply(
            String text,
            String intent,
            boolean grounded,
            List<Citation> citations,
            List<CatalogueRecommendationService.BookRecommendation> recommendations
    ) {
    }

    private Reply compose(
            Conversation conversation,
            List<ChatMessage> prior,
            String message) {

        QueryUnderstandingService.QueryPlan plan =
                queryUnderstandingService.plan(message);

        boolean smallTalk = isSmallTalk(message);
        boolean guidance = containsAny(message.toLowerCase(Locale.ROOT), GUIDANCE_CUES);

        List<CatalogueRecommendationService.BookRecommendation> previous =
                previousRecommendations(prior);

        List<CatalogueRecommendationService.BookRecommendation> fresh =
                new ArrayList<>();

        /*
         * A catalogue request is answered from metadata, never from
         * embeddings. Guidance ("I'm struggling with programming") also
         * wants a concrete starting point, so it gets the readable
         * beginner titles rather than a lecture.
         */
        if (!smallTalk) {

            if (plan.wantsRecommendation()) {
                fresh.addAll(
                        catalogueRecommendationService.recommend(
                                plan,
                                MAX_RECOMMENDATIONS,
                                false
                        )
                );
            }

            if (fresh.isEmpty()
                    && (guidance || plan.wantsRecommendation())
                    && followsOnFrom(previous, message)) {
                fresh.addAll(previous);
            }

            if (fresh.isEmpty() && guidance) {
                fresh.addAll(catalogueRecommendationService.starters(3));
            }

            /*
             * "I don't know." after a suggestion is a continuation, not a
             * new question. Keeping the earlier books on screen lets the
             * student pick one instead of losing them to the scroll.
             */
            if (fresh.isEmpty()
                    && !previous.isEmpty()
                    && isContinuation(message, plan)) {
                fresh.addAll(previous);
            }
        }

        /*
         * Retrieval is skipped for greetings and for pure book requests,
         * where any passage would be noise. It runs for concept and
         * procedural questions, and for guided study where the reader
         * named a concrete language or concept.
         */
        boolean needsPassages =
                !smallTalk
                        && (!plan.wantsRecommendation() || !plan.topics().isEmpty());

        List<SemanticSearchService.SearchResultDTO> passages =
                needsPassages
                        ? retrieve(plan.retrievalQuery(), message)
                        : List.of();

        List<Citation> citations = buildCitations(passages);

        String intent = classify(smallTalk, guidance, plan, citations, fresh, previous, message);

        String answer = generate(
                conversation,
                prior,
                message,
                intent,
                fresh,
                previous,
                passages
        );

        return new Reply(
                answer,
                intent,
                !citations.isEmpty(),
                citations,
                fresh
        );
    }

    private String classify(
            boolean smallTalk,
            boolean guidance,
            QueryUnderstandingService.QueryPlan plan,
            List<Citation> citations,
            List<CatalogueRecommendationService.BookRecommendation> recommendations,
            List<CatalogueRecommendationService.BookRecommendation> previous,
            String message) {

        if (smallTalk) {
            return "CHITCHAT";
        }

        if (guidance) {
            return "GUIDANCE";
        }

        if (!recommendations.isEmpty() && plan.wantsRecommendation()) {
            return "RECOMMENDATION";
        }

        if (!citations.isEmpty()) {
            return "RESEARCH";
        }

        if (followsOnFrom(previous, message) && !previous.isEmpty()) {
            return "RECOMMENDATION";
        }

        if (plan.intent() == QueryUnderstandingService.Intent.CONCEPT_SEARCH
                || plan.intent() == QueryUnderstandingService.Intent.PROCEDURAL) {
            return "EXPLANATION";
        }

        return "GENERAL";
    }

    private List<SemanticSearchService.SearchResultDTO> retrieve(
            String retrievalQuery,
            String message) {

        try {

            List<SemanticSearchService.SearchResultDTO> found =
                    semanticSearchService.performRagChunkSearch(
                            retrievalQuery == null || retrievalQuery.isBlank()
                                    ? message
                                    : retrievalQuery,
                            4,
                            "all"
                    );

            if (found == null) {
                return List.of();
            }

            List<SemanticSearchService.SearchResultDTO> relevant = new ArrayList<>();

            for (SemanticSearchService.SearchResultDTO chunk : found) {
                if (chunk != null
                        && chunk.getSimilarityScore() >= MIN_PASSAGE_SCORE
                        && chunk.getContent() != null
                        && chunk.getContent().trim().length() >= 60) {
                    relevant.add(chunk);
                }
            }

            relevant.sort(
                    Comparator.comparingDouble(
                            SemanticSearchService.SearchResultDTO::getSimilarityScore
                    ).reversed()
            );

            return relevant.size() > 4 ? relevant.subList(0, 4) : relevant;

        } catch (Exception e) {

            logger.warn(
                    "Library retrieval unavailable for chat message: {}",
                    e.getMessage()
            );

            return List.of();
        }
    }

    /* =========================================================
     * PROMPT
     * ========================================================= */

    private String generate(
            Conversation conversation,
            List<ChatMessage> prior,
            String message,
            String intent,
            List<CatalogueRecommendationService.BookRecommendation> recommendations,
            List<CatalogueRecommendationService.BookRecommendation> previous,
            List<SemanticSearchService.SearchResultDTO> passages) {

        if (!llmService.isConfigured()) {
            return fallback(message, intent, recommendations, passages);
        }

        List<LlmService.Turn> turns = new ArrayList<>();

        int start = Math.max(0, prior.size() - CONTEXT_WINDOW);

        for (ChatMessage entry : prior.subList(start, prior.size())) {
            turns.add(
                    new LlmService.Turn(
                            entry.isUserMessage() ? "user" : "model",
                            entry.getContent()
                    )
            );
        }

        turns.add(
                new LlmService.Turn(
                        "user",
                        message + "\n\n" + libraryContext(
                                recommendations,
                                previous,
                                passages
                        )
                )
        );

        String system = systemInstruction(conversation, intent);

        try {

            return llmService.chat(system, turns).trim();

        } catch (Exception e) {

            logger.warn("Chat completion failed: {}", e.getMessage());

            return fallback(message, intent, recommendations, passages);
        }
    }

    private String systemInstruction(
            Conversation conversation,
            String intent) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
                You are Astra, the academic companion inside AstraLib, a university
                digital library. You are talking with a student in an ongoing chat.

                How to answer:
                - Talk like a friendly, experienced tutor, not a report generator.
                - Keep most replies to two or three short paragraphs.
                - Do not use markdown headings, bullet-point walls or numbered essay sections.
                - Ask at most one useful follow-up question, and only when it helps.
                - Be encouraging without piling on generic motivational lines.
                - When a library book is offered, mention its title, and say plainly
                  whether it is available to read or only catalogued.
                - Never invent a book, author, level, availability or citation.
                - The context block appended to the student's message contains the
                  only books and passages from AstraLib. If it lists passages,
                  ground your answer in them and mark claims with [1], [2] as given.
                - If an explanation comes from general programming knowledge rather
                  than the library, say so briefly instead of implying a source.
                - If the library has nothing on the subject, say that honestly.

                Current intent: %s
                """.formatted(intent));

        if (conversation.getSummary() != null
                && !conversation.getSummary().isBlank()) {
            prompt.append("\nEarlier in this conversation:\n")
                    .append(conversation.getSummary())
                    .append('\n');
        }

        return prompt.toString();
    }

    private String libraryContext(
            List<CatalogueRecommendationService.BookRecommendation> recommendations,
            List<CatalogueRecommendationService.BookRecommendation> previous,
            List<SemanticSearchService.SearchResultDTO> passages) {

        StringBuilder context = new StringBuilder();

        context.append("\n\n--- AstraLib context for this turn ---\n");

        if (!recommendations.isEmpty()) {

            context.append("Catalogue books that match this request:\n");

            for (CatalogueRecommendationService.BookRecommendation book : recommendations) {

                context.append("- ")
                        .append(book.title())
                        .append(book.author() == null ? "" : " by " + book.author())
                        .append(" (");

                List<String> facts = new ArrayList<>();

                if (book.language() != null) {
                    facts.add(book.language());
                }
                if (book.level() != null) {
                    facts.add(book.level());
                }

                context.append(String.join(", ", facts)).append(") — ");

                context.append(
                        book.readable()
                                ? "available to read"
                                + (book.format() != null ? " as " + book.format() : "")
                                : "catalogued but not available to read yet"
                );

                context.append(" [bookId=").append(book.bookId()).append("]\n");
            }
        }

        if (!previous.isEmpty() && !previous.equals(recommendations)) {

            context.append("Books already offered earlier in this chat: ")
                    .append(
                            String.join(
                                    ", ",
                                    previous.stream()
                                            .map(CatalogueRecommendationService.BookRecommendation::title)
                                            .toList()
                            )
                    )
                    .append('\n');
        }

        if (passages.isEmpty()) {
            context.append("Library passages: none retrieved for this message.\n");
        } else {

            context.append("Library passages (usable as evidence):\n");

            int index = 1;

            for (SemanticSearchService.SearchResultDTO passage : passages) {

                context.append('[').append(index++).append("] ")
                        .append(passage.getTitle() == null ? "Untitled" : passage.getTitle());

                if (passage.getAuthor() != null && !passage.getAuthor().isBlank()) {
                    context.append(", ").append(passage.getAuthor());
                }

                if (passage.getSection() != null && !passage.getSection().isBlank()) {
                    context.append(", section: ").append(passage.getSection());
                }

                context.append('\n')
                        .append(truncate(passage.getContent(), 900))
                        .append("\n\n");
            }
        }

        return context.toString();
    }

    /**
     * Deterministic answer used when no chat model is configured or the
     * model call fails. It still uses real catalogue records and real
     * passages, so a degraded reply is never a fabricated one.
     */
    private String fallback(
            String message,
            String intent,
            List<CatalogueRecommendationService.BookRecommendation> recommendations,
            List<SemanticSearchService.SearchResultDTO> passages) {

        if ("CHITCHAT".equals(intent)) {
            return "Hey! Good to have you here. What are you working on today?";
        }

        if (!recommendations.isEmpty()) {

            StringBuilder text = new StringBuilder();

            text.append("Here is what AstraLib has for that:\n\n");

            for (CatalogueRecommendationService.BookRecommendation book : recommendations) {
                text.append("• ")
                        .append(book.title());

                if (book.author() != null) {
                    text.append(" — ").append(book.author());
                }

                if (book.level() != null) {
                    text.append(" (").append(book.level());
                    if (book.language() != null) {
                        text.append(", ").append(book.language());
                    }
                    text.append(')');
                }

                text.append(book.readable()
                        ? " — available to read now."
                        : " — catalogued, but not available to read yet.");

                text.append('\n');
            }

            text.append("\nWould you like to start with one of these?");

            return text.toString();
        }

        if (!passages.isEmpty()) {

            SemanticSearchService.SearchResultDTO passage = passages.get(0);

            return truncate(passage.getContent(), 700)
                    + "\n\nSource: "
                    + (passage.getTitle() == null ? "AstraLib document" : passage.getTitle())
                    + (passage.getSection() == null ? "" : ", " + passage.getSection())
                    + ".";
        }

        return "I could not find a passage in the AstraLib collection that answers that, "
                + "and I will not invent one. If you tell me the language or topic you are "
                + "studying, I can look for a suitable book instead.";
    }

    /* =========================================================
     * MEMORY
     * ========================================================= */

    private List<CatalogueRecommendationService.BookRecommendation> previousRecommendations(
            List<ChatMessage> prior) {

        for (int i = prior.size() - 1; i >= 0; i--) {

            ChatMessage entry = prior.get(i);

            if (entry.isUserMessage()) {
                continue;
            }

            List<CatalogueRecommendationService.BookRecommendation> stored =
                    readRecommendations(entry.getRecommendationsJson());

            if (!stored.isEmpty()) {
                return stored;
            }
        }

        return List.of();
    }

    /**
     * Folds messages that have left the verbatim window into a short digest,
     * so a long chat keeps its thread without resending the whole transcript.
     */
    private void refreshDigest(
            Conversation conversation,
            List<ChatMessage> history,
            ChatMessage latest) {

        int total = history.size() + 1;

        if (total <= DIGEST_THRESHOLD) {
            return;
        }

        List<ChatMessage> all = new ArrayList<>(history);
        all.add(latest);

        int keep = Math.max(1, total - CONTEXT_WINDOW);
        StringBuilder digest = new StringBuilder();

        for (int i = 0; i < keep; i++) {

            ChatMessage entry = all.get(i);

            digest.append(entry.isUserMessage() ? "Student: " : "Astra: ")
                    .append(truncate(entry.getContent().replaceAll("\\s+", " "), 160))
                    .append('\n');

            if (digest.length() > 1800) {
                break;
            }
        }

        conversation.setSummary(truncate(digest.toString(), 1800));
    }

    /* =========================================================
     * INTENT HELPERS
     * ========================================================= */

    private static final Set<String> GUIDANCE_CUES = Set.of(
            "struggling", "struggle", "i can't", "i cannot", "i dont understand",
            "i don't understand", "confused", "confusing", "hard for me",
            "difficult for me", "too hard", "feeling low", "feeling stuck",
            "stuck", "don't know where to start", "dont know where to start",
            "no idea where to start", "exam", "exams", "failing", "hate programming",
            "give up", "overwhelmed", "not getting", "behind in"
    );

    private boolean isSmallTalk(String message) {

        String lower = message.toLowerCase(Locale.ROOT).trim();

        if (lower.isEmpty() || lower.length() > 80) {
            return false;
        }

        String stripped = lower.replaceAll("[^a-z\\s']", " ").replaceAll("\\s+", " ").trim();

        for (String opener : SMALL_TALK_OPENERS) {
            if (stripped.equals(opener) || stripped.startsWith(opener + " ")) {
                return true;
            }
        }

        return false;
    }

    private static final Set<String> SMALL_TALK_OPENERS = Set.of(
            "hi", "hello", "hey", "hii", "hiya", "yo", "good morning",
            "good afternoon", "good evening", "how are you", "how's it going",
            "what's up", "thanks", "thank you", "thanks a lot", "bye",
            "goodbye", "see you", "who are you", "what can you do",
            "nice to meet you"
    );

    /** A short, non-specific reply inside an established conversation. */
    private boolean isContinuation(
            String message,
            QueryUnderstandingService.QueryPlan plan) {

        if (message.trim().split("\\s+").length > 6) {
            return false;
        }

        if (!plan.topics().isEmpty() || !plan.expansions().isEmpty()) {
            return false;
        }

        return plan.intent() != QueryUnderstandingService.Intent.CONCEPT_SEARCH
                && plan.intent() != QueryUnderstandingService.Intent.PROCEDURAL;
    }

    private boolean followsOnFrom(
            List<CatalogueRecommendationService.BookRecommendation> previous,
            String message) {

        if (previous.isEmpty()) {
            return false;
        }

        String lower = message.toLowerCase(Locale.ROOT);

        if (lower.split("\\s+").length > 12) {
            return false;
        }

        for (String cue : FOLLOW_UP_CUES) {
            if (lower.contains(cue)) {
                return true;
            }
        }

        return false;
    }

    private static final Set<String> FOLLOW_UP_CUES = Set.of(
            "yes", "yeah", "yep", "sure", "ok", "okay", "please", "start",
            "that one", "this one", "that book", "the first", "first exercise",
            "go ahead", "sounds good", "let's", "lets", "i'll try", "ill try",
            "open it", "show me", "suggested", "recommended"
    );

    private boolean containsAny(String text, Set<String> needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /* =========================================================
     * MAPPING
     * ========================================================= */

    private MessageView toView(ChatMessage message) {

        List<Citation> citations = readCitations(message.getCitationsJson());
        List<CatalogueRecommendationService.BookRecommendation> recommendations =
                readRecommendations(message.getRecommendationsJson());

        return new MessageView(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getIntent(),
                !citations.isEmpty(),
                citations,
                recommendations,
                format(message.getCreatedAt())
        );
    }

    private List<Citation> buildCitations(
            List<SemanticSearchService.SearchResultDTO> passages) {

        List<Citation> citations = new ArrayList<>();

        for (SemanticSearchService.SearchResultDTO passage : passages) {
            citations.add(
                    new Citation(
                            passage.getChunkId(),
                            passage.getDocumentId(),
                            passage.getBookId(),
                            passage.getTitle(),
                            passage.getAuthor(),
                            passage.getSection(),
                            passage.getChunkIndex(),
                            passage.getSimilarityScore()
                    )
            );
        }

        return citations;
    }

    private Conversation owned(Long userId, Long conversationId) {

        return conversationRepository
                .findByIdAndUser_Id(conversationId, userId)
                .orElseThrow(
                        () -> new ConversationNotFoundException(conversationId)
                );
    }

    public static class ConversationNotFoundException extends RuntimeException {

        public ConversationNotFoundException(Long id) {
            super("Conversation not found: " + id);
        }
    }

    private String write(Object value) {

        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            logger.warn("Could not serialise chat metadata: {}", e.getMessage());
            return null;
        }
    }

    private List<Citation> readCitations(String json) {

        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<List<Citation>>() {
                    }
            );
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<CatalogueRecommendationService.BookRecommendation> readRecommendations(
            String json) {

        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<List<CatalogueRecommendationService.BookRecommendation>>() {
                    }
            );
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String titleFrom(String message) {

        String title = message.replaceAll("\\s+", " ").trim();

        if (title.isEmpty()) {
            return "New chat";
        }

        return truncate(title, 60);
    }

    private String truncate(String value, int max) {

        if (value == null) {
            return "";
        }

        String text = value.trim();

        return text.length() <= max
                ? text
                : text.substring(0, max - 1) + "…";
    }

    private String format(LocalDateTime timestamp) {

        return timestamp == null ? "" : timestamp.format(TIMESTAMP);
    }
}
