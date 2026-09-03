package com.solana.rwa.bridge.maritime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solana.rwa.bridge.entity.FinalityOutboxEntry;
import com.solana.rwa.bridge.entity.SettlementStatus;
import com.solana.rwa.bridge.maritime.adapter.out.simulation.SimulatedMaritimeClearanceAdapter;
import com.solana.rwa.bridge.maritime.repository.BillOfLadingRepository;
import com.solana.rwa.bridge.maritime.repository.CanalTransitSettlementRepository;
import com.solana.rwa.bridge.maritime.repository.ContainerConsignmentRepository;
import com.solana.rwa.bridge.repository.FinalityOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration simulation of the maritime Delivery-vs-Payment (DvP)
 * settlement pipeline.
 *
 * <p>Boots the full Spring context (H2 in PostgreSQL mode, Flyway V1–V4, MockMvc)
 * and drives the deterministic {@link SimulatedMaritimeClearanceAdapter} through
 * the authenticated REST API: register → evaluate → execute. The happy path must
 * enqueue exactly one {@code CONFIRMED} finality outbox row; the fail-closed
 * sanctions path must produce zero outbox rows and no settlement mutation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MaritimeSettlementE2EIT {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY = "test-api-key";
    private static final String BASE_PATH = "/api/v1/maritime";
    private static final String WALLET = "7XeXLabcDEFghijkmnpqrstuvwxyz23456789";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CanalTransitSettlementRepository canalTransitSettlementRepository;

    @Autowired
    private FinalityOutboxRepository finalityOutboxRepository;

    @Autowired
    private BillOfLadingRepository billOfLadingRepository;

    @Autowired
    private ContainerConsignmentRepository containerConsignmentRepository;

    @BeforeEach
    void resetDatabase() {
        // H2 is JVM-scoped (DB_CLOSE_DELAY=-1), so rows persist across test methods
        // within the shared Spring context. Delete children before parents to honor FKs.
        canalTransitSettlementRepository.deleteAll();
        finalityOutboxRepository.deleteAll();
        containerConsignmentRepository.deleteAll();
        billOfLadingRepository.deleteAll();
    }

    @Test
    void testHappyPath_clearedTransit_executesSettlementAndEnqueuesOutbox() throws Exception {
        UUID settlementId = registerBillOfLading("BL-E2E-HAPPY-0001", "IMO1234567");

        mockMvc.perform(post(BASE_PATH + "/settlements/{id}/evaluate", settlementId)
                        .header(API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementId").value(settlementId.toString()))
                .andExpect(jsonPath("$.status").value("CLEARED"));

        mockMvc.perform(post(BASE_PATH + "/settlements/{id}/execute", settlementId)
                        .header(API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk());

        List<FinalityOutboxEntry> outbox = finalityOutboxRepository.findAll();
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
    }

    @Test
    void testFailClosedPath_sanctionedTransit_blocksExecution() throws Exception {
        UUID settlementId = registerBillOfLading("BL-E2E-SANCTION-0001",
                SimulatedMaritimeClearanceAdapter.SANCTIONED_VESSEL_IMO);

        mockMvc.perform(post(BASE_PATH + "/settlements/{id}/evaluate", settlementId)
                        .header(API_KEY_HEADER, API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementId").value(settlementId.toString()))
                .andExpect(jsonPath("$.status").value("SANCTIONED"));

        mockMvc.perform(post(BASE_PATH + "/settlements/{id}/execute", settlementId)
                        .header(API_KEY_HEADER, API_KEY))
                .andExpect(status().isUnprocessableEntity());

        assertThat(finalityOutboxRepository.findAll()).isEmpty();
    }

    private UUID registerBillOfLading(String blNumber, String vesselImo) throws Exception {
        String response = mockMvc.perform(post(BASE_PATH + "/bills-of-lading")
                        .header(API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(blNumber, vesselImo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.settlementId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return UUID.fromString(json.get("settlementId").asText());
    }

    private String registerPayload(String blNumber, String vesselImo) throws Exception {
        Map<String, Object> consignment = new LinkedHashMap<>();
        consignment.put("containerNumber", "CONT-001");
        consignment.put("declaredValueUsd", new BigDecimal("12500.50"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("blNumber", blNumber);
        body.put("vesselImo", vesselImo);
        body.put("carrierId", "MSC");
        body.put("originPort", "PACTB");
        body.put("destinationPort", "USNYC");
        body.put("consigneeWallet", WALLET);
        body.put("consignments", List.of(consignment));

        return objectMapper.writeValueAsString(body);
    }
}
