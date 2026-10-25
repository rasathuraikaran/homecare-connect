package com.homecare.config;

import com.homecare.auth.AuthDtos.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorResponse(e.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> describe(f.getField(), f.getDefaultMessage()))
                .distinct()
                .toList();
        return ResponseEntity.badRequest().body(new ErrorResponse(String.join(". ", errors), errors));
    }

    private static final Map<String, String> RATE_LABELS = Map.of(
            "HOURLY", "Per hour charge", "SESSION", "Per session charge",
            "DAILY", "Per day charge", "FULL_DAY_NIGHT", "Full day & night charge");
    private static final Pattern RATE_FIELD = Pattern.compile("^rates\\[(\\w+)].*");

    /** Messages that already read as a sentence are kept; short ones like "must not be blank" get the field name in front. */
    private static String describe(String field, String message) {
        if (message == null || message.isBlank()) message = "is invalid";
        Matcher rate = RATE_FIELD.matcher(field);
        if (rate.matches()) {
            return RATE_LABELS.getOrDefault(rate.group(1), "Charge") + " " + message;
        }
        if (Character.isUpperCase(message.charAt(0))) return message;
        String words = field.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
        return Character.toUpperCase(words.charAt(0)) + words.substring(1) + " " + message;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413).body(new ErrorResponse("Photo is too large (max 2 MB)"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Invalid request. Check the values you entered."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> badParam(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Invalid value for " + e.getName()));
    }
}
