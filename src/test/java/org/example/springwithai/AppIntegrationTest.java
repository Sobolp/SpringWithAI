package org.example.springwithai;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * Full application on a random port, plus {@code MockMvcTester}. Every class annotated with this shares one cached
 * application context; adding other context configuration (properties, {@code @MockitoBean}, profiles) to a single
 * class gives it a context of its own, so prefer adding it here.
 * <p>
 * A real server is needed because MockMvc does not run Spring Boot's error handling, so error bodies can only be
 * checked over HTTP.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public @interface AppIntegrationTest {
}
