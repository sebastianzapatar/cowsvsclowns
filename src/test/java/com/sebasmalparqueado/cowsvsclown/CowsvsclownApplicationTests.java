package com.sebasmalparqueado.cowsvsclown;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: boots the whole Spring context and nothing else.
 *
 * <p>The test body is empty on purpose, and that is not an oversight. The
 * assertion is the startup itself: {@code @SpringBootTest} builds every bean in
 * the application, so the test fails if any of them cannot be constructed. That
 * catches a whole family of mistakes the other tests never see, because they
 * mock or slice away the parts that would break:</p>
 *
 * <ul>
 *   <li>A bean that cannot be injected (missing dependency, circular reference).</li>
 *   <li>An entity Hibernate refuses to map (bad annotation, missing no-args
 *       constructor, a relationship pointing nowhere).</li>
 *   <li>A property referenced in a {@code @Value} that no profile defines.</li>
 *   <li>Two beans of the same type with no {@code @Primary} to break the tie.</li>
 * </ul>
 *
 * <p>Those failures only show up when the real context is assembled. Without
 * this test the suite could be fully green and the application still refuse to
 * start in production.</p>
 *
 * <p>It runs on the {@code test} profile, so it uses in-memory H2 and does not
 * need a Postgres running.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class CowsvsclownApplicationTests {

	@Test
	void contextLoads() {
		// Intentionally empty: see the class javadoc. Booting the context IS
		// the assertion.
	}

}
