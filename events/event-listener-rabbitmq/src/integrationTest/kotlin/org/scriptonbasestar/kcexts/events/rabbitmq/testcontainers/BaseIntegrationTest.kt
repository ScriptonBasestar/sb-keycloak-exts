package org.scriptonbasestar.kcexts.events.rabbitmq.testcontainers

import org.junit.jupiter.api.BeforeAll
import org.slf4j.LoggerFactory
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
abstract class BaseIntegrationTest {
    companion object {
        private val logger = LoggerFactory.getLogger(BaseIntegrationTest::class.java)

        @JvmStatic
        @BeforeAll
        fun setupContainers() {
            logger.info("Setting up TestContainers for integration tests")

            // Docker 환경 확인
            try {
                val dockerVersion =
                    ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
                        .start()
                        .inputStream
                        .bufferedReader()
                        .readText()
                        .trim()
                logger.info("Docker version detected: $dockerVersion")
            } catch (e: Exception) {
                logger.error("Docker is not available or not running", e)
                throw RuntimeException("Docker is required for integration tests", e)
            }

            logger.info("TestContainers setup completed successfully")
        }
    }
}
