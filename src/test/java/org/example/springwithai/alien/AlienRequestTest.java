package org.example.springwithai.alien;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Stream;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AlienRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void toEntityCopiesAllFieldsWithoutId() {
        Alien alien = new AlienRequest("Zorg", "Mars", "Green", "Humanoid").toEntity();

        assertThat(alien)
                .extracting(Alien::getId, Alien::getName, Alien::getOrigin, Alien::getColor, Alien::getType)
                .containsExactly(null, "Zorg", "Mars", "Green", "Humanoid");
    }

    @Test
    void completeRequestIsValid() {
        assertThat(validator.validate(new AlienRequest("Zorg", "Mars", "Green", "Humanoid"))).isEmpty();
    }

    @Test
    void constructorStripsSurroundingWhitespace() {
        assertThat(new AlienRequest(" Zorg", "Mars ", "\tGreen\n", "  Humanoid  "))
                .isEqualTo(new AlienRequest("Zorg", "Mars", "Green", "Humanoid"));
    }

    @Test
    void constructorKeepsNullAsNull() {
        assertThat(new AlienRequest(null, null, null, null).name()).isNull();
    }

    @Test
    void valueAtMaxLengthIsValid() {
        String max = "x".repeat(Alien.MAX_LENGTH);

        assertThat(validator.validate(new AlienRequest(max, max, max, max))).isEmpty();
    }

    @Test
    void surroundingWhitespaceDoesNotCountTowardsMaxLength() {
        String padded = " " + "x".repeat(Alien.MAX_LENGTH) + " ";

        assertThat(validator.validate(new AlienRequest(padded, "Mars", "Green", "Humanoid"))).isEmpty();
    }

    static Stream<Arguments> requestsWithOneTooLongField() {
        String tooLong = "x".repeat(Alien.MAX_LENGTH + 1);
        return Stream.of(
                Arguments.of(new AlienRequest(tooLong, "Mars", "Green", "Humanoid"), "name"),
                Arguments.of(new AlienRequest("Zorg", tooLong, "Green", "Humanoid"), "origin"),
                Arguments.of(new AlienRequest("Zorg", "Mars", tooLong, "Humanoid"), "color"),
                Arguments.of(new AlienRequest("Zorg", "Mars", "Green", tooLong), "type"));
    }

    @ParameterizedTest(name = "{1} too long")
    @MethodSource("requestsWithOneTooLongField")
    void valueOverMaxLengthIsRejected(AlienRequest request, String field) {
        assertThat(validator.validate(request))
                .singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath()).hasToString(field));
    }

    static Stream<Arguments> requestsWithOneBlankField() {
        return Stream.of(null, "", " ", "\t").flatMap(blank -> Stream.of(
                Arguments.of(new AlienRequest(blank, "Mars", "Green", "Humanoid"), "name"),
                Arguments.of(new AlienRequest("Zorg", blank, "Green", "Humanoid"), "origin"),
                Arguments.of(new AlienRequest("Zorg", "Mars", blank, "Humanoid"), "color"),
                Arguments.of(new AlienRequest("Zorg", "Mars", "Green", blank), "type")));
    }

    @ParameterizedTest(name = "{1} blank in {0}")
    @MethodSource("requestsWithOneBlankField")
    void blankFieldIsRejected(AlienRequest request, String field) {
        Set<ConstraintViolation<AlienRequest>> violations = validator.validate(request);

        assertThat(violations)
                .singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath()).hasToString(field));
    }
}
