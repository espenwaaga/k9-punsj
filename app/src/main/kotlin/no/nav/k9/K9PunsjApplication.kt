package no.nav.k9

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.zaxxer.hikari.HikariDataSource
import no.nav.k9.db.DbConfiguration
import no.nav.k9.db.hikariConfig
import org.springframework.beans.factory.annotation.Autowired
import de.huxhorn.sulky.ulid.ULID
import no.nav.k9.jackson.UlidDeserializer
import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import javax.sql.DataSource

@SpringBootApplication(exclude = [ErrorMvcAutoConfiguration::class, FlywayAutoConfiguration::class])
class K9PunsjApplication @Autowired constructor(var dbConfiguration: DbConfiguration) {
	@Bean
	fun objectMapperBuilder(): Jackson2ObjectMapperBuilder {
		return Jackson2ObjectMapperBuilder()
				.propertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
				.modulesToInstall(JavaTimeModule())
				.featuresToDisable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
				.deserializerByType(ULID.Value::class.java, UlidDeserializer())
	}

	@Bean
	fun reactiveWebServerFactory(): ReactiveWebServerFactory {
		return NettyReactiveWebServerFactory()
	}

	@Bean
	@Profile("!test")
	fun databaseInitializer(): DataSource {
		return hikariConfig(dbConfiguration)
	}
}

fun main(args: Array<String>) {
	runApplication<K9PunsjApplication>(*args) {
		setBannerMode(Banner.Mode.OFF)
	}
}