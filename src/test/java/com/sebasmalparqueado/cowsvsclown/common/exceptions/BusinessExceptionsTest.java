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
 */
class BusinessExceptionsTest {

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

        @Test
        @DisplayName("of() also works with a UUID")
        void ofBuildsStandardMessageWithUuid() {
            UUID id = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

            ResourceNotFoundException e = ResourceNotFoundException.of("Cow", id);

            assertEquals("There is no Cow with id 3fa85f64-5717-4562-b3fc-2c963f66afa6",
                    e.getMessage());
        }

        @Test
        @DisplayName("of() does not break with a null id")
        void ofHandlesNullId() {
            assertEquals("There is no Cow with id null",
                    ResourceNotFoundException.of("Cow", null).getMessage());
        }

        @Test
        @DisplayName("is unchecked so it does not force throws and rolls back the transaction")
        void isUnchecked() {
            assertInstanceOf(RuntimeException.class, ResourceNotFoundException.of("Cow", 1));
        }
    }

    @Nested
    @DisplayName("BadRequestException")
    class BadRequest {

        @Test
        @DisplayName("keeps the message it was built with")
        void keepsMessage() {
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
