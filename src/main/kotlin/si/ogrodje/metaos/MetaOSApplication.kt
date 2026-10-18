package si.ogrodje.metaos

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.Banner
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment


@SpringBootApplication
// @EnableConfigurationProperties(MetaOSProperties::class)
class MetaOSApplication(
    private val environment: Environment
) {
    private val logger = LoggerFactory.getLogger(MetaOSApplication::class.java)

    @Bean
    fun commandLineRunner(ctx: ApplicationContext): CommandLineRunner = CommandLineRunner {
        val profiles: Array<String> = environment.activeProfiles
        logger.info("Booting system with profiles: ${profiles.joinToString(",")}")
    }
}

fun main(args: Array<String>) {
    runApplication<MetaOSApplication>(*args) {
        setBannerMode(Banner.Mode.OFF)
    }
}


