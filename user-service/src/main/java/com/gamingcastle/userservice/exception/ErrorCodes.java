package com.gamingcastle.userservice.exception;

public final class ErrorCodes {

    private ErrorCodes() {
        // utility class - no instances
    }

    // --- auth ---
    public static final String EMAIL_TAKEN = "EMAIL_TAKEN";
    public static final String PHONE_TAKEN = "PHONE_TAKEN";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String INVALID_RESET_CODE = "INVALID_RESET_CODE";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";

    // --- user ---
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String USER_ALREADY_DEACTIVATED = "USER_ALREADY_DEACTIVATED";
    public static final String FORBIDDEN = "FORBIDDEN";

    // --- generic / request level ---
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";
    public static final String MISSING_PARAMETER = "MISSING_PARAMETER";
    public static final String MISSING_HEADER = "MISSING_HEADER";
    public static final String TYPE_MISMATCH = "TYPE_MISMATCH";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String DATA_INTEGRITY_VIOLATION = "DATA_INTEGRITY_VIOLATION";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    // --- default messages shared by more than one class ---
    public static final String UNAUTHORIZED_MESSAGE = "Authentication is required to access this resource";
    public static final String FORBIDDEN_MESSAGE = "You do not have permission to perform this action";
    public static final String INTERNAL_ERROR_MESSAGE = "An unexpected error occurred. Please try again later";
}
