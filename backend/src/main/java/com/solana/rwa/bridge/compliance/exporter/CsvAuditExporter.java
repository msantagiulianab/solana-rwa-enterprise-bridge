package com.solana.rwa.bridge.compliance.exporter;

import com.solana.rwa.bridge.compliance.dto.AuditExportRecordDto;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Deterministic RFC-4180 CSV exporter for the settlement-proof audit ledger.
 *
 * <p>Relies exclusively on standard Java string primitives (zero third-party
 * CSV dependencies). Fields containing a comma, double quote, or line break
 * are wrapped in double quotes with embedded quotes doubled, and null optional
 * fields are emitted as empty (unquoted) columns. Rows are terminated with
 * CRLF per RFC-4180.
 */
@Component
public class CsvAuditExporter implements AuditExporter {

    private static final String CANONICAL_HEADER =
            "EventId,Timestamp,AssetId,InvestorWallet,KYC_Verified,OFAC_Passed,Status,CU_Price_MicroLamports,CU_Limit,Signature,Slot,Blockhash";
    private static final String CRLF = "\r\n";

    @Override
    public byte[] export(List<AuditExportRecordDto> records) {
        StringBuilder csv = new StringBuilder();
        csv.append(CANONICAL_HEADER).append(CRLF);

        if (records != null) {
            for (AuditExportRecordDto record : records) {
                appendField(csv, record.getEventId() == null ? null : record.getEventId().toString());
                csv.append(',');
                appendField(csv, record.getTimestamp() == null ? null : record.getTimestamp().toString());
                csv.append(',');
                appendField(csv, record.getAssetId());
                csv.append(',');
                appendField(csv, record.getInvestorWallet());
                csv.append(',');
                csv.append(record.isKycVerified());
                csv.append(',');
                csv.append(record.isOfacPassed());
                csv.append(',');
                appendField(csv, record.getStatus());
                csv.append(',');
                csv.append(record.getComputeUnitPriceMicroLamports());
                csv.append(',');
                csv.append(record.getComputeUnitLimit());
                csv.append(',');
                appendField(csv, record.getSolanaTransactionSignature());
                csv.append(',');
                appendField(csv, record.getSlot() == null ? null : String.valueOf(record.getSlot()));
                csv.append(',');
                appendField(csv, record.getBlockhash());
                csv.append(CRLF);
            }
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendField(StringBuilder csv, String value) {
        if (value == null) {
            return;
        }
        if (requiresQuoting(value)) {
            csv.append('"')
               .append(value.replace("\"", "\"\""))
               .append('"');
        } else {
            csv.append(value);
        }
    }

    private boolean requiresQuoting(String value) {
        return value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
    }
}
