package com.gamingcastle.userservice.util;

public final class APIEndPoints {

    private APIEndPoints() {
    }

    // ---- base paths ----
    public static final String baseAPI = "/api";
    public static final String baseAuthAPI = "/api/auth";

    // ---- reusable path fragments ----
    public static final String id = "/{userId}";

    // ---- auth endpoints ----
    public static final String login = "/login";
    public static final String register = "/register";
    public static final String loginByPhone = login + "/phone";           // FR-03
    public static final String forgotPassword = "/password/forgot";       // FR-04/FR-05
    public static final String resetPassword = "/password/reset";         // FR-04/FR-05
    public static final String reactivateRequest = "/reactivate/request";
    public static final String reactivateConfirm = "/reactivate/confirm";

    // ---- user endpoints ----
    public static final String users = "/users";
    public static final String profile = users + "/me";
    public static final String userById = users + id;
    public static final String deactivateMe = profile + "/deactivate";    // PATCH: user deactivates own account
    public static final String deactivateUser = userById + "/deactivate"; // PATCH: admin deactivates a user
    public static final String activateUser = userById + "/activate";     // PATCH: admin reactivates a user
}
