package com.astralib_backend;

import com.astralib.backend.Author;
import com.astralib.backend.AuthorRepository;
import com.astralib.backend.Book;
import com.astralib.backend.BookRepository;
import com.astralib.backend.Category;
import com.astralib.backend.CategoryRepository;
import com.astralib.backend.ChatMessageRepository;
import com.astralib.backend.ConversationRepository;
import com.astralib.backend.DigitalResource;
import com.astralib.backend.DigitalResourceRepository;
import com.astralib.backend.Pathway;
import com.astralib.backend.PathwayBook;
import com.astralib.backend.PathwayBookRepository;
import com.astralib.backend.PathwayRepository;
import com.astralib.backend.Role;
import com.astralib.backend.User;
import com.astralib.backend.UserRepository;
import com.astralib.backend.VectorRecord;
import com.astralib.backend.VectorRecordRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Tests for the conversational Research Assistant.
 *
 * The suite runs with no LLM key (AbstractIntegrationTest forces an empty
 * key), so every reply here is the deterministic fallback. That is exactly
 * what these tests need: they prove the persistence, ownership, memory and
 * catalogue wiring that the model sits on top of, without calling Gemini.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConversationChatTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "ChatPass123!";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private DigitalResourceRepository digitalResourceRepository;

    @Autowired
    private PathwayRepository pathwayRepository;

    @Autowired
    private PathwayBookRepository pathwayBookRepository;

    @Autowired
    private VectorRecordRepository vectorRecordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    private String aliceToken;
    private String bobToken;

    @BeforeAll
    void setUpUsersAndCatalogue() throws Exception {

        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        createUser("chat-alice@astralib.test");
        createUser("chat-bob@astralib.test");

        aliceToken = login("chat-alice@astralib.test");
        bobToken = login("chat-bob@astralib.test");

        seedBeginnerProgrammingBook();
    }

    // =========================================================
    // FIXTURES
    // =========================================================

    private User createUser(String email) {

        User existing = userRepository.findByEmail(email).orElse(null);

        if (existing != null) {
            return existing;
        }

        User user = new User("Chat Test", email, passwordEncoder.encode(PASSWORD));
        user.setRole(Role.USER);

        return userRepository.save(user);
    }

    private String login(String email) throws Exception {

        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);

        String response = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int tokenIndex = response.indexOf("\"token\":\"");

        assertTrue(tokenIndex >= 0, "Login did not return a token: " + response);

        int start = tokenIndex + "\"token\":\"".length();
        int end = response.indexOf('"', start);

        return response.substring(start, end);
    }

    /**
     * A readable beginner Java title, so the guidance and recommendation
     * paths have a real catalogue record to use.
     */
    private void seedBeginnerProgrammingBook() {

        Author author = authorRepository.save(
                new Author("Chat Fixture Author", "test author")
        );

        Category programming = categoryRepository
                .findByName("Programming")
                .orElseGet(() -> categoryRepository.save(
                        new Category("Programming", "Programming languages")
                ));

        Book book = new Book();
        book.setTitle("Chat Fixture Java Basics");
        book.setAuthor(author);
        book.setCategory(programming);
        book.setLanguage("English");
        book.setResourceType("BOOK");
        book.setResourceId("chat-fixture-java");
        book.setDescription("A beginner Java book used by the chat tests.");
        book.setGenres(new java.util.ArrayList<>(List.of("Java")));

        bookRepository.save(book);

        DigitalResource resource = new DigitalResource();
        resource.setBook(book);
        resource.setResourceType("BOOK");
        resource.setFileName("chat-fixture-java.epub");
        resource.setFilePath("/tmp/chat-fixture-java.epub");
        resource.setFormat("EPUB");
        digitalResourceRepository.save(resource);

        vectorRecordRepository.save(
                new VectorRecord(
                        "chat-fixture-java-0",
                        "chat-fixture-java",
                        "book",
                        book.getTitle(),
                        author.getName(),
                        "Programming",
                        "Chapter 1",
                        0,
                        "Java is a general-purpose programming language that runs "
                                + "on the Java Virtual Machine. A program starts at main.",
                        "[0.0,0.0]"
                )
        );

        Pathway pathway = new Pathway();
        pathway.setName("Chat Fixture Java Pathway");
        pathway.setSlug("chat-fixture-java-pathway");
        pathway.setType(Pathway.TYPE_LEARNING);
        pathway.setSubject("Java");
        pathwayRepository.save(pathway);

        PathwayBook membership = new PathwayBook();
        membership.setPathway(pathway);
        membership.setBook(book);
        membership.setPosition(1);
        membership.setLevel(PathwayBook.LEVEL_BEGINNER);
        membership.setRationale("Chat fixture level.");
        pathwayBookRepository.save(membership);
    }

    // =========================================================
    // AUTH
    // =========================================================

    @Test
    void anonymousCannotUseTheAssistant() throws Exception {

        mockMvc.perform(get("/api/conversations"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(post("/api/conversations"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(
                        post("/api/conversations/messages")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"Hello\"}")
                )
                .andExpect(status().is4xxClientError());
    }

    // =========================================================
    // CONVERSATION LIFECYCLE
    // =========================================================

    @Test
    void greetingCreatesAPersistedConversation() throws Exception {

        JsonNode sent = sendMessage(aliceToken, null, "Hello");

        long conversationId = sent.path("conversationId").asLong();

        assertTrue(conversationId > 0);
        assertEquals("CHITCHAT", sent.path("intent").asText());
        assertEquals("USER", sent.path("userMessage").path("role").asText());
        assertEquals(
                "ASSISTANT",
                sent.path("assistantMessage").path("role").asText()
        );
        assertFalse(
                sent.path("assistantMessage").path("content").asText().isBlank()
        );
        assertFalse(
                sent.path("assistantMessage").path("grounded").asBoolean(),
                "A greeting must not be presented as library-grounded"
        );

        mockMvc.perform(
                        get("/api/conversations/" + conversationId)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"));

        assertEquals(
                2,
                chatMessageRepository.countInConversation(conversationId)
        );

        assertTrue(
                conversationRepository.findById(conversationId).orElseThrow()
                        .getTitle().length() > 0
        );
    }

    @Test
    void explicitNewConversationIsEmpty() throws Exception {

        String created = mockMvc.perform(
                        post("/api/conversations")
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New chat"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = objectMapper.readTree(created).path("id").asLong();

        mockMvc.perform(
                        get("/api/conversations/" + id)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(0));
    }

    @Test
    void conversationKeepsItsHistoryAcrossTurns() throws Exception {

        long conversationId = sendMessage(aliceToken, null, "Hello")
                .path("conversationId").asLong();

        sendMessage(
                aliceToken,
                conversationId,
                "I am struggling with programming"
        );

        String body = mockMvc.perform(
                        get("/api/conversations/" + conversationId)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(4))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode messages = objectMapper.readTree(body).path("messages");

        assertEquals("USER", messages.get(0).path("role").asText());
        assertEquals("Hello", messages.get(0).path("content").asText());
        assertEquals("ASSISTANT", messages.get(1).path("role").asText());

        assertEquals("USER", messages.get(2).path("role").asText());

        assertEquals(
                "GUIDANCE",
                messages.get(3).path("intent").asText(),
                "A student who says they are struggling should be coached, "
                        + "not handed a research report"
        );

        assertTrue(
                messages.get(3).path("recommendations").isArray(),
                "Support should be attached to a real catalogue book"
        );

        assertTrue(
                messages.get(3).path("recommendations").size() > 0,
                "The seeded beginner Java book should be offered"
        );

        assertEquals(
                "Chat Fixture Java Basics",
                messages.get(3).path("recommendations").get(0)
                        .path("title").asText()
        );
    }

    @Test
    void newConversationDoesNotInheritAnotherChatsContext() throws Exception {

        long first = sendMessage(aliceToken, null, "Hello")
                .path("conversationId").asLong();

        sendMessage(aliceToken, first, "I am struggling with programming");

        long second = sendMessage(aliceToken, null, "Hello")
                .path("conversationId").asLong();

        assertFalse(first == second);

        mockMvc.perform(
                        get("/api/conversations/" + second)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].content").value("Hello"));
    }

    // =========================================================
    // OWNERSHIP
    // =========================================================

    @Test
    void oneReaderCannotReadOrContinueAnothersChat() throws Exception {

        long conversationId = sendMessage(aliceToken, null, "Hello")
                .path("conversationId").asLong();

        mockMvc.perform(
                        get("/api/conversations/" + conversationId)
                                .header("Authorization", "Bearer " + bobToken)
                )
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        post("/api/conversations/" + conversationId + "/messages")
                                .header("Authorization", "Bearer " + bobToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"Hello\"}")
                )
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        delete("/api/conversations/" + conversationId)
                                .header("Authorization", "Bearer " + bobToken)
                )
                .andExpect(status().isNotFound());

        assertEquals(
                2,
                chatMessageRepository.countInConversation(conversationId),
                "A foreign user must not add or remove messages"
        );

        String bobList = mockMvc.perform(
                        get("/api/conversations")
                                .header("Authorization", "Bearer " + bobToken)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(
                bobList.contains(String.valueOf(conversationId)),
                "Listing must only contain the caller's conversations"
        );
    }

    @Test
    void deletingAConversationRemovesItsMessages() throws Exception {

        long conversationId = sendMessage(aliceToken, null, "Hello")
                .path("conversationId").asLong();

        mockMvc.perform(
                        delete("/api/conversations/" + conversationId)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/conversations/" + conversationId)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isNotFound());

        assertEquals(
                0,
                chatMessageRepository.countInConversation(conversationId)
        );
    }

    // =========================================================
    // GROUNDING
    // =========================================================

    @Test
    void libraryQuestionIsAnsweredFromIndexedPassagesOnly() throws Exception {

        JsonNode sent = sendMessage(
                aliceToken,
                null,
                "What is Java and where does a program start?"
        );

        String answer = sent.path("assistantMessage").path("content").asText();

        assertFalse(answer.isBlank());

        /*
         * No embeddings are configured in tests, so retrieval yields no
         * passages and the reply must not claim a source. This is the
         * invariant that matters: an ungrounded answer is never dressed
         * up with citations.
         */
        assertTrue(
                sent.path("assistantMessage").path("citations").isEmpty(),
                "No passage was retrieved, so no citation may be invented"
        );

        assertFalse(
                sent.path("assistantMessage").path("grounded").asBoolean()
        );
    }

    @Test
    void emptyMessageIsRejected() throws Exception {

        mockMvc.perform(
                        post("/api/conversations/messages")
                                .header("Authorization", "Bearer " + aliceToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"   \"}")
                )
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private JsonNode sendMessage(
            String token,
            Long conversationId,
            String message) throws Exception {

        String path = conversationId == null
                ? "/api/conversations/messages"
                : "/api/conversations/" + conversationId + "/messages";

        String body = mockMvc.perform(
                        post(path)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                java.util.Map.of("message", message)
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body);
    }
}
