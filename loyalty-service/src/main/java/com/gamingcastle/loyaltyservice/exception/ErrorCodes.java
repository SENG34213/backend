package com.gamingcastle.loyaltyservice.exception;

public final class ErrorCodes {

    private ErrorCodes() {
    }

    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String LOYALTY_ACCOUNT_NOT_FOUND = "LOYALTY_ACCOUNT_NOT_FOUND";
    public static final String LOYALTY_RULES_NOT_FOUND = "LOYALTY_RULES_NOT_FOUND";
    public static final String INSUFFICIENT_POINTS = "INSUFFICIENT_POINTS";
    public static final String BELOW_MINIMUM_REDEMPTION = "BELOW_MINIMUM_REDEMPTION";
    public static final String INVALID_REDEMPTION_STEP = "INVALID_REDEMPTION_STEP";
    public static final String REDEMPTION_LIMIT_EXCEEDED = "REDEMPTION_LIMIT_EXCEEDED";
    public static final String INVALID_LOYALTY_REQUEST = "INVALID_LOYALTY_REQUEST";
    public static final String FORBIDDEN_MESSAGE = "You do not have permission to perform this action";

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";
    public static final String MISSING_PARAMETER = "MISSING_PARAMETER";
    public static final String MISSING_HEADER = "MISSING_HEADER";
    public static final String TYPE_MISMATCH = "TYPE_MISMATCH";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String DATA_INTEGRITY_VIOLATION = "DATA_INTEGRITY_VIOLATION";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String UNAUTHORIZED_MESSAGE = "Authentication is required to access this resource";
    public static final String INTERNAL_ERROR_MESSAGE = "An unexpected error occurred. Please try again later";
}
