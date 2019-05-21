package uk.gov.ons.ctp.integration.contactcentresvc.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.ons.ctp.common.FixtureHelper;
import uk.gov.ons.ctp.common.time.DateTimeUtil;
import uk.gov.ons.ctp.integration.contactcentresvc.client.caseservice.CaseServiceClientServiceImpl;
import uk.gov.ons.ctp.integration.contactcentresvc.client.caseservice.model.CaseContainerDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.CaseDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.CaseEventDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.CaseRequestDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.service.CaseService;

/**
 * This class tests the CaseServiceImpl layer. It mocks out the layer below
 * (CaseServiceClientServiceImpl), which would deal with actually sending a HTTP request to the case
 * service.
 */
public class CaseServiceImplTest {

  @Mock CaseServiceClientServiceImpl CaseServiceClientService = new CaseServiceClientServiceImpl();

  @InjectMocks CaseService caseService = new CaseServiceImpl();

  private UUID uuid = UUID.fromString("b7565b5e-1396-4965-91a2-918c0d3642ed");

  @Before
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testGetCaseByCaseId_withCaseDetails() throws Exception {
    doTestGetCaseByCaseId(true);
  }

  @Test
  public void testGetCaseByCaseId_withNoCaseDetails() throws Exception {
    doTestGetCaseByCaseId(false);
  }

  @Test
  public void testGetCaseByCaseId_nonHouseholdCase() throws Exception {
    // Build results to be returned from search
    CaseContainerDTO caseFromCaseService =
        FixtureHelper.loadClassFixtures(CaseContainerDTO[].class).get(0);
    caseFromCaseService.setCaseType("X"); // Not household case
    Mockito.when(CaseServiceClientService.getCaseById(any(), any()))
        .thenReturn(caseFromCaseService);

    // Run the request
    try {
      caseService.getCaseById(uuid, new CaseRequestDTO(true));
      fail();
    } catch (ResponseStatusException e) {
      assertEquals("Case is a non-household case", e.getReason());
      assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }
  }

  @Test
  public void testGetCaseByCaseRef_withCaseDetails() throws Exception {
    doTestGetCaseByCaseRef(true);
  }

  @Test
  public void testGetCaseByCaseRef_withNoCaseDetails() throws Exception {
    doTestGetCaseByCaseRef(false);
  }

  @Test
  public void testGetCaseByCaseRef_nonHouseholdCase() throws Exception {
    long testCaseRef = 88234544;

    // Build results to be returned from search
    CaseContainerDTO caseFromCaseService =
        FixtureHelper.loadClassFixtures(CaseContainerDTO[].class).get(0);
    caseFromCaseService.setCaseType("X"); // Not household case
    Mockito.when(CaseServiceClientService.getCaseByCaseRef(eq(testCaseRef), any()))
        .thenReturn(caseFromCaseService);

    // Run the request
    try {
      caseService.getCaseByCaseReference(testCaseRef, new CaseRequestDTO(true));
      fail();
    } catch (ResponseStatusException e) {
      assertEquals("Case is a non-household case", e.getReason());
      assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }
  }

  private void doTestGetCaseByCaseId(boolean caseEvents) throws Exception {
    // Build results to be returned from search
    CaseContainerDTO caseFromCaseService =
        FixtureHelper.loadClassFixtures(CaseContainerDTO[].class).get(0);
    Mockito.when(CaseServiceClientService.getCaseById(eq(uuid), any()))
        .thenReturn(caseFromCaseService);

    // Run the request
    CaseRequestDTO requestParams = new CaseRequestDTO(caseEvents);
    CaseDTO results = caseService.getCaseById(uuid, requestParams);

    verifyCase(results, caseEvents);
  }

  private void doTestGetCaseByCaseRef(boolean caseEvents) throws Exception {
    long testCaseRef = 88234544;

    // Build results to be returned from search
    CaseContainerDTO caseFromCaseService =
        FixtureHelper.loadClassFixtures(CaseContainerDTO[].class).get(0);
    Mockito.when(CaseServiceClientService.getCaseByCaseRef(any(), any()))
        .thenReturn(caseFromCaseService);

    // Run the request
    CaseRequestDTO requestParams = new CaseRequestDTO(caseEvents);
    CaseDTO results = caseService.getCaseByCaseReference(testCaseRef, requestParams);

    verifyCase(results, caseEvents);
  }

  private void verifyCase(CaseDTO results, boolean caseEventsExpected) throws ParseException {
    assertEquals(uuid, results.getId());
    assertEquals("1000000000000001", results.getCaseRef());
    assertEquals("H", results.getCaseType());
    assertEquals(asMillis("2019-05-14T16:11:41.343+01:00"), results.getCreatedDateTime().getTime());

    if (caseEventsExpected) {
      assertEquals(2, results.getCaseEvents().size());
      CaseEventDTO event = results.getCaseEvents().get(0);
      assertEquals("Initial creation of case", event.getDescription());
      assertEquals("CASE_CREATED", event.getCategory());
      assertEquals(asMillis("2019-05-14T16:11:41.343+01:00"), event.getCreatedDateTime().getTime());
      event = results.getCaseEvents().get(1);
      assertEquals("Create Household Visit", event.getDescription());
      assertEquals("ACTION_CREATED", event.getCategory());
      assertEquals(asMillis("2019-05-16T12:12:12.343Z"), event.getCreatedDateTime().getTime());
    } else {
      assertNull(results.getCaseEvents());
    }
  }

  private long asMillis(String datetime) throws ParseException {
    SimpleDateFormat dateParser = new SimpleDateFormat(DateTimeUtil.DATE_FORMAT_IN_JSON);

    return dateParser.parse(datetime).getTime();
  }
}