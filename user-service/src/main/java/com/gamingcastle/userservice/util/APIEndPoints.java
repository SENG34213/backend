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

    // ---- user endpoints ----
    public static final String user = "user";
    public static final String users = "users";
    public static final String profile = user + "/me";
    public static final String userById = user + id;
}
