package com.gamingcastle.paymentservice.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMethodJacksonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeLegacyCardAndOnlineValues() throws Exception {
        assertThat(objectMapper.readValue("\"CARD\"", PaymentMethod.class)).isEqualTo(PaymentMethod.CARD);
        assertThat(objectMapper.readValue("\"ONLINE\"", PaymentMethod.class)).isEqualTo(PaymentMethod.ONLINE);
        assertThat(objectMapper.readValue("\"CASH\"", PaymentMethod.class)).isEqualTo(PaymentMethod.CASH);
    }
}
