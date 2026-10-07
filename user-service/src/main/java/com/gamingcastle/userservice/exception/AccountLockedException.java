package com.gamingcastle.userservice.exception;

import java.time.Duration;
import java.time.Instant;

public class AccountLockedException extends RuntimeException {

    private final long retryAfterSeconds;
    private final Instant lockedUntil;

    // existing tests/code break aagadhapadi old constructor ah vechukkonga
    public AccountLockedException() {
        super("Account is temporarily locked due to repeated failed login attempts");
        this.retryAfterSeconds = 0;
        this.lockedUntil = null;
    }

    public AccountLockedException(Instant lockedUntil) {
        this(secondsUntil(lockedUntil), lockedUntil);
    }

    private AccountLockedException(long seconds) {
        this(seconds, Instant.now().plusSeconds(seconds));
    }

    private AccountLockedException(long seconds, Instant lockedUntil) {
        super("Account is temporarily locked due to repeated failed login attempts. "
                + "Try again in " + format(seconds) + ".");
        this.retryAfterSeconds = seconds;
        this.lockedUntil = lockedUntil;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    private static long secondsUntil(Instant lockedUntil) {
        long millis = Duration.between(Instant.now(), lockedUntil).toMillis();
        return Math.max(0, (long) Math.ceil(millis / 1000.0));
    }

    private static String format(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;

        if (m == 0) {
            return s + " second" + (s == 1 ? "" : "s");
        }

        return m + " minute" + (m == 1 ? "" : "s")
                + (s > 0 ? " " + s + " seconds" : "");
    }
}