package com.credvenn.lm.logbook;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/applications/{applicationId}/logbook")
@Tag(name="Logbook Workflow") @SecurityRequirement(name="bearerAuth")
public class LogbookWorkflowController {
    private final LogbookWorkflowService workflow;
    @GetMapping("/workflow") @Operation(summary="Read stage requirements and background loan operations",description="Requires LOAN_VIEW. Checks current evidence, product and financing; operation errors are sanitized.")
    public LogbookWorkflowService.WorkflowResponse get(@PathVariable String applicationId){return workflow.get(applicationId);}
    @PostMapping("/financing") @Operation(summary="Assess logbook financing without a device or deposit",description="Requires LOAN_CREATE. Supply expectedApplicationVersion and amount. Initial support: KES products with monthly repayment cadence and total term at most 360 months. Fineract owns the repayment schedule.")
    public LogbookWorkflowService.FinancingResponse assess(@PathVariable String applicationId,@Valid @RequestBody LogbookWorkflowService.FinancingRequest request){return workflow.assess(applicationId,request);}
    @PostMapping("/operations/{operationId}/retry") @Operation(summary="Queue reconciliation and retry of a failed logbook operation",description="Requires CREDIT_MANUAL_APPROVE. A RUNNING operation may be recovered after 15 minutes. Remote state is checked before another write.")
    public LogbookLoanOperation retry(@PathVariable String applicationId,@PathVariable String operationId){return workflow.retry(applicationId,operationId);}
}
