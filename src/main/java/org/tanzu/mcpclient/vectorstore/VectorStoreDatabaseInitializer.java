package org.tanzu.mcpclient.vectorstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Initializes the vector store database by dropping and recreating the table if needed.
 * This ensures the table schema matches the current embedding dimensions.
 */
@Component
@ConditionalOnProperty(name = "app.vectorstore.reinit", havingValue = "true")
public class VectorStoreDatabaseInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreDatabaseInitializer.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        logger.info("Initializing vector store database - dropping and recreating table");
        
        try {
            // Drop the vector_store table if it exists
            jdbcTemplate.execute("DROP TABLE IF EXISTS public.vector_store CASCADE");
            logger.info("Dropped existing vector_store table");
            
            // Drop the index if it exists
            jdbcTemplate.execute("DROP INDEX IF EXISTS vector_store_embedding_idx");
            logger.info("Dropped existing vector_store index");
            
            logger.info("Vector store table will be recreated by PgVectorStore.builder()");
        } catch (Exception e) {
            logger.error("Error initializing vector store database", e);
        }
    }
}

