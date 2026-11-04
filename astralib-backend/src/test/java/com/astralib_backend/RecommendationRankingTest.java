package com.astralib_backend;

import com.astralib.backend.Author;
import com.astralib.backend.AuthorRepository;
import com.astralib.backend.Book;
import com.astralib.backend.BookRepository;
import com.astralib.backend.CatalogueRecommendationService;
import com.astralib.backend.Category;
import com.astralib.backend.CategoryRepository;
import com.astralib.backend.DigitalResource;
import com.astralib.backend.DigitalResourceRepository;
import com.astralib.backend.Pathway;
import com.astralib.backend.PathwayBook;
import com.astralib.backend.PathwayBookRepository;
import com.astralib.backend.PathwayRepository;
import com.astralib.backend.QueryUnderstandingService;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ordering of book recommendations for a level-and-language request.
 *
 * "Find an easy Python book" used to answer with a metadata-only title while
 * two readable Python textbooks sat in the collection: their language lives in
 * a subject phrase ("Python (Computer program language)") that the language
 * matcher did not read, so they were filtered out before ranking.
 *
 * The rating ladder is checked here as behaviour: readable at the requested
 * level first, then a readable neighbouring level, then a catalogue-only title
 * at the requested level, and an advanced book only with the mismatch stated.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecommendationRankingTest extends AbstractIntegrationTest {

    @Autowired private BookRepository bookRepository;
    @Autowired private AuthorRepository authorRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PathwayRepository pathwayRepository;
    @Autowired private PathwayBookRepository pathwayBookRepository;
    @Autowired private DigitalResourceRepository digitalResourceRepository;
    @Autowired private QueryUnderstandingService queryUnderstandingService;
    @Autowired private CatalogueRecommendationService recommendationService;

    private List<CatalogueRecommendationService.BookRecommendation> pythonBooks;

    @BeforeAll
    void setUp() {

        Author author = authorRepository.save(new Author("Ranking Test Author", null));
        Category category = categoryRepository.save(new Category("Programming", null));

        Pathway pathway = new Pathway();
        pathway.setName("Ranking Test Python");
        pathway.setSlug("ranking-test-python");
        pathway.setType(Pathway.TYPE_LEARNING);
        pathway.setSubject("Programming");
        pathway.setDescription("Fixture pathway for recommendation ordering");
        pathway = pathwayRepository.save(pathway);

        /* Readable beginner: the language is only named in a subject phrase. */
        Book readableBeginner = newBook(
                "Open Python Textbook",
                author,
                category,
                List.of("Python (Computer program language)")
        );
        readableBeginner.setCatalogueEntry(false);
        addStoredResource(readableBeginner, "data/fulltext/python/open_python.epub");
        place(pathway, readableBeginner, "BEGINNER", 1);

        /* Catalogue-only beginner: correct level, nothing to read. */
        Book catalogueOnlyBeginner = newBook(
                "Metadata Only Python Book",
                author,
                category,
                List.of("Python (Computer program language)")
        );
        catalogueOnlyBeginner.setCatalogueEntry(true);
        place(pathway, catalogueOnlyBeginner, "BEGINNER", 2);

        /* Readable advanced: cannot be the answer for a beginner. */
        Book readableAdvanced = newBook(
                "Advanced Python Handbook",
                author,
                category,
                List.of("Python (Computer program language)")
        );
        readableAdvanced.setCatalogueEntry(true);
        addStoredResource(readableAdvanced, "data/fulltext/python/advanced_python.pdf");
        place(pathway, readableAdvanced, "ADVANCED", 3);

        QueryUnderstandingService.QueryPlan plan =
                queryUnderstandingService.plan("find an easy python book");

        pythonBooks = recommendationService.recommend(plan, 6, false);
    }

    @Test
    void readableBeginnerBookIsTheFirstRecommendation() {

        assertFalse(pythonBooks.isEmpty(), "the Python pathway must produce recommendations");

        CatalogueRecommendationService.BookRecommendation first = pythonBooks.get(0);

        assertEquals("Open Python Textbook", first.title());
        assertEquals("BEGINNER", first.level());
        assertEquals("Python", first.language());
        assertTrue(first.readable(), "the first recommendation must be readable");
    }

    @Test
    void catalogueOnlyBeginnerComesBeforeTheAdvancedAlternative() {

        List<String> titles = pythonBooks.stream()
                .map(CatalogueRecommendationService.BookRecommendation::title)
                .toList();

        int catalogueOnly = titles.indexOf("Metadata Only Python Book");
        int advanced = titles.indexOf("Advanced Python Handbook");

        assertTrue(catalogueOnly >= 0, "the catalogue-only beginner title must still be offered");
        assertTrue(
                catalogueOnly < advanced,
                "an unavailable beginner title is a better answer than an advanced book: " + titles
        );
    }

    @Test
    void advancedBookExplainsTheLevelMismatch() {

        CatalogueRecommendationService.BookRecommendation advanced = pythonBooks.stream()
                .filter(book -> "Advanced Python Handbook".equals(book.title()))
                .findFirst()
                .orElseThrow();

        assertTrue(
                advanced.reason().toLowerCase().contains("above the beginner level"),
                "the mismatch must be stated, not implied: " + advanced.reason()
        );

        assertTrue(
                advanced.reason().toLowerCase().contains("advanced"),
                "the reason must name the level it actually has: " + advanced.reason()
        );
    }

    private Book newBook(
            String title,
            Author author,
            Category category,
            List<String> subjects) {

        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(author);
        book.setCategory(category);
        book.setSubjects(subjects);
        book.setDescription("Fixture record");
        return bookRepository.save(book);
    }

    private void addStoredResource(Book book, String path) {

        /*
         * isOpenable() asks for a resource identifier as well as a stored
         * file: an upload is attached to the record before it has one.
         */
        book.setResourceId("RANKING-" + book.getId());
        bookRepository.save(book);

        DigitalResource resource = new DigitalResource();
        resource.setBook(book);
        resource.setResourceType("book");
        resource.setFileName(path.substring(path.lastIndexOf('/') + 1));
        resource.setFilePath(path);
        resource.setFormat(path.endsWith(".pdf") ? "application/pdf" : "application/epub+zip");
        digitalResourceRepository.save(resource);
    }

    private void place(Pathway pathway, Book book, String level, int position) {

        PathwayBook membership = new PathwayBook();
        membership.setPathway(pathway);
        membership.setBook(book);
        membership.setLevel(level);
        membership.setPosition(position);
        pathwayBookRepository.save(membership);
    }
}
