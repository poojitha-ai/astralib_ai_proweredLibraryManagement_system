package com.astralib_backend;

import com.astralib.backend.Author;
import com.astralib.backend.AuthorRepository;
import com.astralib.backend.Book;
import com.astralib.backend.BookRepository;
import com.astralib.backend.Category;
import com.astralib.backend.CategoryRepository;
import com.astralib.backend.DigitalResourceRepository;
import com.astralib.backend.Role;
import com.astralib.backend.User;
import com.astralib.backend.UserRepository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Authorization regression tests for catalogue and resource writes.
 *
 * These run through the real Spring Security filter chain (including
 * JwtAuthenticationFilter) against the isolated H2 database, and they
 * assert the resulting database state for every operation that is
 * supposed to succeed or fail.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogueAuthorizationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "TestPassword123!";

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
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    private String userToken;
    private String librarianToken;
    private String adminToken;

    private Long userId;
    private Long adminId;

    @BeforeAll
    void setUpSecurityFixtures() throws Exception {

        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        User user = createUser("reader@astralib.test", Role.USER);
        User librarian = createUser("librarian@astralib.test", Role.LIBRARIAN);
        User admin = createUser("admin@astralib.test", Role.ADMIN);

        userId = user.getId();
        adminId = admin.getId();

        userToken = login("reader@astralib.test");
        librarianToken = login("librarian@astralib.test");
        adminToken = login("admin@astralib.test");
    }

    private User createUser(String email, Role role) {

        User existing = userRepository.findByEmail(email).orElse(null);

        if (existing != null) {
            return existing;
        }

        User user = new User(
                role.name() + " Test",
                email,
                passwordEncoder.encode(PASSWORD)
        );
        user.setRole(role);

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

    // =========================================================
    // FIXTURES
    // =========================================================

    private Author newAuthor(String name) {
        return authorRepository.save(new Author(name, "test author"));
    }

    private Category newCategory(String name) {
        return categoryRepository.save(new Category(name, "test category"));
    }

    private Book newBook(String title, Author author, Category category) {

        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(author);
        book.setCategory(category);
        book.setLanguage("English");
        book.setResourceType("BOOK");

        return bookRepository.save(book);
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

    private static MockMultipartFile epubFile() {

        return new MockMultipartFile(
                "file",
                "test-book.epub",
                "application/epub+zip",
                "PK\u0003\u0004 fake epub bytes".getBytes(StandardCharsets.UTF_8)
        );
    }

    // =========================================================
    // 1. ANONYMOUS READS
    // =========================================================

    @Test
    void anonymousCanBrowseThePublicCatalogue() throws Exception {

        Author author = newAuthor("Anonymous Browse Author");
        Category category = newCategory("Anonymous Browse Category");

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/books/" + newBook("Browse Me", author, category).getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/authors"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
    }

    // =========================================================
    // 2. ANONYMOUS WRITES
    // =========================================================

    @Test
    void anonymousCannotCreateUpdateOrDeleteBooks() throws Exception {

        Author author = newAuthor("Anon Book Author");
        Category category = newCategory("Anon Book Category");
        Book book = newBook("Untouched Book", author, category);

        long before = bookRepository.count();

        assertDenied(mockMvc.perform(
                post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Anonymous Created","authorId":%d,"categoryId":%d}
                                """.formatted(author.getId(), category.getId()))
        ));

        assertDenied(mockMvc.perform(
                put("/api/books/" + book.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Anonymous Edited\"}")
        ));

        assertDenied(mockMvc.perform(
                delete("/api/books/" + book.getId())
        ));

        assertEquals(before, bookRepository.count(), "No book may be created anonymously");

        assertEquals(
                "Untouched Book",
                bookRepository.findById(book.getId()).orElseThrow().getTitle(),
                "Anonymous PUT must not edit the book"
        );

        assertTrue(
                bookRepository.existsById(book.getId()),
                "Anonymous DELETE must not remove the book"
        );
    }

    @Test
    void anonymousCannotManageAuthorsAndCategories() throws Exception {

        Author author = newAuthor("Anon Author Fixture");
        Category category = newCategory("Anon Category Fixture");

        long authorsBefore = authorRepository.count();
        long categoriesBefore = categoryRepository.count();

        assertDenied(mockMvc.perform(
                post("/api/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anonymous Author\"}")
        ));

        assertDenied(mockMvc.perform(
                put("/api/authors/" + author.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anonymous Renamed\"}")
        ));

        assertDenied(mockMvc.perform(delete("/api/authors/" + author.getId())));

        assertDenied(mockMvc.perform(
                post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anonymous Category\"}")
        ));

        assertDenied(mockMvc.perform(delete("/api/categories/" + category.getId())));

        assertEquals(authorsBefore, authorRepository.count());
        assertEquals(categoriesBefore, categoryRepository.count());

        assertEquals(
                "Anon Author Fixture",
                authorRepository.findById(author.getId()).orElseThrow().getName()
        );

        assertTrue(categoryRepository.existsById(category.getId()));
    }

    @Test
    void anonymousCannotUploadDigitalResources() throws Exception {

        Author author = newAuthor("Anon Upload Author");
        Category category = newCategory("Anon Upload Category");
        Book book = newBook("Upload Target Book", author, category);

        long before = digitalResourceRepository.count();

        assertDenied(mockMvc.perform(
                multipart("/api/resources")
                        .file(epubFile())
                        .param("bookId", String.valueOf(book.getId()))
                        .param("resourceType", "BOOK")
        ));

        assertEquals(
                before,
                digitalResourceRepository.count(),
                "Anonymous upload must not create a digital resource"
        );
    }

    // =========================================================
    // 3. ORDINARY USER
    // =========================================================

    @Test
    void ordinaryUserCanReadButCannotManageTheCatalogue() throws Exception {

        Author author = newAuthor("User Denied Author");
        Category category = newCategory("User Denied Category");
        Book book = newBook("User Denied Book", author, category);

        long booksBefore = bookRepository.count();
        long resourcesBefore = digitalResourceRepository.count();

        mockMvc.perform(get("/api/books").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("reader@astralib.test"))
                .andExpect(jsonPath("$.role").value("USER"));

        assertDenied(mockMvc.perform(
                post("/api/books")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"User Created","authorId":%d,"categoryId":%d}
                                """.formatted(author.getId(), category.getId()))
        ));

        assertDenied(mockMvc.perform(
                put("/api/books/" + book.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"User Edited\"}")
        ));

        assertDenied(mockMvc.perform(
                delete("/api/books/" + book.getId())
                        .header("Authorization", "Bearer " + userToken)
        ));

        assertDenied(mockMvc.perform(
                multipart("/api/resources")
                        .file(epubFile())
                        .param("bookId", String.valueOf(book.getId()))
                        .header("Authorization", "Bearer " + userToken)
        ));

        assertDenied(mockMvc.perform(
                post("/api/categories")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"User Category\"}")
        ));

        assertEquals(booksBefore, bookRepository.count());
        assertEquals(resourcesBefore, digitalResourceRepository.count());

        assertEquals(
                "User Denied Book",
                bookRepository.findById(book.getId()).orElseThrow().getTitle()
        );
    }

    // =========================================================
    // 4. LIBRARIAN
    // =========================================================

    @Test
    void librarianCanManageTheCatalogue() throws Exception {

        Author author = newAuthor("Librarian Author");
        Category category = newCategory("Librarian Category");

        String createdJson = mockMvc.perform(
                        post("/api/books")
                                .header("Authorization", "Bearer " + librarianToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"title":"Librarian Created Book","authorId":%d,"categoryId":%d,"language":"English"}
                                        """.formatted(author.getId(), category.getId()))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(createdJson.contains("Librarian Created Book"));

        Book created = bookRepository.findAll().stream()
                .filter(b -> "Librarian Created Book".equals(b.getTitle()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Librarian-created book was not persisted"));

        assertNotNull(created.getId());

        mockMvc.perform(
                        put("/api/books/" + created.getId())
                                .header("Authorization", "Bearer " + librarianToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Librarian Edited Book\"}")
                )
                .andExpect(status().isOk());

        assertEquals(
                "Librarian Edited Book",
                bookRepository.findById(created.getId()).orElseThrow().getTitle(),
                "Librarian PUT must persist the edit"
        );

        mockMvc.perform(
                        post("/api/authors")
                                .header("Authorization", "Bearer " + librarianToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Librarian New Author\"}")
                )
                .andExpect(status().isOk());

        assertTrue(
                authorRepository.findAll().stream()
                        .anyMatch(a -> "Librarian New Author".equals(a.getName())),
                "Librarian-created author must be persisted"
        );

        mockMvc.perform(
                        post("/api/categories")
                                .header("Authorization", "Bearer " + librarianToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Librarian New Category\",\"description\":\"created by librarian\"}")
                )
                .andExpect(status().isOk());

        assertTrue(
                categoryRepository.findByName("Librarian New Category").isPresent(),
                "Librarian-created category must be persisted"
        );

        long resourcesBefore = digitalResourceRepository.count();

        mockMvc.perform(
                        multipart("/api/resources")
                                .file(epubFile())
                                .param("bookId", String.valueOf(created.getId()))
                                .param("resourceType", "BOOK")
                                .header("Authorization", "Bearer " + librarianToken)
                )
                .andExpect(status().isOk());

        assertEquals(
                resourcesBefore + 1,
                digitalResourceRepository.count(),
                "Librarian upload must create exactly one digital resource"
        );

        assertFalse(
                digitalResourceRepository.findByBook_Id(created.getId()).isEmpty(),
                "Uploaded resource must be attached to the book"
        );

        mockMvc.perform(
                        delete("/api/books/" + created.getId())
                                .header("Authorization", "Bearer " + librarianToken)
                )
                .andExpect(status().isOk());

        assertFalse(
                bookRepository.existsById(created.getId()),
                "Librarian DELETE must remove the book"
        );
    }

    // =========================================================
    // 5. ADMIN
    // =========================================================

    @Test
    void adminCanManageTheCatalogueAndAdminEndpoints() throws Exception {

        Author author = newAuthor("Admin Author");
        Author deletableAuthor = newAuthor("Admin Deletable Author");
        Category category = newCategory("Admin Category");

        mockMvc.perform(
                        post("/api/books")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"title":"Admin Created Book","authorId":%d,"categoryId":%d}
                                        """.formatted(author.getId(), category.getId()))
                )
                .andExpect(status().isOk());

        Book created = bookRepository.findAll().stream()
                .filter(b -> "Admin Created Book".equals(b.getTitle()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Admin-created book was not persisted"));

        // An author with no books attached, so the FK constraint is not
        // what is being exercised here.
        mockMvc.perform(
                        delete("/api/authors/" + deletableAuthor.getId())
                                .header("Authorization", "Bearer " + adminToken)
                )
                .andExpect(status().isOk());

        assertFalse(authorRepository.existsById(deletableAuthor.getId()));
        assertTrue(authorRepository.existsById(author.getId()));

        // Existing admin-only rules must survive the change.
        mockMvc.perform(
                        get("/api/admin/vector-stats")
                                .header("Authorization", "Bearer " + adminToken)
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/admin/vector-stats")
                                .header("Authorization", "Bearer " + librarianToken)
                )
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/api/admin/vector-stats")
                                .header("Authorization", "Bearer " + userToken)
                )
                .andExpect(status().isForbidden());

        assertTrue(bookRepository.existsById(created.getId()));
    }

    // =========================================================
    // 6. AUTHENTICATION ITSELF
    // =========================================================

    @Test
    void publicAuthenticationEndpointsKeepWorking() throws Exception {

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"reader@astralib.test\",\"password\":\"wrong\"}")
                )
                .andExpect(status().isBadRequest());

        String email = "newreg-" + System.nanoTime() + "@astralib.test";

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"New Reader","email":"%s","password":"%s"}
                                        """.formatted(email, PASSWORD))
                )
                .andExpect(status().isOk());

        User registered = userRepository.findByEmail(email).orElseThrow();

        assertEquals(
                Role.USER,
                registered.getRole(),
                "Self-registration must never grant a privileged role"
        );

        assertTrue(userRepository.existsById(userId));
        assertTrue(userRepository.existsById(adminId));
    }
}
