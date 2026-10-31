package org.example.springwithai;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OpenAPI schema for error responses. Documentation only: the body itself is written by Spring Boot's default
 * error handling ({@code BasicErrorController}).
 * <p>
 * The four required fields are always present. {@code message}, {@code trace} and {@code errors} appear only when
 * the matching {@code spring.web.error.include-*} setting is on; they are off by default (as in a packaged jar), and
 * {@code spring-boot-devtools} turns them all on under {@code bootRun}. {@code OpenApiDocsTest} checks this schema
 * against real error bodies in both setups.
 */
@Schema(description = "Error body returned for 4xx and 5xx responses.")
public record ApiError(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "When the error occurred",
                example = "2026-10-31T14:31:40.518+00:00")
        OffsetDateTime timestamp,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "HTTP status code", example = "404")
        int status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "HTTP reason phrase", example = "Not Found")
        String error,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Request path", example = "/api/aliens/99")
        String path,

        @Schema(description = "Exception message. Only with spring.web.error.include-message enabled.",
                example = "Alien not found: 99")
        String message,

        @Schema(description = "Stack trace. Only with spring.web.error.include-stacktrace enabled; "
                + "never enable in production.")
        String trace,

        @Schema(description = "Validation errors of a 400 response. "
                + "Only with spring.web.error.include-binding-errors enabled.")
        List<ApiBindingError> errors) {

    /** One validation error, as Spring serializes a {@code FieldError} (or an {@code ObjectError}, without field). */
    @Schema(name = "ApiBindingError", description = "A single validation error.")
    public record ApiBindingError(
            @Schema(description = "Name of the validated object", example = "alienRequest")
            String objectName,

            @Schema(description = "Rejected field; absent for errors on the whole object", example = "name")
            String field,

            @Schema(description = "The rejected value", example = "")
            Object rejectedValue,

            @Schema(description = "Constraint that failed", example = "NotBlank")
            String code,

            @Schema(description = "Message codes for resolving a localized message")
            List<String> codes,

            @Schema(description = "Arguments for the message codes")
            List<Object> arguments,

            @Schema(description = "Whether binding (type conversion) failed, rather than validation")
            Boolean bindingFailure,

            @Schema(description = "Default validation message", example = "must not be blank")
            String defaultMessage) {
    }
}
