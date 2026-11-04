package com.astralib_backend;

import com.astralib.backend.Author;
import com.astralib.backend.AuthorRepository;
import com.astralib.backend.Book;
import com.astralib.backend.BookRepository;
import com.astralib.backend.Category;
import com.astralib.backend.CategoryRepository;
import com.astralib.backend.DigitalResource;
import com.astralib.backend.Pathway;
import com.astralib.backend.PathwayBook;
import com.astralib.backend.PathwayBookRepository;
import com.astralib.backend.PathwayRepository;
import com.astralib.backend.Role;
import com.astralib.backend.User;
import com.astralib.backend.UserRepository;
import com.astralib.backend.VectorRecord;
import com.astralib.backend.VectorRecordRepository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * The natural-language search endpoint.
 *
 * The bug these cover: \"new book to read and easy to understand\" returned an
 * empty page, because semantic search only ever looked for a passage of prose
 * above a 0.60 cosine floor. A book request has no passage to find; it has to
 * be answered from catalogue metadata, without embeddings at all.
 *
 * The test context deliberately has no embedding provider (see
 * AbstractIntegrationTest), which makes that requirement testable: the
 * recommendation path must still work, and a question that genuinely needs
 * vectors must fail loudly instead of pretending the library holds nothing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SemanticSearchEndpointTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SearchPassword123!";

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BookRepository bookRepository;
    @Autowired private AuthorRepository authorRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PathwayRepository pathwayRepository;
    @Autowired private PathwayBookRepository pathwayBookRepository;
    @Autowired private VectorRecordRepository vectorRecordRepository;

    private MockMvc mockMvc;
    private String token;

    @BeforeAll
    void setUp() throws Exception {

        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        Author author = authorRepository.save(new Author("Search Test Author", null));
        Category category = categoryRepository.save(new Category("Search Test Programming", null));

        Pathway pathway = new Pathway();
        pathway.setName("Search Test Pathway");
        pathway.setSlug("search-test-pathway");
        pathway.setType(Pathway.TYPE_LEARNING);
        pathway.setSubject("Programming");
        pathway.setDescription("Fixture pathway");
        pathway = pathwayRepository.save(pathway);

        /* A beginner Python book that really has a stored edition. */
        Book readable = book("Readable Python Primer", author, category);
        readable.setResourceId("SEARCH-TEST-PY");
        readable.setGenres(List.of("Python"));
        readable.setDescription("An introductory Python text.");

        DigitalResource resource = new DigitalResource();
        resource.setBook(readable);
        resource.setResourceType("BOOK");
        resource.setFileName("readable-python-primer.pdf");
        resource.setFilePath("uploads/readable-python-primer.pdf");
        resource.setFormat("PDF");
        readable.getDigitalResources().add(resource);

        readable = bookRepository.save(readable);

        /*
         * Smart Search calls a book readable only when its passages are in
         * the vector index, so the fixture needs a real indexed chunk.
         */
        vectorRecordRepository.save(new VectorRecord(
                "SEARCH-TEST-PY-CHK-00001",
                "SEARCH-TEST-PY",
                "book",
                "Readable Python Primer",
                "Search Test Author",
                "Search Test Programming",
                "Chapter 1",
                1,
                "A variable is a name that refers to a value. This chapter "
                        + "introduces the assignment statement and the types of "
                        + "data used in Python programs.",
                "[1.0]"
        ));

        PathwayBook beginner = new PathwayBook();
        beginner.setPathway(pathway);
        beginner.setBook(readable);
        beginner.setPosition(1);
        beginner.setLevel(PathwayBook.LEVEL_BEGINNER);
        beginner.setRationale("Starting point");
        pathwayBookRepository.save(beginner);

        /* An advanced Java book with no stored text at all. */
        Book metadataOnly = book("Metadata Only Java", author, category);
        metadataOnly.setGenres(List.of("Java"));
        metadataOnly.setDescription("Catalogued, not digitised.");
        metadataOnly = bookRepository.save(metadataOnly);

        PathwayBook advanced = new PathwayBook();
        advanced.setPathway(pathway);
        advanced.setBook(metadataOnly);
        advanced.setPosition(2);
        advanced.setLevel(PathwayBook.LEVEL_ADVANCED);
        advanced.setRationale("Second step");
        pathwayBookRepository.save(advanced);

        User reader = userRepository.findByEmail("search-endpoint@astralib.test")
                .orElseGet(() -> {
                    User created = new User(
                            "Search Endpoint Reader",
                            "search-endpoint@astralib.test",
                            passwordEncoder.encode(PASSWORD)
                    );
                    created.setRole(Role.USER);
                    return userRepository.save(created);
                });

        MvcResult login = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"%s","password":"%s"}
                                        """.formatted(reader.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        String body = login.getResponse().getContentAsString();
        int start = body.indexOf("\"token\":\"") + "\"token\":\"".length();
        token = body.substring(start, body.indexOf('"', start));
    }

    private Book book(String title, Author author, Category category) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(author);
        book.setCategory(category);
        book.setLanguage("English");
        book.setResourceType("BOOK");
        return book;
    }

    @Test
    void vagueBookRequestIsAnsweredFromTheCatalogueWithoutEmbeddings() throws Exception {

        mockMvc.perform(get("/api/search/semantic")
                        .param("query", "new book to read and easy to understand")
                        .param("topK", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("semantic"))
                .andExpect(jsonPath("$.understanding.intent").value("BOOK_RECOMMENDATION"))
                .andExpect(jsonPath("$.confidence.embedding").value("not-required"))
                .andExpect(jsonPath("$.passages.length()").value(0))
                .andExpect(jsonPath("$.recommendations[*].title")
                        .value(org.hamcrest.Matchers.hasItem("Readable Python Primer")))
                .andExpect(jsonPath("$.recommendations[?(@.title == 'Readable Python Primer')].readable")
                        .value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.recommendations[?(@.title == 'Metadata Only Java')].readable")
                        .value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.notice").exists());
    }

    @Test
    void unavailableBookIsRecommendedButLabelledNotReadable() throws Exception {

        mockMvc.perform(get("/api/search/semantic")
                        .param("query", "recommend an advanced java book")
                        .param("topK", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[?(@.title == 'Metadata Only Java')].level")
                        .value("ADVANCED"))
                .andExpect(jsonPath("$.recommendations[?(@.title == 'Metadata Only Java')].readable")
                        .value(false))
                .andExpect(jsonPath("$.recommendations[?(@.title == 'Metadata Only Java')].format")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void questionNeedingVectorsFailsLoudlyWhenNoEmbeddingProviderExists() throws Exception {

        mockMvc.perform(get("/api/search/semantic")
                        .param("query", "what is a closure in JavaScript")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void semanticSearchIsNotPublic() throws Exception {

        mockMvc.perform(get("/api/search/semantic")
                        .param("query", "new book to read"))
                .andExpect(status().is4xxClientError());
    }
}
