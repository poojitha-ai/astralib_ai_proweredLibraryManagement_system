package com.astralib_backend;

import com.astralib.backend.AstralibBackendApplication;
import com.astralib.backend.DatasetLoaderService;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;

/**
 * Base class for tests that need the full Spring application context.
 *
 * Safety guarantees:
 *
 * 1. The datasource is an in-memory H2 database, forced through
 *    DynamicPropertySource. That source has the highest precedence,
 *    so DB_URL / DB_USERNAME in the environment cannot redirect the
 *    tests onto the real MySQL `astralib` database.
 * 2. Hibernate runs with create-drop, so schema changes only ever
 *    touch the disposable in-memory database.
 * 3. DatasetLoaderService is replaced with a mock. The application's
 *    initData runner calls it on startup; mocking it means no dataset
 *    is read from disk or written to the database during tests.
 * 4. The embedding/LLM provider is forced to "none" with an empty API
 *    key, so no test can reach the local Python service or Gemini.
 */
@SpringBootTest(classes = AstralibBackendApplication.class)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /** Mocked so startup never loads or writes catalogue data. */
    @MockitoBean
    protected DatasetLoaderService datasetLoaderService;

    @DynamicPropertySource
    static void isolateTestEnvironment(DynamicPropertyRegistry registry) {

        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:astralib_test;MODE=MySQL;DB_CLOSE_DELAY=-1"
        );

        registry.add(
                "spring.datasource.driver-class-name",
                () -> "org.h2.Driver"
        );

        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        registry.add(
                "astralib.data-root",
                () -> Path.of(
                        System.getProperty("java.io.tmpdir"),
                        "astralib-test-data"
                ).toString()
        );

        // Resource uploads must never land in the real
        // astralib-backend/uploads directory.
        registry.add(
                "astralib.upload-root",
                () -> Path.of(
                        System.getProperty("java.io.tmpdir"),
                        "astralib-test-uploads"
                ).toString()
        );

        // Tripwire: any accidental embedding call fails instead of
        // performing network I/O.
        registry.add("llm.embedding-provider", () -> "none");
        registry.add("llm.local-embedding-url", () -> "http://127.0.0.1:9/embed");
        registry.add("llm.api-key", () -> "");
    }
}
