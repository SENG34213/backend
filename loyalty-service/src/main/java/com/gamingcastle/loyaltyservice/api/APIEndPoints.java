package com.gamingcastle.loyaltyservice.api;

public final class APIEndPoints {

    private APIEndPoints() {
    }

    public static final String baseAPI = "/api";
    public static final String baseAdminAPI = "/api/admin";
    public static final String internalAPI = "/api/internal";
    public static final String internalPathPrefix = internalAPI + "/";

    public static final String loyalty = "/loyalty";
    public static final String adminLoyalty = "loyalty/admin";
    public static final String internalLoyalty = "loyalty/internal";

    public static final String userIdPath = "/{userId}";

    public static final String balance = loyalty + "/balance" + userIdPath;
    public static final String history = loyalty + "/history" + userIdPath;
    public static final String calculateRedemption = loyalty + "/calculate-redemption";

    public static final String adminRules = adminLoyalty + "/rules";
    public static final String adminAdjust = adminLoyalty + "/adjust";

    public static final String award = internalLoyalty + "/award";
    public static final String redeemReserve = internalLoyalty + "/redeem/reserve";
    public static final String redeemConfirm = internalLoyalty + "/redeem/confirm";
    public static final String redeemRelease = internalLoyalty + "/redeem/release";
    public static final String reverse = internalLoyalty + "/reverse";
}
