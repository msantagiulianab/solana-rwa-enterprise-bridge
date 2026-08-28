package com.solana.rwa.bridge.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solana.rwa.bridge.exception.SolanaRpcException;
import com.solana.rwa.bridge.rpc.SolanaRpcAdapter;
import com.solana.rwa.bridge.simulation.dto.RpcSimulationResponseDto;
import com.solana.rwa.bridge.simulation.dto.SimulationResultDto;
import com.solana.rwa.bridge.simulation.exception.SimulationExecutionException;
import com.solana.rwa.bridge.simulation.service.TransactionSimulationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link TransactionSimulationService}.
 *
 * <p>The Solana RPC transport is mocked (never a live Devnet call). Covers
 * successful CU/log extraction with the +15% safety margin, fail-closed
 * structured program-error parsing, and fail-closed handling of transport
 * failures and null responses.
 */
@ExtendWith(MockitoExtension.class)
class TransactionSimulationServiceTest {

    private static final String BASE64_TX =
            "dGVzdC10cmFuc2FjdGlvbi13aXJlLWZvcm1hdA==";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SolanaRpcAdapter solanaRpcAdapter;

    @InjectMocks
    private TransactionSimulationService simulationService;

    private RpcSimulationResponseDto.SimulationValue value(JsonNode err, List<String> logs,
                                                           Long unitsConsumed) {
        return new RpcSimulationResponseDto.SimulationValue(err, logs, unitsConsumed, null, null);
    }

    @Test
    void simulate_returnsUnitsConsumedLogsAndRecommendedComputeUnitLimit() {
        List<String> logs = List.of(
                "Program 11111111111111111111111111111111 invoke [1]",
                "Program 11111111111111111111111111111111 success");
        when(solanaRpcAdapter.simulateTransaction(BASE64_TX))
                .thenReturn(value(null, logs, 200_000L));

        SimulationResultDto result = simulationService.simulate(BASE64_TX);

        assertThat(result.success()).isTrue();
        assertThat(result.unitsConsumed()).isEqualTo(200_000L);
        assertThat(result.logs()).containsExactlyElementsOf(logs);
        assertThat(result.recommendedComputeUnitLimit()).isEqualTo(230_000L);
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void simulate_throwsStructuredExceptionForCustomInstructionError() throws Exception {
        JsonNode err = objectMapper.readTree("{\"InstructionError\":[0,{\"Custom\":1}]}");
        when(solanaRpcAdapter.simulateTransaction(BASE64_TX))
                .thenReturn(value(err, List.of("Program log: Transfer: insufficient funds"), 1_024L));

        assertThatThrownBy(() -> simulationService.simulate(BASE64_TX))
                .isInstanceOfSatisfying(SimulationExecutionException.class, ex -> {
                    assertThat(ex.getErrorType()).isEqualTo("InstructionError");
                    assertThat(ex.getInstructionIndex()).isZero();
                    assertThat(ex.getProgramError()).isEqualTo("Custom");
                    assertThat(ex.getProgramErrorCode()).isEqualTo(1);
                    assertThat(ex.getLogs()).hasSize(1);
                    assertThat(ex.getUnitsConsumed()).isEqualTo(1_024L);
                    assertThat(ex.getMessage())
                            .contains("InstructionError")
                            .contains("Custom");
                });
    }

    @Test
    void recommendedComputeUnitLimit_appliesFifteenPercentSafetyMargin() {
        assertThat(TransactionSimulationService.recommendedComputeUnitLimit(200_000L))
                .isEqualTo(230_000L);
        assertThat(TransactionSimulationService.recommendedComputeUnitLimit(1_000_000L))
                .isEqualTo(1_150_000L);
        assertThat(TransactionSimulationService.recommendedComputeUnitLimit(1L))
                .isEqualTo(2L);
    }

    @Test
    void simulate_failsClosedOnRpcTransportFailure() {
        when(solanaRpcAdapter.simulateTransaction(BASE64_TX))
                .thenThrow(new SolanaRpcException("simulateTransaction",
                        new RuntimeException("Read timed out")));

        assertThatThrownBy(() -> simulationService.simulate(BASE64_TX))
                .isInstanceOf(SimulationExecutionException.class)
                .hasMessageContaining("Read timed out")
                .hasCauseInstanceOf(SolanaRpcException.class);
    }

    @Test
    void simulate_failsClosedOnNullResponse() {
        when(solanaRpcAdapter.simulateTransaction(BASE64_TX)).thenReturn(null);

        assertThatThrownBy(() -> simulationService.simulate(BASE64_TX))
                .isInstanceOf(SimulationExecutionException.class)
                .hasMessageContaining("no result");
    }

    @Test
    void simulate_rejectsBlankEncodedTransactionWithoutRpcCall() {
        assertThatThrownBy(() -> simulationService.simulate("   "))
                .isInstanceOf(SimulationExecutionException.class)
                .hasMessageContaining("blank");

        verifyNoInteractions(solanaRpcAdapter);
    }
}


