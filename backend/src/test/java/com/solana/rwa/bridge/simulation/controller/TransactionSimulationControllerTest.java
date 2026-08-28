package com.solana.rwa.bridge.simulation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solana.rwa.bridge.config.ApiKeyAuthInterceptor;
import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.simulation.dto.RpcSimulationResponseDto;
import com.solana.rwa.bridge.simulation.dto.SimulationResultDto;
import com.solana.rwa.bridge.simulation.exception.SimulationExecutionException;
import com.solana.rwa.bridge.simulation.service.TransactionSimulationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc web-layer tests for {@link TransactionSimulationController}.
 *
 * <p>The simulation service is mocked (never a live Devnet rehearsal). Verifies
 * request binding and the {@code X-API-Key} mutating-route gate, the structured
 * success payload, blank/malformed-body 400s, reverted-dry-run 422 diagnostics,
 * and the fail-closed 502 upstream RPC outage contract.
 */
@WebMvcTest(TransactionSimulationController.class)
@Import(ApiKeyAuthInterceptor.class)
@ActiveProfiles("test")
class TransactionSimulationControllerTest {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY = "test-api-key";
    private static final String BASE64_TX = "dGVzdC10cmFuc2FjdGlvbi13aXJlLWZvcm1hdA==";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionSimulationService simulationService;

    @Test
    void simulate_validBase64Transaction_returns200WithStructuredResult() throws Exception {
        SimulationResultDto result = new SimulationResultDto(
                true,
                200_000L,
                List.of("Program log: dry run ok", "Program log: success"),
                null,
                230_000L);
        when(simulationService.simulate(BASE64_TX)).thenReturn(result);

        mockMvc.perform(post("/api/v1/settlement/simulate")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(BASE64_TX)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.unitsConsumed").value(200_000))
                .andExpect(jsonPath("$.recommendedComputeUnitLimit").value(230_000))
                .andExpect(jsonPath("$.logs").isArray())
                .andExpect(jsonPath("$.logs[0]").value("Program log: dry run ok"))
                .andExpect(jsonPath("$.logs[1]").value("Program log: success"))
                .andExpect(jsonPath("$.errorMessage").doesNotExist());

        verify(simulationService).simulate(BASE64_TX);
    }

    @Test
    void simulate_blankEncodedTransaction_returns400WithValidationDetails() throws Exception {
        mockMvc.perform(post("/api/v1/settlement/simulate")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"encodedTransaction\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("encodedTransaction: encodedTransaction must not be blank"));
    }

    @Test
    void simulate_malformedBody_returns400WithStructuredError() throws Exception {
        mockMvc.perform(post("/api/v1/settlement/simulate")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void simulate_revertedDryRun_returns422WithErrorDiagnostics() throws Exception {
        SimulationExecutionException ex = SimulationExecutionException.from(
                new RpcSimulationResponseDto.SimulationValue(
                        objectMapper.readTree("{\"InstructionError\":[0,{\"Custom\":1}]}"),
                        List.of("Program log: Transfer: insufficient funds"),
                        1_024L,
                        null,
                        null));
        when(simulationService.simulate(BASE64_TX)).thenThrow(ex);

        mockMvc.perform(post("/api/v1/settlement/simulate")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(BASE64_TX)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.message")
                        .value("Transaction simulation reverted: InstructionError at instruction 0 (Custom 1)"))
                .andExpect(jsonPath("$.errorType").value("InstructionError"))
                .andExpect(jsonPath("$.instructionIndex").value(0))
                .andExpect(jsonPath("$.programError").value("Custom"))
                .andExpect(jsonPath("$.programErrorCode").value(1))
                .andExpect(jsonPath("$.unitsConsumed").value(1_024))
                .andExpect(jsonPath("$.logs[0]").value("Program log: Transfer: insufficient funds"));
    }

    @Test
    void simulate_upstreamRpcFailure_returns502FailClosed() throws Exception {
        SimulationExecutionException ex = new SimulationExecutionException(
                "Transaction simulation failed: Solana RPC call 'simulateTransaction' failed: Read timed out",
                new SolanaRpcException("simulateTransaction", new RuntimeException("Read timed out")));
        when(simulationService.simulate(BASE64_TX)).thenThrow(ex);

        mockMvc.perform(post("/api/v1/settlement/simulate")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(BASE64_TX)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("Bad Gateway"))
                .andExpect(jsonPath("$.message")
                        .value("Transaction simulation could not be completed: upstream RPC node is unavailable (fail-closed)"));
    }

    @Test
    void simulate_missingApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/settlement/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(BASE64_TX)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Missing or invalid API key"));
    }

    private String payload(String encodedTransaction) {
        return "{\"encodedTransaction\":\"" + encodedTransaction + "\"}";
    }
}
