package com.gamingcastle.userservice.exception;

public class AccountDeactivatedByAdminException extends RuntimeException {
    public AccountDeactivatedByAdminException() {
        this("Your account has been deactivated by an administrator. Please contact support to get it reactivated");
    }
    public AccountDeactivatedByAdminException(String message) {
        super(message);
    }
}
