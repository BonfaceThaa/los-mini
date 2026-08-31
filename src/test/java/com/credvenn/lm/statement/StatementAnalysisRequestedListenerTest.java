package com.credvenn.lm.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class StatementAnalysisRequestedListenerTest {

    @Test
    void listenerRunsAfterCommitWithFallbackWhenNoTransactionIsActive() throws NoSuchMethodException {
        Method method = StatementAnalysisRequestedListener.class.getMethod(
                "onStatementAnalysisRequested",
                StatementAnalysisRequestedEvent.class);
        TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);

        assertEquals(TransactionPhase.AFTER_COMMIT, listener.phase());
        assertTrue(listener.fallbackExecution());
    }

    @Test
    void listenerForwardsTheSavedAnalysisIdToProcessing() {
        StatementAnalysisProcessingService processingService = mock(StatementAnalysisProcessingService.class);
        StatementAnalysisRequestedListener listener = new StatementAnalysisRequestedListener(processingService);
        StatementAnalysisRequestedEvent event = new StatementAnalysisRequestedEvent(
                "tenant-1",
                "app-1",
                "document-1",
                "analysis-1",
                "officer",
                null);

        listener.onStatementAnalysisRequested(event);

        verify(processingService).process("tenant-1", "analysis-1", "officer", null);
    }
}