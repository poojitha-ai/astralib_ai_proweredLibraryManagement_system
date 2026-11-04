
package com.astralib.backend;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("run-embedding-migration")
public class EmbeddingMigrationRunner implements CommandLineRunner {

    private final VectorEmbeddingMigrationService migrationService;

    public EmbeddingMigrationRunner(
            VectorEmbeddingMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Starting local embedding migration...");
        migrationService.migrateAllEmbeddings();
        System.out.println("Local embedding migration finished.");
    }
}

