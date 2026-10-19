package com.gamingcastle.notificationservice.service;

import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwilioSmsServiceImplTest {

    private TwilioSmsServiceImpl smsService;

    private final String fromNumber = "+1234567890";
    private final String defaultCountryCode = "+94";

    @BeforeEach
    void setUp() {
        smsService = new TwilioSmsServiceImpl(fromNumber, defaultCountryCode);
    }

    @Test
    @DisplayName("send: normalizes local number starting with 0 to E.164 and creates Twilio message")
    void send_shouldNormalizeLocalNumberWithLeadingZero_andSendMessage() {
        try (MockedStatic<Message> mockedMessage = mockStatic(Message.class)) {
            MessageCreator creator = mock(MessageCreator.class);
            Message mockMsg = mock(Message.class);

            mockedMessage.when(() -> Message.creator(
                    eq(new PhoneNumber("+94771234567")),
                    eq(new PhoneNumber(fromNumber)),
                    eq("Your code is 123456")
            )).thenReturn(creator);

            when(creator.create()).thenReturn(mockMsg);

            smsService.send("0771234567", "Your code is 123456");

            verify(creator).create();
        }
    }

    @Test
    @DisplayName("send: keeps E.164 number when already prefixed with +")
    void send_shouldKeepE164Number_whenAlreadyPrefixedWithPlus() {
        try (MockedStatic<Message> mockedMessage = mockStatic(Message.class)) {
            MessageCreator creator = mock(MessageCreator.class);
            Message mockMsg = mock(Message.class);

            mockedMessage.when(() -> Message.creator(
                    eq(new PhoneNumber("+14155552671")),
                    eq(new PhoneNumber(fromNumber)),
                    eq("Your code is 123456")
            )).thenReturn(creator);

            when(creator.create()).thenReturn(mockMsg);

            smsService.send("+14155552671", "Your code is 123456");

            verify(creator).create();
        }
    }

    @Test
    @DisplayName("send: prefixes with default country code when number starts without 0 or +")
    void send_shouldPrefixDefaultCountryCode_whenNoLeadingZeroOrPlus() {
        try (MockedStatic<Message> mockedMessage = mockStatic(Message.class)) {
            MessageCreator creator = mock(MessageCreator.class);
            Message mockMsg = mock(Message.class);

            mockedMessage.when(() -> Message.creator(
                    eq(new PhoneNumber("+94771234567")),
                    eq(new PhoneNumber(fromNumber)),
                    eq("Hello")
            )).thenReturn(creator);

            when(creator.create()).thenReturn(mockMsg);

            smsService.send("771234567", "Hello");

            verify(creator).create();
        }
    }

    @Test
    @DisplayName("send: throws RuntimeException when Twilio throws ApiException")
    void send_shouldThrowRuntimeException_whenApiExceptionOccurs() {
        try (MockedStatic<Message> mockedMessage = mockStatic(Message.class)) {
            MessageCreator creator = mock(MessageCreator.class);

            mockedMessage.when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
                    .thenReturn(creator);

            when(creator.create()).thenThrow(new ApiException("Invalid Phone Number"));

            assertThatThrownBy(() -> smsService.send("+1234567890", "Test message"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Unable to send verification SMS");
        }
    }
}
