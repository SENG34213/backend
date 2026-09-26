package com.gamingcastle.userservice.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * FR-05: initializes the Twilio SDK once at startup with the account
 * credentials, so TwilioSmsServiceImpl can call Message.creator(...)
 * directly without wiring credentials through every call site.
 */
@Configuration
public class TwilioConfig {

    private static final Logger log = LoggerFactory.getLogger(TwilioConfig.class);

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @PostConstruct
    public void init() {
        if (accountSid == null || accountSid.isBlank() || authToken == null || authToken.isBlank()) {
            log.warn("Twilio credentials are not configured - FR-05 SMS password reset will fail until "
                    + "twilio.account-sid / twilio.auth-token (TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN env vars) are set.");
            return;
        }
        Twilio.init(accountSid, authToken);
        log.info("Twilio client initialized");
    }
}
