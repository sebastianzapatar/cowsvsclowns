package com.sebasmalparqueado.cowsvsclown.common.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the three business exceptions.
 *
 * <p>They have little code, but two things do matter and are asserted here: that the
 * message reaches {@code getMessage()} untouched (the handler forwards it as-is to the
 * client, so if it is lost here the API answers with an empty message), and that they
 * are unchecked, which is what lets Spring roll back the transaction without services
 * declaring {@code throws}.</p>
 *
 * <p>The {@code isUnchecked} assertions look trivial and are not. Spring's
 * declarative rollback only triggers on {@code RuntimeException} by default: if
 * one of these ever stopped extending it, a service that threw mid-transaction
 * would <em>commit</em> the partial work instead of undoing it, and no other
 * test in the suite would notice. That is a data-corruption bug hiding behind a
 * one-word change in a class declaration.</p>
 *
 * <p>Grouped with {@code @Nested} so the report reads one block per exception
 * instead of nine flat method names.</p>
 */
class BusinessExceptionsTest {

    /**
     * The 404 of the API. It is the only one with a factory, because "there is
     * no X with id Y" is written in a dozen places and the wording has to match
     * everywhere — the message goes straight to the client.
     */
    @Nested
    @DisplayName("ResourceNotFoundException")
    class ResourceNotFound {

        @Test
        @DisplayName("keeps the message it was built with")
        void keepsMessage() {
            ResourceNotFoundException e =
                    new ResourceNotFoundException("The clown is not in the database");

            assertEquals("The clown is not in the database", e.getMessage());
        }

        @Test
        @DisplayName("of() builds the standard message with a numeric id")
        void ofBuildsStandardMessageWithLong() {
            ResourceNotFoundException e = ResourceNotFoundException.of("Owner", 42L);

            assertEquals("There is no Owner with id 42", e.getMessage());
        }

        /**
         * The factory takes {@code Object}, so both id types in the project have
         * to render correctly: {@code Long} for owners and {@code UUID} for cows
         * and clowns. Tested separately because a formatting change that broke
         * only the UUID case would otherwise slip past the Long test.
         */
        @Test
        @DisplayName("of() also works with a UUID")
        void ofBuildsStandardMessageWithUuid() {
            UUID id = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

            ResourceNotFoundException e = ResourceNotFoundException.of("Cow", id);

            assertEquals("There is no Cow with id 3fa85f64-5717-4562-b3fc-2c963f66afa6",
                    e.getMessage());
        }

        /**
         * Guards against the exception itself throwing. A null id reaching here
         * already means something went wrong upstream; if formatting the message
         * blew up with a NullPointerException, the client would get a 500 with no
         * detail instead of the 404 that explains the actual problem — and the
         * real cause would be buried under a stack trace from the error path.
         */
        @Test
        @DisplayName("of() does not break with a null id")
        void ofHandlesNullId() {
            assertEquals("There is no Cow with id null",
                    ResourceNotFoundException.of("Cow", null).getMessage());
        }

        @Test
        @DisplayName("is unchecked so it does not force throws and rolls back the transaction")
        void isUnchecked() {
            // See the class javadoc: this is what keeps @Transactional rolling
            // back instead of committing half-finished work.
            assertInstanceOf(RuntimeException.class, ResourceNotFoundException.of("Cow", 1));
        }
    }

    /**
     * The 400 for a <em>business</em> rule, as opposed to the 400 Spring raises
     * on its own when {@code @Valid} rejects a field. Same status code, different
     * origin: this one is thrown when the body is perfectly well-formed but what
     * it asks for makes no sense.
     *
     * <p>No factory here — unlike "not found", these messages are one-offs that
     * describe the specific rule that was broken.</p>
     */
    @Nested
    @DisplayName("BadRequestException")
    class BadRequest {

        @Test
        @DisplayName("keeps the message it was built with")
        void keepsMessage() {
            // The message survives verbatim because the handler forwards it to
            // the client untouched. Unlike the 500 path, nothing is sanitised
            // here: these texts are written to be read by whoever called the API.
            BadRequestException e =
                    new BadRequestException("cannot assign an inactive cow to a clown");

            assertEquals("cannot assign an inactive cow to a clown", e.getMessage());
        }

        @Test
        @DisplayName("is unchecked")
        void isUnchecked() {
            assertInstanceOf(RuntimeException.class, new BadRequestException("x"));
        }
    }

    /**
     * The 409. Thrown when the request collides with the state that is already
     * there — duplicate names, mostly.
     *
     * <p>It is worth separating from {@code BadRequestException}: a 400 says
     * "this request is wrong", a 409 says "this request would have been fine
     * earlier". The client can retry a 409 with different data; a 400 usually
     * means a bug on its side.</p>
     */
    @Nested
    @DisplayName("ConflictException")
    class Conflict {

        @Test
        @DisplayName("keeps the message it was built with")
        void keepsMessage() {
            ConflictException e = new ConflictException("There is already a cow named Lola");

            assertEquals("There is already a cow named Lola", e.getMessage());
        }

        @Test
        @DisplayName("is unchecked")
        void isUnchecked() {
            assertInstanceOf(RuntimeException.class, new ConflictException("x"));
        }
    }
}
