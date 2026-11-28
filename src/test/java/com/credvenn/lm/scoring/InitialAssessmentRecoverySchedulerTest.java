package com.credvenn.lm.scoring;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.credvenn.lm.tenant.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class InitialAssessmentRecoverySchedulerTest {
    @Test void scansEachTenantAndContinuesAfterFailure() {
        var tenants = mock(TenantRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        var starts = mock(InitialAssessmentStartService.class);
        var a = new Tenant(); a.setId("tenant-a");
        var b = new Tenant(); b.setId("tenant-b");
        when(tenants.findAllByOrderByNameAsc()).thenReturn(List.of(a, b));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String[]>>any(), eq("tenant-a")))
                .thenReturn(java.util.Collections.singletonList(new String[]{"app-a", "analysis-a"}));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String[]>>any(), eq("tenant-b")))
                .thenReturn(java.util.Collections.singletonList(new String[]{"app-b", "analysis-b"}));
        when(starts.start("tenant-a", "app-a", "analysis-a")).thenThrow(new IllegalStateException("unavailable"));
        new InitialAssessmentRecoveryScheduler(tenants, jdbc, starts).recover();
        verify(starts).start("tenant-a", "app-a", "analysis-a");
        verify(starts).start("tenant-b", "app-b", "analysis-b");
    }
}
