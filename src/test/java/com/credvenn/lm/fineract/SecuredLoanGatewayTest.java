package com.credvenn.lm.fineract;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.tenant.Tenant;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SecuredLoanGatewayTest {
    private MockRestServiceServer server;
    private HttpFineractGateway gateway;
    private LoanRequestApplication app;
    private Tenant tenant;
    private static final String LOOKUP="http://lms.test/loans?externalId=los-app&limit=2";
    @BeforeEach void setup(){
        var builder=RestClient.builder().baseUrl("http://lms.test");server=MockRestServiceServer.bindTo(builder).build();
        gateway=new HttpFineractGateway(builder.build(),new FineractProperties("http://lms.test","test","test",1,1,"en","dd MMMM yyyy","mifos-standard-strategy"));
        tenant=new Tenant();tenant.setFineractTenantId("lender");
        app=new LoanRequestApplication();ReflectionTestUtils.setField(app,"id","app");app.setFineractClientId("3");app.setApprovedFineractProductId("7");app.setApprovedAmount(new BigDecimal("480000"));
    }
    private FineractLoanProduct product(){return new FineractLoanProduct("7","Logbook","LB12",new BigDecimal("50000"),new BigDecimal("2000000"),12,12,null,0,1,new BigDecimal("2"),1,2,1,2,12,"KES",true);}
    private String existing(){return """
        {"pageItems":[{"id":100,"externalId":"los-app","clientId":3,"loanProductId":7,"principal":480000,
          "status":{"code":"loanStatusType.submitted.and.pending.approval","active":false}}]}
        """;}
    @Test void creationUsesExternalReferenceAndSelectedStrategy(){
        server.expect(requestTo(LOOKUP)).andExpect(method(HttpMethod.GET)).andExpect(header("Fineract-Platform-TenantId","lender"))
            .andRespond(withSuccess("{\"pageItems\":[]}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://lms.test/loans")).andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""
                {"externalId":"los-app","clientId":3,"productId":7,"principal":480000,"loanType":"individual",
                 "loanTermFrequency":12,"loanTermFrequencyType":2,"numberOfRepayments":12,"repaymentEvery":1,
                 "interestType":0,"transactionProcessingStrategyCode":"mifos-standard-strategy"}
                """))
            .andRespond(withSuccess("{\"resourceId\":100}",MediaType.APPLICATION_JSON));
        assertEquals("100",gateway.createSecuredPendingLoan(tenant,app,product(),"mifos-standard-strategy"));server.verify();
    }
    @Test void retryFindsExistingLoanWithoutPostingAgain(){
        server.expect(requestTo(LOOKUP)).andRespond(withSuccess(existing(),MediaType.APPLICATION_JSON));
        assertEquals("100",gateway.createSecuredPendingLoan(tenant,app,product(),"mifos-standard-strategy"));server.verify();
    }
    @Test void mismatchedRemotePrincipalFailsClosed(){
        server.expect(requestTo(LOOKUP)).andRespond(withSuccess(existing().replace("480000","500000"),MediaType.APPLICATION_JSON));
        assertThrows(BadRequestException.class,()->gateway.createSecuredPendingLoan(tenant,app,product(),"mifos-standard-strategy"));server.verify();
    }
    @Test void incompleteLookupNeverTriggersCreate(){
        server.expect(requestTo(LOOKUP)).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        assertThrows(BadRequestException.class,()->gateway.createSecuredPendingLoan(tenant,app,product(),"mifos-standard-strategy"));server.verify();
    }
    @Test void foreignExternalReferenceFailsClosed(){
        server.expect(requestTo(LOOKUP)).andRespond(withSuccess(existing().replace("los-app","los-other"),MediaType.APPLICATION_JSON));
        assertThrows(BadRequestException.class,()->gateway.findSecuredLoan(tenant,app));server.verify();
    }
}
