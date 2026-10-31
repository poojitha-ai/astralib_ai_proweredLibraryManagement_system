package com.astralib_backend;

import com.astralib.backend.Author;
import com.astralib.backend.AuthorRepository;
import com.astralib.backend.Book;
import com.astralib.backend.BookRepository;
import com.astralib.backend.Category;
import com.astralib.backend.CategoryRepository;
import com.astralib.backend.DigitalResource;
import com.astralib.backend.DigitalResourceRepository;
import com.astralib.backend.FavoriteController;
import com.astralib.backend.FavoriteRepository;
import com.astralib.backend.ReadingHistoryController;
import com.astralib.backend.ReadingHistoryRepository;
import com.astralib.backend.Role;
import com.astralib.backend.User;
import com.astralib.backend.UserRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Security tests for per-user library data: favorites and reading history.
 *
 * Every query in these controllers is scoped to the JWT principal, so the
 * tests focus on (a) anonymous access being rejected, (b) authenticated
 * users still working, and (c) one user never seeing or altering another
 * user's rows.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserDataAuthorizationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "UserDataPass123!";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DigitalResourceRepository digitalResourceRepository;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private ReadingHistoryRepository historyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    private User alice;
    private User bob;

    private String aliceToken;
    private String bobToken;

    @BeforeAll
    void setUpUsers() throws Exception {

        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        alice = createUser("alice@astralib.test");
        bob = createUser("bob@astralib.test");

        aliceToken = login("alice@astralib.test");
        bobToken = login("bob@astralib.test");
    }

    private User createUser(String email) {

        User existing = userRepository.findByEmail(email).orElse(null);

        if (existing != null) {
            return existing;
        }

        User user = new User("Reader", email, passwordEncoder.encode(PASSWORD));
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

        int start = response.indexOf("\"token\":\"") + "\"token\":\"".length();
        int end = response.indexOf('"', start);

        assertTrue(start > 0 && end > start, "Login failed: " + response);

        return response.substring(start, end);
    }

    // =========================================================
    // FIXTURES
    // =========================================================

    private Book newBook(String title) {

        Author author = authorRepository.save(new Author(title + " Author", "bio"));
        Category category = categoryRepository.save(new Category(title + " Category", "desc"));

        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(author);
        book.setCategory(category);
        book.setLanguage("English");
        book.setResourceType("BOOK");

        return bookRepository.save(book);
    }

    private DigitalResource newResource(Book book) {

        DigitalResource resource = new DigitalResource();
        resource.setBook(book);
        resource.setFileName("fixture.epub");
        resource.setFilePath("fulltext/books/fixture/fixture.epub");
        resource.setFormat("application/epub+zip");
        resource.setResourceType("BOOK");
        resource.setUploadDate(LocalDateTime.now());

        return digitalResourceRepository.save(resource);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Finds the array element whose numeric field matches, or null. */
    private static JsonNode findEntry(JsonNode array, String field, long value) {

        if (array == null || !array.isArray()) {
            return null;
        }

        for (JsonNode node : array) {
            if (node.path(field).asLong() == value) {
                return node;
            }
        }

        return null;
    }

    private static ResultActions assertDenied(ResultActions actions) throws Exception {

        actions.andExpect(status().is4xxClientError());

        int status = actions.andReturn().getResponse().getStatus();

        assertTrue(
                status == 401 || status == 403,
                "Expected 401 or 403 but was " + status
        );

        return actions;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // =========================================================
    // 1. ANONYMOUS ACCESS
    // =========================================================

    @Test
    void anonymousCannotAccessFavoritesOrReadingHistory() throws Exception {

        Book book = newBook("Anon UserData Book");
        DigitalResource resource = newResource(book);

        long favoritesBefore = favoriteRepository.count();
        long historyBefore = historyRepository.count();

        assertDenied(mockMvc.perform(get("/api/favorites")));

        assertDenied(mockMvc.perform(post("/api/favorites/" + book.getId())));

        assertDenied(mockMvc.perform(delete("/api/favorites/" + book.getId())));

        assertDenied(mockMvc.perform(get("/api/reading-history")));

        assertDenied(mockMvc.perform(
                post("/api/reading-history/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookId":%d,"resourceId":%d,"progressPercent":50}
                                """.formatted(book.getId(), resource.getId()))
        ));

        assertDenied(mockMvc.perform(
                get("/api/reading-history/progress/" + book.getId())
        ));

        assertEquals(favoritesBefore, favoriteRepository.count());
        assertEquals(historyBefore, historyRepository.count());
    }

    // =========================================================
    // 2. AUTHENTICATED USER: OWN DATA
    // =========================================================

    @Test
    void authenticatedUserManagesOwnFavorites() throws Exception {

        Book book = newBook("Favourite Book");

        mockMvc.perform(
                        post("/api/favorites/" + book.getId())
                                .header("Authorization", bearer(aliceToken))
                )
                .andExpect(status().isOk());

        assertTrue(
                favoriteRepository
                        .findByUser_IdAndBook_Id(alice.getId(), book.getId())
                        .isPresent(),
                "Favorite must be stored for the authenticated user"
        );

        JsonNode favorites = json(
                mockMvc.perform(
                                get("/api/favorites")
                                        .header("Authorization", bearer(aliceToken))
                        )
                        .andExpect(status().isOk())
                        .andReturn()
        );

        JsonNode entry = findEntry(favorites, "bookId", book.getId());

        assertNotNull(entry, "Own favorite missing from list: " + favorites);
        assertEquals("Favourite Book", entry.path("title").asText());

        mockMvc.perform(
                        delete("/api/favorites/" + book.getId())
                                .header("Authorization", bearer(aliceToken))
                )
                .andExpect(status().isOk());

        assertFalse(
                favoriteRepository
                        .findByUser_IdAndBook_Id(alice.getId(), book.getId())
                        .isPresent(),
                "Favorite must be deleted"
        );
    }

    @Test
    void signedInDashboardAndCatalogueRemainAvailable() throws Exception {

        Book book = newBook("Dashboard Book");

        mockMvc.perform(
                        get("/api/users/me")
                                .header("Authorization", bearer(bobToken))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bob@astralib.test"));

        mockMvc.perform(
                        get("/api/books")
                                .header("Authorization", bearer(bobToken))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/books/" + book.getId())
                                .header("Authorization", bearer(bobToken))
                )
                .andExpect(status().isOk());
    }

    // =========================================================
    // 3. CROSS-USER FAVORITES
    // =========================================================

    @Test
    void userCannotReadOrDeleteAnotherUsersFavorite() throws Exception {

        Book book = newBook("Alice Only Book");

        mockMvc.perform(
                        post("/api/favorites/" + book.getId())
                                .header("Authorization", bearer(aliceToken))
                )
                .andExpect(status().isOk());

        // Bob has no favorites, so he must see an empty list.
        mockMvc.perform(
                        get("/api/favorites")
                                .header("Authorization", bearer(bobToken))
                )
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        // Bob deleting Alice's favorite must not succeed and must not
        // touch Alice's row.
        mockMvc.perform(
                        delete("/api/favorites/" + book.getId())
                                .header("Authorization", bearer(bobToken))
                )
                .andExpect(status().isNotFound());

        assertTrue(
                favoriteRepository
                        .findByUser_IdAndBook_Id(alice.getId(), book.getId())
                        .isPresent(),
                "Another user must not delete Alice's favorite"
        );

        assertFalse(
                favoriteRepository
                        .findByUser_IdAndBook_Id(bob.getId(), book.getId())
                        .isPresent()
        );
    }

    // =========================================================
    // 4. AUTHENTICATED USER: OWN READING HISTORY
    // =========================================================

    @Test
    void authenticatedUserSavesAndReadsOwnProgress() throws Exception {

        Book book = newBook("Progress Book");
        DigitalResource resource = newResource(book);

        mockMvc.perform(
                        post("/api/reading-history/progress")
                                .header("Authorization", bearer(aliceToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"bookId":%d,"resourceId":%d,"progressPercent":42,"lastLocationCfi":"epubcfi(/6/4!/2/1:1)"}
                                        """.formatted(book.getId(), resource.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercent").value(42));

        JsonNode history = json(
                mockMvc.perform(
                                get("/api/reading-history")
                                        .header("Authorization", bearer(aliceToken))
                        )
                        .andExpect(status().isOk())
                        .andReturn()
        );

        JsonNode entry = findEntry(history, "bookId", book.getId());

        assertNotNull(entry, "Reading history row missing: " + history);
        assertEquals(42, entry.path("progressPercent").asInt());

        mockMvc.perform(
                        get("/api/reading-history/progress/" + book.getId())
                                .header("Authorization", bearer(aliceToken))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercent").value(42))
                .andExpect(jsonPath("$.lastLocationCfi").value("epubcfi(/6/4!/2/1:1)"));
    }

    // =========================================================
    // 5. CROSS-USER READING HISTORY
    // =========================================================

    @Test
    void userCannotSeeOrOverwriteAnotherUsersProgress() throws Exception {

        Book book = newBook("Shared Volume");
        DigitalResource resource = newResource(book);

        mockMvc.perform(
                        post("/api/reading-history/progress")
                                .header("Authorization", bearer(aliceToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"bookId":%d,"resourceId":%d,"progressPercent":42}
                                        """.formatted(book.getId(), resource.getId()))
                )
                .andExpect(status().isOk());

        // Bob's own history is unaffected by Alice's row.
        mockMvc.perform(
                        get("/api/reading-history")
                                .header("Authorization", bearer(bobToken))
                )
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        // Bob asking for "his" progress on the same book gets a fresh
        // zero, never Alice's position.
        mockMvc.perform(
                        get("/api/reading-history/progress/" + book.getId())
                                .header("Authorization", bearer(bobToken))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercent").value(0))
                .andExpect(jsonPath("$.id").doesNotExist());

        // Bob recording progress for himself must not overwrite Alice's row.
        mockMvc.perform(
                        post("/api/reading-history/progress")
                                .header("Authorization", bearer(bobToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"bookId":%d,"resourceId":%d,"progressPercent":90}
                                        """.formatted(book.getId(), resource.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercent").value(90));

        var aliceRow = historyRepository
                .findByUser_IdAndBook_IdAndResource_Id(
                        alice.getId(),
                        book.getId(),
                        resource.getId()
                )
                .orElseThrow(() -> new AssertionError("Alice's row disappeared"));

        var bobRow = historyRepository
                .findByUser_IdAndBook_IdAndResource_Id(
                        bob.getId(),
                        book.getId(),
                        resource.getId()
                )
                .orElseThrow(() -> new AssertionError("Bob's row was not created"));

        assertEquals(42, aliceRow.getProgressPercent(), "Alice's progress was overwritten");
        assertEquals(90, bobRow.getProgressPercent());
        assertTrue(!aliceRow.getId().equals(bobRow.getId()));
    }

    // =========================================================
    // 6. NO PRINCIPAL -> 401, NOT 500
    // =========================================================

    @Test
    void controllersRejectMissingOrAnonymousPrincipalsWithoutThrowing() {

        FavoriteController favorites = new FavoriteController(
                favoriteRepository,
                userRepository,
                bookRepository
        );

        ReadingHistoryController history = new ReadingHistoryController(
                historyRepository,
                userRepository,
                bookRepository,
                digitalResourceRepository
        );

        Authentication anonymous = new AnonymousAuthenticationToken(
                "anonymous-key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );

        // A real numeric principal still works.
        Authentication numeric = new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken(alice.getId(), null, List.of());

        for (Authentication authentication : new Authentication[]{null, anonymous}) {

            assertEquals(
                    HttpStatus.UNAUTHORIZED,
                    favoriteControllerStatus(favorites.listFavorites(authentication))
            );

            assertEquals(
                    HttpStatus.UNAUTHORIZED,
                    favoriteControllerStatus(favorites.removeFavorite(1L, authentication))
            );

            assertEquals(
                    HttpStatus.UNAUTHORIZED,
                    favoriteControllerStatus(favorites.addFavorite(1L, authentication))
            );

            assertEquals(
                    HttpStatus.UNAUTHORIZED,
                    historyControllerStatus(history.getHistory(authentication))
            );

            assertEquals(
                    HttpStatus.UNAUTHORIZED,
                    historyControllerStatus(history.getProgress(1L, authentication))
            );

            assertEquals(
                    HttpStatus.UNAUTHORIZED,
                    historyControllerStatus(
                            history.saveProgress(
                                    Map.of("bookId", 1, "resourceId", 1),
                                    authentication
                            )
                    )
            );
        }

        assertEquals(
                HttpStatus.OK,
                historyControllerStatus(history.getHistory(numeric))
        );
    }

    private static HttpStatus favoriteControllerStatus(ResponseEntity<?> response) {
        return HttpStatus.valueOf(response.getStatusCode().value());
    }

    private static HttpStatus historyControllerStatus(ResponseEntity<?> response) {
        return HttpStatus.valueOf(response.getStatusCode().value());
    }
}
