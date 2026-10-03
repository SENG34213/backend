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

    // ---- user endpoints ----
    public static final String users = "/users";
    public static final String profile = users + "/me";
    public static final String userById = users + id;
}
