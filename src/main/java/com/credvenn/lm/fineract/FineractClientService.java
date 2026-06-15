package com.credvenn.lm.fineract;

import com.credvenn.lm.client.ClientRecord;
import com.credvenn.lm.client.ClientRecordRepository;
import com.credvenn.lm.client.ClientRecordService;
import com.credvenn.lm.common.api.PagedResponse;
import com.credvenn.lm.common.api.PaginationSupport;
import com.credvenn.lm.security.CurrentActorService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FineractClientService {

    private static final Map<String, String> CLIENT_SORT_COLUMNS = new LinkedHashMap<>();

    static {
        CLIENT_SORT_COLUMNS.put("displayName", "displayName");
        CLIENT_SORT_COLUMNS.put("firstname", "firstname");
        CLIENT_SORT_COLUMNS.put("lastname", "lastname");
        CLIENT_SORT_COLUMNS.put("accountNo", "accountNo");
        CLIENT_SORT_COLUMNS.put("externalId", "externalId");
        CLIENT_SORT_COLUMNS.put("status", "status");
        CLIENT_SORT_COLUMNS.put("officeName", "officeName");
    }

    private final CurrentActorService currentActorService;
    private final ClientRecordRepository clientRecordRepository;
    private final ClientRecordService clientRecordService;

    public FineractClientService(
            CurrentActorService currentActorService,
            ClientRecordRepository clientRecordRepository,
            ClientRecordService clientRecordService) {
        this.currentActorService = currentActorService;
        this.clientRecordRepository = clientRecordRepository;
        this.clientRecordService = clientRecordService;
    }

    @Transactional(readOnly = true)
    public List<FineractDtos.FineractClientResponse> listCurrentTenantClients() {
        String tenantId = currentTenantId();
        return clientRecordRepository.findAllByTenantId(tenantId, Pageable.unpaged()).stream()
                .map(FineractClientService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<FineractDtos.FineractClientResponse> listCurrentTenantClients(
            Integer page,
            Integer size,
            String sortBy,
            String sortDir) {
        String tenantId = currentTenantId();
        Pageable pageable = PaginationSupport.pageable(page, size, sortBy, sortDir, CLIENT_SORT_COLUMNS, "displayName");
        String normalizedSortBy = PaginationSupport.normalizeSortBy(sortBy, CLIENT_SORT_COLUMNS, "displayName");
        String normalizedSortDir = PaginationSupport.normalizeDirectionValue(sortDir);
        var resultPage = clientRecordRepository.findAllByTenantId(tenantId, pageable).map(FineractClientService::toResponse);
        return PagedResponse.fromPage(resultPage, normalizedSortBy, normalizedSortDir);
    }

    @Transactional(readOnly = true)
    public FineractDtos.FineractClientResponse getCurrentTenantClient(String fineractClientId) {
        String tenantId = currentTenantId();
        return toResponse(clientRecordService.getRequiredByFineractClientId(tenantId, fineractClientId));
    }

    private String currentTenantId() {
        return currentActorService.requireCurrentUser().tenantId();
    }

    private static FineractDtos.FineractClientResponse toResponse(ClientRecord record) {
        return new FineractDtos.FineractClientResponse(
                record.getFineractClientId(),
                record.getAccountNo(),
                record.getExternalId(),
                record.getStatus(),
                record.isActive(),
                record.getFirstname(),
                record.getMiddlename(),
                record.getLastname(),
                record.getDisplayName(),
                record.getMobileNo(),
                record.getOfficeName());
    }
}
