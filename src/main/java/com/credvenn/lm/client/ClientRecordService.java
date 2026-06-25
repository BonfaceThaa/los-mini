package com.credvenn.lm.client;

import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.fineract.FineractGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientRecordService {

    private final ClientRecordRepository clientRecordRepository;

    public ClientRecordService(ClientRecordRepository clientRecordRepository) {
        this.clientRecordRepository = clientRecordRepository;
    }

    @Transactional
    public void upsertFromFineract(String tenantId, String applicationId, String nationalId, FineractGateway.FineractClient client) {
        ClientRecord record = clientRecordRepository.findByTenantIdAndFineractClientId(tenantId, client.id())
                .orElseGet(ClientRecord::new);
        record.setTenantId(tenantId);
        record.setApplicationId(applicationId);
        record.setFineractClientId(client.id());
        record.setNationalId(normalizeNationalId(nationalId));
        record.setAccountNo(client.accountNo());
        record.setExternalId(client.externalId());
        record.setStatus(client.status());
        record.setActive(client.active());
        record.setFirstname(client.firstname());
        record.setMiddlename(client.middlename());
        record.setLastname(client.lastname());
        record.setDisplayName(client.displayName());
        record.setMobileNo(client.mobileNo());
        record.setOfficeName(client.officeName());
        clientRecordRepository.save(record);
    }

    @Transactional(readOnly = true)
    public ClientRecord getRequiredByFineractClientId(String tenantId, String fineractClientId) {
        return clientRecordRepository.findByTenantIdAndFineractClientId(tenantId, fineractClientId)
                .orElseThrow(() -> new NotFoundException("Client not found"));
    }

    @Transactional(readOnly = true)
    public ClientRecord findByTenantIdAndNationalId(String tenantId, String nationalId) {
        return clientRecordRepository.findByTenantIdAndNationalId(tenantId, normalizeNationalId(nationalId))
                .orElse(null);
    }

    private String normalizeNationalId(String nationalId) {
        if (nationalId == null) {
            return null;
        }
        String trimmed = nationalId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
