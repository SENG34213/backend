package com.gamingcastle.userservice.service;

import com.gamingcastle.userservice.exception.NotificationDeliveryException;
import com.gamingcastle.userservice.util.PhoneNumberUtil;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * FR-05: sends the password-reset code by SMS via Twilio. Requires
 * twilio.account-sid / twilio.auth-token / twilio.from-number to be set
 * (see application.yml) and TwilioConfig to have run Twilio.init() at
 * startup. Locally-formatted numbers are normalized to E.164 before send —
 * Twilio rejects anything else.
 */
@Service
public class TwilioSmsServiceImpl implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsServiceImpl.class);

    private final String fromNumber;
    private final String defaultCountryCode;

    public TwilioSmsServiceImpl(@Value("${twilio.from-number}") String fromNumber,
                                @Value("${twilio.default-country-code}") String defaultCountryCode) {
        this.fromNumber = fromNumber;
        this.defaultCountryCode = defaultCountryCode;
    }

    @Override
    public void send(String toPhoneNumber, String message) {
        String e164 = PhoneNumberUtil.toE164(toPhoneNumber, defaultCountryCode);
        try {
            Message.creator(new PhoneNumber(e164), new PhoneNumber(fromNumber), message).create();
            log.info("Password reset SMS dispatched to {}", e164);
        } catch (ApiException ex) {
            log.error("Failed to send password reset SMS to {}: {}", e164, ex.getMessage());
            throw new NotificationDeliveryException("Unable to send verification SMS", ex);
        }
    }
}
