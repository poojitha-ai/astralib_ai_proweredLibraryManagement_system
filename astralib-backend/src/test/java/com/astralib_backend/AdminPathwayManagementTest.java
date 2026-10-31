package com.astralib_backend;

import com.astralib.backend.Author;
import com.astralib.backend.AuthorRepository;
import com.astralib.backend.Book;
import com.astralib.backend.BookRepository;
import com.astralib.backend.Category;
import com.astralib.backend.CategoryRepository;
import com.astralib.backend.DigitalResource;
import com.astralib.backend.DigitalResourceRepository;
import com.astralib.backend.Pathway;
import com.astralib.backend.PathwayBook;
import com.astralib.backend.PathwayBookRepository;
import com.astralib.backend.PathwayRepository;
import com.astralib.backend.Role;
import com.astralib.backend.User;
import com.astralib.backend.UserRepository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Pathway curation is an administrative tool: the catalogue keeps using
 * the structure, but only an administrator may read or change it from
 * the management API.
 *
 * These tests go through the real security filter chain and assert the
 * stored rows as well as the HTTP responses.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminPathwayManagementTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "TestPassword123!";

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BookRepository bookRepository;
    @Autowired private AuthorRepository authorRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private DigitalResourceRepository digitalResourceRepository;
    @Autowired private PathwayRepository pathwayRepository;
    @Autowired private PathwayBookRepository pathwayBookRepository;

    private MockMvc mockMvc;

    private String userToken;
    private String librarianToken;
    private String adminToken;

    private Long firstBookId;
    private Long secondBookId;
    private Long storedResourceBookId;
    private Long metadataOnlyBookId;

    @BeforeAll
    void setUp() throws Exception {

        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        userToken = login(createUser("pathway-user@astralib.test", Role.USER).getEmail());
        librarianToken = login(createUser("pathway-librarian@astralib.test", Role.LIBRARIAN).getEmail());
        adminToken = login(createUser("pathway-admin@astralib.test", Role.ADMIN).getEmail());

        Author author = authorRepository.save(new Author("Curation Fixture Author", null));
        Category category = categoryRepository.save(new Category("Curation Fixture Genre", null));

        firstBookId = newBook("Curation First", author, category).getId();
        secondBookId = newBook("Curation Second", author, category).getId();

        Book withFile = newBook("Curation With File", author, category);

        DigitalResource resource = new DigitalResource();
        resource.setBook(withFile);
        resource.setResourceType("EPUB");
        resource.setFileName("curation.epub");
        resource.setFilePath("fulltext/books/curation/curation.epub");
        digitalResourceRepository.save(resource);

        storedResourceBookId = withFile.getId();
        metadataOnlyBookId = newBook("Curation Metadata Only", author, category).getId();
    }

    // =========================================================
    // FIXTURES AND HELPERS
    // =========================================================

    private User createUser(String email, Role role) {

        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User(
                    role.name() + " Curation",
                    email,
                    passwordEncoder.encode(PASSWORD)
            );
            user.setRole(role);

            return userRepository.save(user);
        });
    }

    private String login(String email) throws Exception {

        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);

        String response = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int index = response.indexOf("\"token\":\"");
        int start = index + "\"token\":\"".length();

        return response.substring(start, response.indexOf('"', start));
    }

    private Book newBook(String title, Author author, Category category) {

        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(author);
        book.setCategory(category);
        book.setLanguage("English");
        book.setResourceType("EPUB");

        return bookRepository.save(book);
    }

    private static ResultActions assertDenied(ResultActions actions) throws Exception {

        actions.andExpect(status().is4xxClientError());

        int status = actions.andReturn().getResponse().getStatus();

        assertTrue(status == 401 || status == 403, "Expected 401 or 403 but was " + status);

        return actions;
    }

    private Long createPathway(String name, String type) throws Exception {

        String slug = name.toLowerCase().replace(' ', '-');

        mockMvc.perform(post("/api/admin/pathways")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","type":"%s","subject":"Testing"}
                                """.formatted(name, type)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.type").value(type));

        Pathway created = pathwayRepository.findBySlug(slug).orElse(null);
        assertNotNull(created, "The pathway must be persisted");

        return created.getId();
    }

    private ResultActions addBook(Long pathwayId, String body) throws Exception {
        return mockMvc.perform(post("/api/admin/pathways/" + pathwayId + "/books")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // =========================================================
    // AUTHORIZATION
    // =========================================================

    @Test
    void anonymousRequestsAreRejected() throws Exception {

        assertDenied(mockMvc.perform(get("/api/admin/pathways")));

        assertDenied(mockMvc.perform(post("/api/admin/pathways")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Anonymous Path\",\"type\":\"LEARNING\"}")));

        assertTrue(pathwayRepository.findBySlug("anonymous-path").isEmpty());
    }

    @Test
    void ordinaryUsersCannotManagePathways() throws Exception {

        assertDenied(mockMvc.perform(get("/api/admin/pathways")
                .header("Authorization", "Bearer " + userToken)));

        assertDenied(mockMvc.perform(post("/api/admin/pathways")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Reader Path\",\"type\":\"LEARNING\"}")));

        assertDenied(mockMvc.perform(get("/api/admin/pathways/metadata-gaps")
                .header("Authorization", "Bearer " + userToken)));

        assertTrue(pathwayRepository.findBySlug("reader-path").isEmpty());
    }

    @Test
    void librariansDoNotCuratePathways() throws Exception {

        assertDenied(mockMvc.perform(get("/api/admin/pathways")
                .header("Authorization", "Bearer " + librarianToken)));
    }

    @Test
    void readersCanStillBrowseBookCatalogueData() throws Exception {

        // Pathway data stays readable for the catalogue, but through the
        // read-only browsing endpoint, not the management API.
        mockMvc.perform(get("/api/pathways"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/pathways/memberships"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/books").param("resourceOnly", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void theLibraryCatalogueOnlyListsBooksWithAFiles() throws Exception {

        String body = mockMvc.perform(get("/api/books").param("resourceOnly", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"id\":" + storedResourceBookId),
                "A book with a stored file belongs in the library catalogue");
        assertFalse(body.contains("\"id\":" + metadataOnlyBookId),
                "A metadata-only record must not appear in the library catalogue");
    }

    @Test
    void cataloguedEntriesWithoutFilesAppearButAreNotReadable() throws Exception {

        Author author = authorRepository.save(new Author("Catalogued Only Author", null));
        Category category = categoryRepository.save(new Category("Programming", null));

        Book catalogued = newBook("Catalogued Without File", author, category);
        catalogued.setCatalogueEntry(Boolean.TRUE);
        catalogued.setResourceType(null);
        catalogued = bookRepository.save(catalogued);

        String body = mockMvc.perform(get("/api/books").param("catalogueOnly", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"id\":" + catalogued.getId()),
                "A curated catalogue entry without a file must still be listed");
        assertFalse(body.contains("\"id\":" + metadataOnlyBookId),
                "A bare dataset record must still stay out of the catalogue");

        String readable = mockMvc.perform(get("/api/books").param("resourceOnly", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(readable.contains("\"id\":" + catalogued.getId()),
                "resourceOnly must keep meaning 'a reader can open it'");
        assertTrue(readable.contains("\"id\":" + storedResourceBookId),
                "A book with a stored file is readable");
    }

    // =========================================================
    // CURATION
    // =========================================================

    @Test
    void adminCanCreateAndEditPathways() throws Exception {

        Long pathwayId = createPathway("Curated Admin Pathway", Pathway.TYPE_LEARNING);

        mockMvc.perform(put("/api/admin/pathways/" + pathwayId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Systems","audience":"Second-year students",
                                 "description":"A documented sequence used for catalogue levels."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Systems"))
                .andExpect(jsonPath("$.audience").value("Second-year students"));

        Pathway reloaded = pathwayRepository.findById(pathwayId).orElseThrow();

        assertEquals("Systems", reloaded.getSubject());
        assertEquals("A documented sequence used for catalogue levels.", reloaded.getDescription());
    }

    @Test
    void adminCanAddEditAndRemoveMemberships() throws Exception {

        Long pathwayId = createPathway("Curated Membership Pathway", Pathway.TYPE_LEARNING);

        addBook(pathwayId, """
                {"bookId":%d,"level":"BEGINNER","position":1,
                 "rationale":"Starts from no prior knowledge"}
                """.formatted(firstBookId))
                .andExpect(status().isCreated());

        addBook(pathwayId, """
                {"bookId":%d,"level":"ADVANCED","position":2,"prerequisiteBookId":%d,
                 "rationale":"Builds on the first book","learningOutcome":"Design a subsystem"}
                """.formatted(secondBookId, firstBookId))
                .andExpect(status().isCreated());

        List<PathwayBook> entries = pathwayBookRepository.findByPathway_IdOrderByPositionAsc(pathwayId);

        assertEquals(2, entries.size(), "Both memberships must be stored");
        assertEquals("BEGINNER", entries.get(0).getLevel());
        assertEquals("ADVANCED", entries.get(1).getLevel());
        assertEquals(firstBookId, entries.get(1).getPrerequisiteBook().getId());
        assertEquals("Design a subsystem", entries.get(1).getLearningOutcome());

        Long entryId = entries.get(0).getId();

        mockMvc.perform(put("/api/admin/pathways/" + pathwayId + "/books/" + entryId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level":"INTERMEDIATE","position":1,
                                 "rationale":"Adjusted after review"}
                                """))
                .andExpect(status().isOk());

        PathwayBook updated = pathwayBookRepository.findById(entryId).orElseThrow();

        assertEquals("INTERMEDIATE", updated.getLevel());
        assertEquals("Adjusted after review", updated.getRationale());

        Long dependentEntryId = entries.get(1).getId();

        // A book that another book depends on cannot simply disappear.
        mockMvc.perform(delete("/api/admin/pathways/" + pathwayId + "/books/" + entryId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        assertEquals(
                2,
                pathwayBookRepository.findByPathway_IdOrderByPositionAsc(pathwayId).size(),
                "A refused removal leaves the sequence intact"
        );

        // Clearing the dependent prerequisite then allows the removal.
        mockMvc.perform(put("/api/admin/pathways/" + pathwayId + "/books/" + dependentEntryId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"ADVANCED\",\"position\":2,\"prerequisiteBookId\":null}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/pathways/" + pathwayId + "/books/" + entryId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        assertTrue(bookRepository.findById(firstBookId).isPresent(),
                "Removing a membership keeps the underlying book");

        assertTrue(bookRepository.findById(secondBookId).isPresent(),
                "The dependent book keeps existing too");

        assertEquals(
                1,
                pathwayBookRepository.findByPathway_IdOrderByPositionAsc(pathwayId).size()
        );
    }

    @Test
    void aLevelMustMatchThePathwayType() throws Exception {

        Long learningId = createPathway("Curated Level Type Pathway", Pathway.TYPE_LEARNING);

        addBook(learningId, """
                {"bookId":%d,"level":"ACCESSIBLE","position":1}
                """.formatted(firstBookId))
                .andExpect(status().isBadRequest());

        Long leisureId = createPathway("Curated Leisure Type Pathway", Pathway.TYPE_LEISURE);

        addBook(leisureId, """
                {"bookId":%d,"level":"BEGINNER","position":1}
                """.formatted(firstBookId))
                .andExpect(status().isBadRequest());

        assertTrue(pathwayBookRepository
                .findByPathway_IdOrderByPositionAsc(learningId).isEmpty(),
                "A rejected level must not create a membership");

        addBook(leisureId, """
                {"bookId":%d,"level":"ACCESSIBLE","position":1}
                """.formatted(firstBookId))
                .andExpect(status().isCreated());
    }

    @Test
    void theSameBookCannotBeAddedTwice() throws Exception {

        Long pathwayId = createPathway("Curated Duplicate Pathway", Pathway.TYPE_LEARNING);

        addBook(pathwayId, """
                {"bookId":%d,"level":"BEGINNER","position":1}
                """.formatted(firstBookId))
                .andExpect(status().isCreated());

        addBook(pathwayId, """
                {"bookId":%d,"level":"INTERMEDIATE","position":2}
                """.formatted(firstBookId))
                .andExpect(status().isConflict());

        assertEquals(
                1,
                pathwayBookRepository.findByPathway_IdOrderByPositionAsc(pathwayId).size()
        );
    }

    @Test
    void aPrerequisiteMustComeEarlierInTheSequence() throws Exception {

        Long pathwayId = createPathway("Curated Prerequisite Pathway", Pathway.TYPE_LEARNING);

        addBook(pathwayId, """
                {"bookId":%d,"level":"BEGINNER","position":1}
                """.formatted(firstBookId))
                .andExpect(status().isCreated());

        addBook(pathwayId, """
                {"bookId":%d,"level":"ADVANCED","position":2}
                """.formatted(secondBookId))
                .andExpect(status().isCreated());

        // A later book may point back at an earlier one.
        addBook(pathwayId, """
                {"bookId":%d,"level":"INTERMEDIATE","position":3,"prerequisiteBookId":%d}
                """.formatted(metadataOnlyBookId, secondBookId))
                .andExpect(status().isCreated());

        // Pointing forward would invert the chain, so it is refused.
        Long firstEntry = pathwayBookRepository
                .findByPathway_IdOrderByPositionAsc(pathwayId).stream()
                .filter(entry -> entry.getBook().getId().equals(firstBookId))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(put("/api/admin/pathways/" + pathwayId + "/books/" + firstEntry)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level":"BEGINNER","position":1,"prerequisiteBookId":%d}
                                """.formatted(secondBookId)))
                .andExpect(status().isBadRequest());

        PathwayBook unchanged = pathwayBookRepository.findById(firstEntry).orElseThrow();

        assertEquals(null, unchanged.getPrerequisiteBook(),
                "A refused prerequisite must not be stored");

        // An unknown prerequisite is reported rather than stored.
        addBook(pathwayId, """
                {"bookId":%d,"level":"ADVANCED","position":4,"prerequisiteBookId":999999}
                """.formatted(storedResourceBookId))
                .andExpect(status().isBadRequest());

        // Two books cannot occupy the same place in the sequence.
        addBook(pathwayId, """
                {"bookId":%d,"level":"ADVANCED","position":2}
                """.formatted(storedResourceBookId))
                .andExpect(status().isBadRequest());

        assertEquals(
                3,
                pathwayBookRepository.findByPathway_IdOrderByPositionAsc(pathwayId).size()
        );
    }

    @Test
    void metadataGapsAreReportedToAdminsOnly() throws Exception {

        mockMvc.perform(get("/api/admin/pathways/metadata-gaps")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].missing").isArray());
    }
}
