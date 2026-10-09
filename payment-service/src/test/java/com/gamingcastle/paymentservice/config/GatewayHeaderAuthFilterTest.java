package com.gamingcastle.paymentservice.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayHeaderAuthFilterTest {

    @Test
    void shouldRejectRequestWithoutGatewaySecret() throws Exception {
        GatewayHeaderAuthFilter filter = new GatewayHeaderAuthFilter();
        ReflectionTestUtils.setField(filter, "expectedGatewaySecret", "shared-secret");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments");
        request.addHeader("X-User-Id", "11111111-1111-1111-1111-111111111111");
        request.addHeader("X-User-Role", "CUSTOMER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { throw new AssertionError("Filter chain should not run"); };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }
}
