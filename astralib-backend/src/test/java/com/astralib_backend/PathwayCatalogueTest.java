package com.astralib_backend;

import com.astralib.backend.Author;
import com.astralib.backend.AuthorRepository;
import com.astralib.backend.Book;
import com.astralib.backend.BookRepository;
import com.astralib.backend.Category;
import com.astralib.backend.CategoryRepository;
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
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Pathway browsing and catalogue-level filtering.
 *
 * The curated collection is data, but the structure that makes it
 * navigable (sequence, level, prerequisite) is application behaviour,
 * so it is covered here against the isolated H2 database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PathwayCatalogueTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "TestPassword123!";

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BookRepository bookRepository;
    @Autowired private AuthorRepository authorRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PathwayRepository pathwayRepository;
    @Autowired private PathwayBookRepository pathwayBookRepository;

    private MockMvc mockMvc;

    private String slug;
    private Long firstBookId;
    private Long secondBookId;
    private Long outsiderBookId;

    @BeforeAll
    void setUp() throws Exception {

        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        Author author = authorRepository.save(new Author("Pathway Test Author", null));
        Category category = categoryRepository.save(new Category("Pathway Test Genre", null));

        Book first = newBook("Pathway First Book", author, category);
        Book second = newBook("Pathway Second Book", author, category);
        Book outsider = newBook("Unplaced Book", author, category);

        firstBookId = first.getId();
        secondBookId = second.getId();
        outsiderBookId = outsider.getId();

        Pathway pathway = new Pathway();
        pathway.setName("Test Learning Path");
        pathway.setSlug("test-learning-path");
        pathway.setType(Pathway.TYPE_LEARNING);
        pathway.setSubject("Testing");
        pathway.setDescription("Fixture pathway");
        pathway = pathwayRepository.save(pathway);

        slug = pathway.getSlug();

        PathwayBook beginner = new PathwayBook();
        beginner.setPathway(pathway);
        beginner.setBook(first);
        beginner.setPosition(1);
        beginner.setLevel(PathwayBook.LEVEL_BEGINNER);
        beginner.setRationale("Starting point");
        beginner.setLearningOutcome("Explain the first step");
        pathwayBookRepository.save(beginner);

        PathwayBook advanced = new PathwayBook();
        advanced.setPathway(pathway);
        advanced.setBook(second);
        advanced.setPosition(2);
        advanced.setLevel(PathwayBook.LEVEL_ADVANCED);
        advanced.setPrerequisiteBook(first);
        advanced.setRationale("Builds on the first book");
        pathwayBookRepository.save(advanced);
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

    @Test
    void anonymousReadersCanBrowsePathways() throws Exception {

        mockMvc.perform(get("/api/pathways"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/pathways/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.books.length()").value(2))
                .andExpect(jsonPath("$.books[0].title").value("Pathway First Book"))
                .andExpect(jsonPath("$.books[0].level").value("BEGINNER"))
                .andExpect(jsonPath("$.books[0].position").value(1))
                .andExpect(jsonPath("$.books[1].level").value("ADVANCED"))
                .andExpect(jsonPath("$.books[1].prerequisite.title").value("Pathway First Book"))
                .andExpect(jsonPath("$.books[1].rationale").value("Builds on the first book"));
    }

    @Test
    void unknownPathwayReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/pathways/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void catalogueFiltersByLevelAndPathway() throws Exception {

        String byLevel = mockMvc.perform(get("/api/books").param("level", "ADVANCED"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(byLevel.contains("Pathway Second Book"), "ADVANCED filter must include its member");
        assertTrue(!byLevel.contains("Pathway First Book"), "ADVANCED filter must exclude BEGINNER books");

        String byPathway = mockMvc.perform(get("/api/books").param("pathway", slug))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(byPathway.contains("Pathway First Book"));
        assertTrue(byPathway.contains("Pathway Second Book"));
        assertTrue(!byPathway.contains("Unplaced Book"), "Books outside the pathway must be excluded");
    }

    @Test
    void bookMembershipsAndGenreFiltersWork() throws Exception {

        mockMvc.perform(get("/api/pathways/by-book/" + firstBookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value(slug))
                .andExpect(jsonPath("$[0].level").value("BEGINNER"));

        assertEquals(
                0,
                pathwayBookRepository.findByBook_Id(outsiderBookId).size(),
                "A book outside every pathway must have no memberships"
        );

        // Genre labels are stored separately from the single category.
        Book outsider = bookRepository.findById(outsiderBookId).orElseThrow();
        outsider.setGenres(List.of("Pathway Test Genre", "Extra Genre"));
        bookRepository.save(outsider);

        String byGenre = mockMvc.perform(get("/api/books").param("genre", "Extra Genre"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(byGenre.contains("Unplaced Book"));
        assertTrue(!byGenre.contains("Pathway First Book"));
    }

    @Test
    void onlyAdminsCanApplyTheCurationManifest() throws Exception {

        User user = userRepository.findByEmail("pathway-reader@astralib.test")
                .orElseGet(() -> {
                    User created = new User(
                            "Pathway Reader",
                            "pathway-reader@astralib.test",
                            passwordEncoder.encode(PASSWORD)
                    );
                    created.setRole(Role.USER);
                    return userRepository.save(created);
                });

        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(user.getEmail(), PASSWORD);

        String response = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int start = response.indexOf("\"token\":\"") + "\"token\":\"".length();
        String token = response.substring(start, response.indexOf('"', start));

        mockMvc.perform(post("/api/admin/catalogue/seed")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/catalogue/seed"))
                .andExpect(status().is4xxClientError());
    }
}
