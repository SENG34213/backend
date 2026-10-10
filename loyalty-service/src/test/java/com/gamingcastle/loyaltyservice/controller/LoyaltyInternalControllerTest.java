package com.gamingcastle.loyaltyservice.controller;

import com.gamingcastle.loyaltyservice.dto.request.AwardPointsRequest;
import com.gamingcastle.loyaltyservice.dto.request.RedeemPointsRequest;
import com.gamingcastle.loyaltyservice.dto.response.AwardPointsResponse;
import com.gamingcastle.loyaltyservice.dto.response.LoyaltyStatusResponse;
import com.gamingcastle.loyaltyservice.dto.response.RedemptionReservationResponse;
import com.gamingcastle.loyaltyservice.dto.response.ReverseLoyaltyResponse;
import com.gamingcastle.loyaltyservice.service.LoyaltyService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoyaltyInternalControllerTest {

    private final LoyaltyService loyaltyService = mock(LoyaltyService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LoyaltyInternalController(loyaltyService)).build();

    static Stream<Arguments> internalEndpoints() {
        return Stream.of(
                Arguments.of("/api/internal/loyalty/award",
                        "{\"userId\":\"11111111-1111-4111-8111-111111111111\",\"bookingId\":\"22222222-2222-4222-8222-222222222222\",\"amountPaid\":12.5,\"paymentId\":\"33333333-3333-4333-8333-333333333333\"}",
                        "award"),
                Arguments.of("/api/internal/loyalty/redeem/reserve",
                        "{\"userId\":\"11111111-1111-4111-8111-111111111111\",\"bookingId\":\"22222222-2222-4222-8222-222222222222\",\"bookingTotal\":120.00,\"points\":50}",
                        "reserve"),
                Arguments.of("/api/internal/loyalty/redeem/confirm",
                        "{\"bookingId\":\"22222222-2222-4222-8222-222222222222\"}",
                        "confirm"),
                Arguments.of("/api/internal/loyalty/redeem/release",
                        "{\"bookingId\":\"22222222-2222-4222-8222-222222222222\",\"reason\":\"payment failed\"}",
                        "release"),
                Arguments.of("/api/internal/loyalty/reverse",
                        "{\"bookingId\":\"22222222-2222-4222-8222-222222222222\",\"reason\":\"booking cancelled\"}",
                        "reverse")
        );
    }

    @ParameterizedTest
    @MethodSource("internalEndpoints")
    void internalEndpoints_shouldBeMappedAndReachController(String path, String body, String action) throws Exception {
        switch (action) {
            case "award" -> when(loyaltyService.awardPoints(any(AwardPointsRequest.class)))
                    .thenReturn(new AwardPointsResponse(5L, 100L, "BRONZE", false));
            case "reserve" -> when(loyaltyService.reserveRedemption(any(RedeemPointsRequest.class)))
                    .thenReturn(new RedemptionReservationResponse(
                            UUID.fromString("44444444-4444-4444-8444-444444444444"),
                            50L,
                            new BigDecimal("25.00"),
                            new BigDecimal("95.00"),
                            250L));
            case "confirm" -> when(loyaltyService.confirmRedemption(any(UUID.class)))
                    .thenReturn(new LoyaltyStatusResponse("CONFIRMED", 50L, 0L, 250L));
            case "release" -> when(loyaltyService.releaseRedemption(any(UUID.class), eq("payment failed")))
                    .thenReturn(new LoyaltyStatusResponse("RELEASED", 0L, 0L, 250L));
            case "reverse" -> when(loyaltyService.reverseBooking(any(UUID.class), eq("booking cancelled")))
                    .thenReturn(new ReverseLoyaltyResponse(10L, 20L, true, 180L));
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }

        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
