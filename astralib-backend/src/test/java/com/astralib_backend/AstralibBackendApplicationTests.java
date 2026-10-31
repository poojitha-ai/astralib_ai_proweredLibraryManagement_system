package com.astralib_backend;

import org.junit.jupiter.api.Test;

import com.astralib.backend.BookRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;

/**
 * Verifies that the application context starts cleanly and, more
 * importantly, that it starts against disposable test infrastructure.
 *
 * This test used to live in the com.astralib_backend package with a bare
 * @SpringBootTest. Spring Boot searches upward from the test's package for
 * a @SpringBootConfiguration, and the application class lives in
 * com.astralib.backend, so the search fell off the top of the package tree
 * and the context could never be found. The application class is now named
 * explicitly through AbstractIntegrationTest(classes = ...), which keeps the
 * production package untouched.
 */
/*
 * This class observes what the application does while starting up (the
 * dataset loader runs as a startup runner). A cached context would have
 * started before this class ran, and Spring resets @MockitoBean mocks
 * between test classes, so the startup interaction has to be watched on a
 * context this class owns: the context is rebuilt here, every assertion
 * still holds, and the test no longer depends on class execution order.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AstralibBackendApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private BookRepository bookRepository;

    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;

    @Test
    void contextLoads() {
        assertNotNull(dataSource);
    }

    @Test
    void runsAgainstTheDisposableInMemoryDatabase() throws Exception {

        String url;

        try (Connection connection = dataSource.getConnection()) {
            url = connection.getMetaData().getURL();
        }

        assertTrue(
                url.startsWith("jdbc:h2:mem:"),
                "Tests must run on in-memory H2, but connected to: " + url
        );

        assertFalse(
                url.contains("3306") || url.contains("/astralib"),
                "Tests must never reach the development MySQL database: " + url
        );

        assertEquals(
                "create-drop",
                ddlAuto,
                "Hibernate must only create/drop the disposable test schema"
        );
    }

    /**
     * The initData runner does invoke the dataset loader on startup, so the
     * guarantee is not that it is never called, but that it is the mock:
     * the real loader would read ../data and write catalogue rows.
     */
    @Test
    void startupUsesTheMockedDatasetLoaderAndImportsNothing() {

        assertTrue(
                mockingDetails(datasetLoaderService).isMock(),
                "DatasetLoaderService must be replaced by a mock in tests"
        );

        verify(datasetLoaderService, atLeastOnce()).loadAllDatasets();

        assertEquals(
                0L,
                bookRepository.count(),
                "Startup must not import catalogue rows into the test database"
        );
    }
}
