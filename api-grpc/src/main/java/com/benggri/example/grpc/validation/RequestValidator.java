package com.benggri.example.grpc.validation;

import com.benggri.example.grpc.proto.LoginRequest;
import com.benggri.example.grpc.proto.RefreshRequest;
import com.benggri.example.grpc.proto.RegisterRequest;
import com.benggri.example.grpc.proto.SearchPostsRequest;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The protos carry no validation annotations, so every limit is enforced here (same limits as the REST and
 * GraphQL adapters). A failure names only the field, never the rejected value.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RequestValidator {

    public static final int DEFAULT_PAGE_SIZE = 20;

    static final int MAX_EMAIL = 254;
    static final int MIN_PASSWORD = 8;
    static final int MAX_PASSWORD = 72;
    static final int MAX_LOGIN_PASSWORD = MAX_PASSWORD * 4;
    static final int MAX_NAME = 50;
    static final int MAX_TITLE = 200;
    static final int MAX_CONTENT = 10_000;
    static final int MAX_TOKEN = 4096;

    // linear-time shape check; the length is bounded before it runs
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public static void register(RegisterRequest request) {
        email("email", request.getEmail());
        passwordLength("password", request.getPassword());
        text("name", request.getName(), MAX_NAME);
    }

    public static void login(LoginRequest request) {
        text("email", request.getEmail(), MAX_EMAIL);
        text("password", request.getPassword(), MAX_LOGIN_PASSWORD);
    }

    public static void refresh(RefreshRequest request) {
        text("refresh_token", request.getRefreshToken(), MAX_TOKEN);
    }

    public static void memberName(String name) {
        text("name", name, MAX_NAME);
    }

    public static void post(String title, String content) {
        text("title", title, MAX_TITLE);
        text("content", content, MAX_CONTENT);
    }

    /** proto3 returns 0 for an unset int64, so 0 and negatives are both rejected. */
    public static void id(long id) {
        if (id <= 0) {
            throw new InvalidFieldException("id");
        }
    }

    /** Zero is valid for both (page 0, size 0 = default); only negatives are rejected. */
    public static void search(SearchPostsRequest request) {
        if (request.getPage() < 0) {
            throw new InvalidFieldException("page");
        }
        if (request.getSize() < 0) {
            throw new InvalidFieldException("size");
        }
    }

    private static void text(String field, String value, int max) {
        if (value.isBlank() || value.length() > max) {
            throw new InvalidFieldException(field);
        }
    }

    private static void email(String field, String value) {
        text(field, value, MAX_EMAIL);
        if (!EMAIL.matcher(value).matches()) {
            throw new InvalidFieldException(field);
        }
    }

    /** Characters, not bytes; core additionally rejects more than 72 UTF-8 bytes (BCrypt's limit). */
    private static void passwordLength(String field, String value) {
        int length = value.codePointCount(0, value.length());
        if (value.isBlank() || length < MIN_PASSWORD || length > MAX_PASSWORD) {
            throw new InvalidFieldException(field);
        }
    }
}
