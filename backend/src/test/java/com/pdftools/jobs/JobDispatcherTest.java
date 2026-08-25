package com.pdftools.jobs;

import com.pdftools.jobs.persistence.JobRepository;
import com.pdftools.operations.OperationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobDispatcherTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobExecutionService executionService;

    @Mock
    private TaskExecutor taskExecutor;

    @Mock
    private OperationRegistry operationRegistry;

    @Test
    void queriesOnlyOperationsSupportedByTheLocalWorker() {
        Set<String> supported = Set.of("protect", "unlock");
        when(operationRegistry.keys()).thenReturn(supported);
        when(
            jobRepository
                .findTop20ByStatusAndOperationInOrderByCreatedAtAsc(
                    JobStatus.PENDING,
                    supported
                )
        ).thenReturn(List.of());
        JobDispatcher dispatcher = dispatcher();

        dispatcher.dispatchPending();

        verify(jobRepository)
            .findTop20ByStatusAndOperationInOrderByCreatedAtAsc(
                JobStatus.PENDING,
                supported
            );
    }

    @Test
    void doesNotQueryWhenTheWorkerHasNoRegisteredOperations() {
        when(operationRegistry.keys()).thenReturn(Set.of());
        JobDispatcher dispatcher = dispatcher();

        dispatcher.dispatchPending();

        verifyNoInteractions(jobRepository);
    }

    private JobDispatcher dispatcher() {
        return new JobDispatcher(
            jobRepository,
            executionService,
            taskExecutor,
            operationRegistry
        );
    }
}
