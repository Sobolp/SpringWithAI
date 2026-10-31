package org.example.springwithai.alien;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.example.springwithai.ApiError;
import org.springframework.http.MediaType;

/** Documents the 400 an endpoint returns when its {@code @Valid} request body fails validation. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "400", description = "Invalid request body",
        content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class)))
@interface BadRequestResponse {
}
