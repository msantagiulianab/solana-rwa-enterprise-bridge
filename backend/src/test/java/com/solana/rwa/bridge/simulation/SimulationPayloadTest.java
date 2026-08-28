package com.solana.rwa.bridge.simulation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solana.rwa.bridge.rpc.dto.RpcEnvelope;
import com.solana.rwa.bridge.simulation.dto.RpcSimulationResponseDto;
import com.solana.rwa.bridge.simulation.dto.SimulationRequestDto;
import com.solana.rwa.bridge.simulation.dto.SimulationResultDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline TDD harness for the pre-flight {@code simulateTransaction} JSON-RPC
 * wire models.
 *
 * <p>Verifies that the request DTO serializes a fully formed JSON-RPC 2.0
 * envelope with the fail-safe simulation config, and that the raw RPC response
 * deserializes into strongly typed domain models — distinguishing a successful
 * simulation ({@code err == null}) from a structured instruction error.
 */
class SimulationPayloadTest {

    private static final String BASE64_WIRE_TRANSACTION =
            "dGVzdC10cmFuc2FjdGlvbi13aXJlLWZvcm1hdA==";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializeRequest_emitsSimulateTransactionJsonRpcEnvelopeWithConfig() throws Exception {
        SimulationRequestDto request = SimulationRequestDto.of(BASE64_WIRE_TRANSACTION, 42L);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(json.get("id").asLong()).isEqualTo(42L);
        assertThat(json.get("method").asText()).isEqualTo("simulateTransaction");

        JsonNode params = json.get("params");
        assertThat(params.size()).isEqualTo(2);
        assertThat(params.get(0).asText()).isEqualTo(BASE64_WIRE_TRANSACTION);

        JsonNode config = params.get(1);
        assertThat(config.get("sigVerify").asBoolean()).isFalse();
        assertThat(config.get("encoding").asText()).isEqualTo("base64");
        assertThat(config.get("replaceRecentBlockhash").asBoolean()).isTrue();
    }

    @Test
    void deserializeSuccessfulResponse_parsesNullErrAndMapsToSuccessfulResult() throws Exception {
        String json = """
                {
                  "jsonrpc": "2.0",
                  "result": {
                    "context": { "slot": 348125 },
                    "value": {
                      "err": null,
                      "logs": [
                        "Program 11111111111111111111111111111111 invoke [1]",
                        "Program 11111111111111111111111111111111 success"
                      ],
                      "accounts": null,
                      "unitsConsumed": 3486,
                      "returnData": null
                    }
                  },
                  "id": 1
                }
                """;

        RpcEnvelope<RpcSimulationResponseDto> envelope = objectMapper.readValue(json,
                new TypeReference<RpcEnvelope<RpcSimulationResponseDto>>() {
                });

        assertThat(envelope.result()).isNotNull();
        RpcSimulationResponseDto.SimulationValue value = envelope.result().value();
        assertThat(value).isNotNull();
        assertThat(value.err() == null || value.err().isNull()).isTrue();
        assertThat(value.unitsConsumed()).isEqualTo(3486L);
        assertThat(value.logs()).containsExactly(
                "Program 11111111111111111111111111111111 invoke [1]",
                "Program 11111111111111111111111111111111 success");

        SimulationResultDto result = envelope.result().toSimulationResult();

        assertThat(result.success()).isTrue();
        assertThat(result.unitsConsumed()).isEqualTo(3486L);
        assertThat(result.logs()).hasSize(2);
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void deserializeFailedResponse_mapsStructuredErrToFailureResult() throws Exception {
        String json = """
                {
                  "jsonrpc": "2.0",
                  "result": {
                    "context": { "slot": 348126 },
                    "value": {
                      "err": { "InstructionError": [0, { "Custom": 1 }] },
                      "logs": [
                        "Program ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL invoke [1]",
                        "Program log: Transfer: insufficient funds"
                      ],
                      "accounts": null,
                      "unitsConsumed": 1024,
                      "returnData": null
                    }
                  },
                  "id": 2
                }
                """;

        RpcEnvelope<RpcSimulationResponseDto> envelope = objectMapper.readValue(json,
                new TypeReference<RpcEnvelope<RpcSimulationResponseDto>>() {
                });

        RpcSimulationResponseDto.SimulationValue value = envelope.result().value();
        assertThat(value.err().isObject()).isTrue();
        assertThat(value.err().path("InstructionError").isArray()).isTrue();
        assertThat(value.unitsConsumed()).isEqualTo(1024L);

        SimulationResultDto result = envelope.result().toSimulationResult();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage())
                .contains("InstructionError")
                .contains("Custom");
        assertThat(result.logs()).hasSize(2);
        assertThat(result.unitsConsumed()).isEqualTo(1024L);
    }


    @Test
    void deserializeResponseWithoutErrField_mapsToSuccessfulResult() throws Exception {
        String json = """
                {
                  "jsonrpc": "2.0",
                  "result": {
                    "context": { "slot": 348127 },
                    "value": {
                      "logs": ["Program log: dry run ok"],
                      "accounts": null,
                      "unitsConsumed": 200,
                      "returnData": null
                    }
                  },
                  "id": 3
                }
                """;

        RpcEnvelope<RpcSimulationResponseDto> envelope = objectMapper.readValue(json,
                new TypeReference<RpcEnvelope<RpcSimulationResponseDto>>() {
                });

        assertThat(envelope.result().isSuccessful()).isTrue();

        SimulationResultDto result = envelope.result().toSimulationResult();

        assertThat(result.success()).isTrue();
        assertThat(result.unitsConsumed()).isEqualTo(200L);
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void deserializeResponse_parsesReturnDataAndAccounts() throws Exception {
        String json = """
                {
                  "jsonrpc": "2.0",
                  "result": {
                    "context": { "slot": 348128 },
                    "value": {
                      "err": null,
                      "logs": ["Program log: returning data"],
                      "accounts": [
                        {
                          "lamports": 1461600,
                          "data": ["base64data", "base64"],
                          "owner": "11111111111111111111111111111111",
                          "executable": false,
                          "rentEpoch": 0
                        }
                      ],
                      "unitsConsumed": 5500,
                      "returnData": {
                        "programId": "ComputeBudget111111111111111111111111111111",
                        "data": ["AQAAAA==", "base64"]
                      }
                    }
                  },
                  "id": 4
                }
                """;

        RpcEnvelope<RpcSimulationResponseDto> envelope = objectMapper.readValue(json,
                new TypeReference<RpcEnvelope<RpcSimulationResponseDto>>() {
                });

        RpcSimulationResponseDto.SimulationValue value = envelope.result().value();
        assertThat(value.returnData()).isNotNull();
        assertThat(value.returnData().get("programId").asText())
                .isEqualTo("ComputeBudget111111111111111111111111111111");
        assertThat(value.returnData().get("data").size()).isEqualTo(2);
        assertThat(value.accounts()).isNotNull();
        assertThat(value.accounts().size()).isEqualTo(1);
    }
}

