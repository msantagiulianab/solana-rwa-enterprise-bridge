package com.solana.rwa.bridge.simulation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * REST request payload for the pre-flight transaction rehearsal endpoint.
 *
 * @param encodedTransaction base64-encoded serialized Solana wire transaction
 *                           to rehearse against {@code simulateTransaction}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SimulationSubmitRequest(
        @JsonProperty("encodedTransaction")
        @NotBlank(message = "encodedTransaction must not be blank")
        String encodedTransaction) {
}
