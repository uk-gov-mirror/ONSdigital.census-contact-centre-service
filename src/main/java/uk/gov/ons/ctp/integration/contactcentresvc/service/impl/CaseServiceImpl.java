package uk.gov.ons.ctp.integration.contactcentresvc.service.impl;

import static uk.gov.ons.ctp.integration.contactcentresvc.utility.Constants.UNKNOWN_UUID;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import ma.glasnost.orika.MapperFacade;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.common.error.CTPException.Fault;
import uk.gov.ons.ctp.common.event.EventPublisher;
import uk.gov.ons.ctp.common.event.EventPublisher.EventType;
import uk.gov.ons.ctp.common.event.EventPublisher.Source;
import uk.gov.ons.ctp.common.event.model.Address;
import uk.gov.ons.ctp.common.event.model.AddressCompact;
import uk.gov.ons.ctp.common.event.model.AddressNotValid;
import uk.gov.ons.ctp.common.event.model.CollectionCaseCompact;
import uk.gov.ons.ctp.common.event.model.Contact;
import uk.gov.ons.ctp.common.event.model.FulfilmentRequest;
import uk.gov.ons.ctp.common.event.model.RespondentRefusalDetails;
import uk.gov.ons.ctp.common.event.model.SurveyLaunchedResponse;
import uk.gov.ons.ctp.common.model.Language;
import uk.gov.ons.ctp.common.model.UniquePropertyReferenceNumber;
import uk.gov.ons.ctp.common.time.DateTimeUtil;
import uk.gov.ons.ctp.integration.caseapiclient.caseservice.CaseServiceClientServiceImpl;
import uk.gov.ons.ctp.integration.caseapiclient.caseservice.model.CaseContainerDTO;
import uk.gov.ons.ctp.integration.caseapiclient.caseservice.model.SingleUseQuestionnaireIdDTO;
import uk.gov.ons.ctp.integration.common.product.ProductReference;
import uk.gov.ons.ctp.integration.common.product.model.Product;
import uk.gov.ons.ctp.integration.common.product.model.Product.Region;
import uk.gov.ons.ctp.integration.contactcentresvc.CCSvcBeanMapper;
import uk.gov.ons.ctp.integration.contactcentresvc.config.AppConfig;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.CaseDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.CaseEventDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.CaseQueryRequestDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.CaseStatus;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.CaseType;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.DeliveryChannel;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.LaunchRequestDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.ModifyCaseRequestDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.PostalFulfilmentRequestDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.Reason;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.RefusalRequestDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.ResponseDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.SMSFulfilmentRequestDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.service.CaseService;
import uk.gov.ons.ctp.integration.eqlaunch.service.EqLaunchService;

@Service
@Validated()
@Configuration
public class CaseServiceImpl implements CaseService {

  private static final Logger log = LoggerFactory.getLogger(CaseServiceImpl.class);

  @Autowired private AppConfig appConfig;

  @Autowired private CaseServiceClientServiceImpl caseServiceClient;

  @Autowired private ProductReference productReference;

  private MapperFacade caseDTOMapper = new CCSvcBeanMapper();

  @Autowired private EqLaunchService eqLaunchService;

  @Autowired private EventPublisher eventPublisher;

  public ResponseDTO fulfilmentRequestByPost(PostalFulfilmentRequestDTO requestBodyDTO)
      throws CTPException {

    log.with(requestBodyDTO)
        .debug("Now in the fulfilmentRequestByPost method in class CaseServiceImpl.");

    UUID caseId = requestBodyDTO.getCaseId();

    Contact contact = new Contact();
    contact.setTitle(requestBodyDTO.getTitle());
    contact.setForename(requestBodyDTO.getForename());
    contact.setSurname(requestBodyDTO.getSurname());

    FulfilmentRequest fulfilmentRequestPayload =
        createFulfilmentRequestPayload(
            requestBodyDTO.getFulfilmentCode(), Product.DeliveryChannel.POST, caseId, contact);

    eventPublisher.sendEvent(
        EventType.FULFILMENT_REQUESTED,
        Source.CONTACT_CENTRE_API,
        appConfig.getChannel(),
        fulfilmentRequestPayload);

    ResponseDTO response =
        ResponseDTO.builder().id(caseId.toString()).dateTime(DateTimeUtil.nowUTC()).build();

    log.with(response)
        .debug("Now returning from the fulfilmentRequestByPost method in class CaseServiceImpl.");

    return response;
  }

  public ResponseDTO fulfilmentRequestBySMS(SMSFulfilmentRequestDTO requestBodyDTO)
      throws CTPException {
    log.with(requestBodyDTO)
        .debug("Now in the fulfilmentRequestBySMS method in class CaseServiceImpl.");

    UUID caseId = requestBodyDTO.getCaseId();

    Contact contact = new Contact();
    contact.setTelNo(requestBodyDTO.getTelNo());

    FulfilmentRequest fulfilmentRequestedPayload =
        createFulfilmentRequestPayload(
            requestBodyDTO.getFulfilmentCode(), Product.DeliveryChannel.SMS, caseId, contact);
    eventPublisher.sendEvent(
        EventType.FULFILMENT_REQUESTED,
        Source.CONTACT_CENTRE_API,
        appConfig.getChannel(),
        fulfilmentRequestedPayload);

    ResponseDTO response =
        ResponseDTO.builder().id(caseId.toString()).dateTime(DateTimeUtil.nowUTC()).build();

    log.with(response)
        .debug("Now returning from the fulfilmentRequestBySMS method in class CaseServiceImpl.");

    return response;
  }

  @Override
  public CaseDTO getCaseById(final UUID caseId, CaseQueryRequestDTO requestParamsDTO) {
    log.debug("Fetching case details by caseId: {}", caseId);

    // Get the case details from the case service
    Boolean getCaseEvents = requestParamsDTO.getCaseEvents();
    CaseContainerDTO caseDetails = caseServiceClient.getCaseById(caseId, getCaseEvents);

    // Do not return HI cases
    if (caseDetails.getCaseType().equals(CaseType.HI.name())) {
      log.with(caseId).info("Case is not suitable as it is a household individual case");
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Case is not suitable");
    }

    // Convert from Case service to Contact Centre DTOs NB. A request for an SPG case will not get
    // this far.
    CaseDTO caseServiceResponse = mapCaseContainerDTO(caseDetails);

    filterCaseEvents(caseServiceResponse, getCaseEvents);

    log.with("caseId", caseId).debug("Returning case details for caseId");

    return caseServiceResponse;
  }

  private CaseDTO mapCaseContainerDTO(CaseContainerDTO caseDetails) {
    CaseDTO caseServiceResponse = caseDTOMapper.map(caseDetails, CaseDTO.class);
    caseServiceResponse.setAllowedDeliveryChannels(
        calculateAllowedDeliveryChannels(caseServiceResponse));

    return caseServiceResponse;
  }

  private List<CaseDTO> mapCaseContainerDTOList(List<CaseContainerDTO> casesToReturn) {
    List<CaseDTO> caseServiceListResponse = caseDTOMapper.mapAsList(casesToReturn, CaseDTO.class);

    for (CaseDTO caseServiceResponse : caseServiceListResponse) {
      caseServiceResponse.setAllowedDeliveryChannels(
          calculateAllowedDeliveryChannels(caseServiceResponse));
    }

    return caseServiceListResponse;
  }

  private List<DeliveryChannel> calculateAllowedDeliveryChannels(CaseDTO caseServiceResponse) {

    List<DeliveryChannel> dcList = null;

    if (caseServiceResponse.isHandDelivery()
        && caseServiceResponse.getCaseType().equals(CaseType.SPG.name())) {
      log.with(caseServiceResponse.getId())
          .debug(
              "Calculating allowed delivery channel list as [SMS] because handDelivery=true "
                  + "and caseType=SPG");
      dcList = Arrays.asList(DeliveryChannel.SMS);
    } else {
      log.with(caseServiceResponse.getId())
          .debug("Calculating allowed delivery channel list as [POST, SMS]");
      dcList = Arrays.asList(DeliveryChannel.POST, DeliveryChannel.SMS);
    }

    return dcList;
  }

  @Override
  public List<CaseDTO> getCaseByUPRN(
      UniquePropertyReferenceNumber uprn, CaseQueryRequestDTO requestParamsDTO) {
    log.with("uprn", uprn).debug("Fetching case details by UPRN");

    // Get the case details from the case service
    Boolean getCaseEvents = requestParamsDTO.getCaseEvents();
    List<CaseContainerDTO> caseDetails =
        caseServiceClient.getCaseByUprn(uprn.getValue(), getCaseEvents);

    // Only return cases that are not of caseType = HI
    List<CaseContainerDTO> casesToReturn =
        (List<CaseContainerDTO>)
            caseDetails
                .parallelStream()
                .filter(c -> !(c.getCaseType().equals(CaseType.HI.name())))
                .collect(Collectors.toList());

    // Convert from Case service to Contact Centre DTOs
    List<CaseDTO> caseServiceResponse = mapCaseContainerDTOList(casesToReturn);

    // Clean up the events before returning them
    caseServiceResponse.stream().forEach(c -> filterCaseEvents(c, getCaseEvents));

    log.with("uprn", uprn)
        .with("cases", caseServiceResponse.size())
        .debug("Returning case details for UPRN");

    return caseServiceResponse;
  }

  @Override
  public CaseDTO getCaseByCaseReference(final long caseRef, CaseQueryRequestDTO requestParamsDTO) {
    log.with("caseRef", caseRef).debug("Fetching case details by case reference");

    // Get the case details from the case service
    Boolean getCaseEvents = requestParamsDTO.getCaseEvents();
    CaseContainerDTO caseDetails = caseServiceClient.getCaseByCaseRef(caseRef, getCaseEvents);

    // Do not return HI cases
    if (caseDetails.getCaseType().equals(CaseType.HI.name())) {
      log.with(caseDetails.getId())
          .info("Case is not suitable as it is a household individual case");
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Case is not suitable");
    }

    CaseDTO caseServiceResponse = mapCaseContainerDTO(caseDetails);

    filterCaseEvents(caseServiceResponse, getCaseEvents);

    log.with("caseRef", caseRef).debug("Returning case details for case reference");

    return caseServiceResponse;
  }

  @Override
  public ResponseDTO reportRefusal(UUID caseId, RefusalRequestDTO requestBodyDTO)
      throws CTPException {
    String reportedDateTime = "null";
    if (requestBodyDTO.getDateTime() != null) {
      reportedDateTime = DateTimeUtil.formatDate(requestBodyDTO.getDateTime());
    }
    log.with("caseId", caseId)
        .with("reportedDateTime", reportedDateTime)
        .debug("Processing refusal for case with reported dateTime");

    // Create and publish a respondent refusal event
    UUID refusalCaseId = caseId == null ? new UUID(0, 0) : caseId;
    RespondentRefusalDetails refusalPayload =
        createRespondentRefusalPayload(refusalCaseId, requestBodyDTO);

    eventPublisher.sendEvent(
        EventType.REFUSAL_RECEIVED,
        Source.CONTACT_CENTRE_API,
        appConfig.getChannel(),
        refusalPayload);

    // Build response
    ResponseDTO response =
        ResponseDTO.builder()
            .id(caseId == null ? UNKNOWN_UUID : caseId.toString())
            .dateTime(DateTimeUtil.nowUTC())
            .build();

    log.with("caseId", caseId).debug("Returning refusal response for case");

    return response;
  }

  @Override
  public String getLaunchURLForCaseId(final UUID caseId, LaunchRequestDTO requestParamsDTO)
      throws CTPException {
    log.with("caseId", caseId)
        .with("request", requestParamsDTO)
        .debug("Processing request to create launch URL");

    // Validate case known and is for CE or HH
    CaseContainerDTO caseDetails = caseServiceClient.getCaseById(caseId, false);
    CaseType caseType = CaseType.valueOf(caseDetails.getCaseType());
    if (!(caseType == CaseType.CE || caseType == CaseType.HH || caseType == CaseType.SPG)) {
      throw new CTPException(Fault.BAD_REQUEST, "Case type must be SPG, CE or HH");
    }

    // Create a new case if for a HH individual
    boolean individual = requestParamsDTO.getIndividual();
    UUID individualCaseId = null;
    if (caseType == CaseType.HH && individual == true) {
      individualCaseId = UUID.randomUUID();
      caseDetails.setId(individualCaseId);
      caseDetails.setCaseType(CaseType.HI.name());
      log.with("individualCaseId", individualCaseId).info("Creating new HI case");
    }

    // Get RM to allocate a new questionnaire ID
    log.info("Before new QID");
    SingleUseQuestionnaireIdDTO newQuestionnaireIdDto =
        caseServiceClient.getSingleUseQuestionnaireId(caseId, individual, individualCaseId);
    String questionnaireId = newQuestionnaireIdDto.getQuestionnaireId();
    String formType = newQuestionnaireIdDto.getFormType();
    log.with("newQuestionnaireID", questionnaireId)
        .with("formType", formType)
        .info("Have generated new questionnaireId");

    // Finally, build the url needed to launch the survey
    String encryptedPayload = "";
    try {
      encryptedPayload =
          eqLaunchService.getEqLaunchJwe(
              Language.ENGLISH,
              uk.gov.ons.ctp.common.model.Source.CONTACT_CENTRE_API,
              uk.gov.ons.ctp.common.model.Channel.CC,
              caseDetails,
              requestParamsDTO.getAgentId(),
              questionnaireId,
              formType,
              null,
              null,
              appConfig.getKeystore());
    } catch (CTPException e) {
      log.with(e).error("Failed to create JWE payload for eq launch");
      throw e;
    }

    // Create full launch URL
    String eqUrl = "https://" + appConfig.getEq().getHost() + "/session?token=" + encryptedPayload;
    log.with("launchURL", eqUrl).debug("Have created launch URL");

    // Finally tell RM that a survey has been launched
    publishSurveyLaunchedEvent(caseDetails.getId(), questionnaireId, requestParamsDTO.getAgentId());

    return eqUrl;
  }

  // will throw exception if case does not exist.
  private void verifyCaseExists(UUID caseId) {
    caseServiceClient.getCaseById(caseId, false);
  }

  @Override
  public ResponseDTO modifyCase(ModifyCaseRequestDTO modifyRequestDTO) throws CTPException {
    UUID caseId = modifyRequestDTO.getCaseId();

    log.with("caseId", caseId).with("status", modifyRequestDTO.getStatus()).debug("Modify Case");

    verifyCaseExists(caseId);

    CollectionCaseCompact collectionCase = new CollectionCaseCompact(caseId);

    if (CaseStatus.UNCHANGED == modifyRequestDTO.getStatus()) {
      log.with("caseId", caseId).debug("No event published since status is UNCHANGED");
    } else {
      log.debug("Case modified: publishing AddressNotValid event");
      AddressNotValid payload =
          AddressNotValid.builder()
              .collectionCase(collectionCase)
              .notes(modifyRequestDTO.getNotes())
              .reason(modifyRequestDTO.getStatus().name())
              .build();

      eventPublisher.sendEvent(
          EventType.ADDRESS_NOT_VALID, Source.CONTACT_CENTRE_API, appConfig.getChannel(), payload);
    }
    ResponseDTO response =
        ResponseDTO.builder().id(caseId.toString()).dateTime(DateTimeUtil.nowUTC()).build();

    log.with(response).debug("Return from modify case");
    return response;
  }

  private void publishSurveyLaunchedEvent(UUID caseId, String questionnaireId, String agentId) {
    log.with("questionnaireId", questionnaireId)
        .with("caseId", caseId)
        .with("agentId", agentId)
        .info("Generating SurveyLaunched event");

    SurveyLaunchedResponse response =
        SurveyLaunchedResponse.builder()
            .questionnaireId(questionnaireId)
            .caseId(caseId)
            .agentId(agentId)
            .build();

    String transactionId =
        eventPublisher.sendEvent(
            EventType.SURVEY_LAUNCHED, Source.CONTACT_CENTRE_API, appConfig.getChannel(), response);

    log.with("caseId", response.getCaseId())
        .with("transactionId", transactionId)
        .debug("SurveyLaunch event published");
  }

  private void filterCaseEvents(CaseDTO caseDTO, Boolean getCaseEvents) {
    if (getCaseEvents) {
      // Only return whitelisted events
      Set<String> whitelistedEventCategories =
          appConfig.getCaseServiceSettings().getWhitelistedEventCategories();
      List<CaseEventDTO> filteredEvents =
          caseDTO
              .getCaseEvents()
              .stream()
              .filter(e -> whitelistedEventCategories.contains(e.getCategory()))
              .collect(Collectors.toList());
      caseDTO.setCaseEvents(filteredEvents);
    } else {
      // Caller doesn't want any event data
      caseDTO.setCaseEvents(null);
    }
  }

  /**
   * create a contact centre fulfilment request event
   *
   * @param fulfilmentCode the code for the product requested
   * @param deliveryChannel how the fulfilment should be delivered
   * @param caseId the id of the household case the fulfilment is for
   * @return the request event to be delivered to the events exchange
   * @throws CTPException the requested product is invalid for the parameters given
   */
  private FulfilmentRequest createFulfilmentRequestPayload(
      String fulfilmentCode, Product.DeliveryChannel deliveryChannel, UUID caseId, Contact contact)
      throws CTPException {
    log.with(fulfilmentCode)
        .debug("Entering createFulfilmentEvent method in class CaseServiceImpl");

    CaseContainerDTO caze = caseServiceClient.getCaseById(caseId, false);
    Region region = Region.valueOf(caze.getRegion().substring(0, 1));
    Product product = findProduct(fulfilmentCode, deliveryChannel, region);

    if (deliveryChannel == Product.DeliveryChannel.POST) {
      if (caze.isHandDelivery()) {
        log.info("Postal fulfilments cannot be delivered to this respondent");
        throw new CTPException(
            Fault.BAD_REQUEST, "Postal fulfilments cannot be delivered to this respondent");
      }
      if (product.getIndividual()) {
        if (StringUtils.isBlank(contact.getTitle())
            || StringUtils.isBlank(contact.getForename())
            || StringUtils.isBlank(contact.getSurname())) {

          log.warn("Individual fields are required for the requested fulfilment");
          throw new CTPException(
              Fault.BAD_REQUEST,
              "The fulfilment is for an individual so none of the following fields can be empty: "
                  + "'title', 'forename' and 'surname'");
        }
      }
    }

    FulfilmentRequest fulfilmentRequest = new FulfilmentRequest();
    // create a new indiv id only if the parent case is an HH and the product requested is for an
    // indiv
    // SPG and CE indiv product requests do not need an indiv id creating
    if (CaseType.HH.name().equals(caze.getCaseType()) && product.getIndividual()) {
      fulfilmentRequest.setIndividualCaseId(UUID.randomUUID().toString());
    }

    fulfilmentRequest.setFulfilmentCode(product.getFulfilmentCode());
    fulfilmentRequest.setCaseId(caseId.toString());
    fulfilmentRequest.setContact(contact);

    // Get the case address details from the case service
    CaseContainerDTO caseDetails = caseServiceClient.getCaseById(caseId, false);
    Address address = new Address();
    address.setAddressLine1(caseDetails.getAddressLine1());
    address.setAddressLine2(caseDetails.getAddressLine2());
    address.setAddressLine3(caseDetails.getAddressLine3());
    address.setTownName(caseDetails.getTownName());
    address.setPostcode(caseDetails.getPostcode());
    address.setRegion(caseDetails.getRegion());
    address.setLatitude(caseDetails.getLatitude());
    address.setLongitude(caseDetails.getLongitude());
    address.setUprn(caseDetails.getUprn());
    address.setArid(caseDetails.getArid());
    address.setAddressType(caseDetails.getAddressType());
    address.setEstabType(caseDetails.getEstabType());
    fulfilmentRequest.setAddress(address);

    return fulfilmentRequest;
  }

  /**
   * find the product using the parameters provided
   *
   * @param fulfilmentCode the code for the product requested
   * @param deliveryChannel how should the fulfilment be delivered
   * @param region identifies the region of the household case the fulfilment is for - used to
   *     confirm the requested products eligibility
   * @return the matching product
   * @throws CTPException the product could not found or is ineligible for the given parameters
   */
  private Product findProduct(
      String fulfilmentCode, Product.DeliveryChannel deliveryChannel, Product.Region region)
      throws CTPException {
    log.with(fulfilmentCode)
        .with(deliveryChannel)
        .with(region)
        .debug("Passing fulfilmentCode, deliveryChannel, and region, into findProduct method.");
    Product searchCriteria =
        Product.builder()
            .fulfilmentCode(fulfilmentCode)
            .requestChannels(Arrays.asList(Product.RequestChannel.CC))
            .deliveryChannel(deliveryChannel)
            .regions(Arrays.asList(region))
            .build();
    List<Product> products = productReference.searchProducts(searchCriteria);
    if (products.size() == 0) {
      log.with("searchCriteria", searchCriteria).warn("Compatible product cannot be found");
      throw new CTPException(Fault.BAD_REQUEST, "Compatible product cannot be found");
    }

    return products.get(0);
  }

  /**
   * Create a case refusal event.
   *
   * @param caseId is the UUID for the case, or null if the endpoint was invoked with a caseId of
   *     'unknown'.
   * @param refusalRequest holds the details about the refusal.
   * @return the request event to be delivered to the events exchange.
   * @throws CTPException if there is a failure.
   */
  private RespondentRefusalDetails createRespondentRefusalPayload(
      UUID caseId, RefusalRequestDTO refusalRequest) throws CTPException {

    // Create message payload
    RespondentRefusalDetails refusal = new RespondentRefusalDetails();
    refusal.setType(mapToType(refusalRequest.getReason()));
    refusal.setReport(refusalRequest.getNotes());
    CollectionCaseCompact collectionCase = new CollectionCaseCompact(caseId);
    refusal.setCollectionCase(collectionCase);
    refusal.setAgentId(refusalRequest.getAgentId());

    // Populate contact
    Contact contact = new Contact();
    contact.setTitle(refusalRequest.getTitle());
    contact.setForename(refusalRequest.getForename());
    contact.setSurname(refusalRequest.getSurname());
    contact.setTelNo(refusalRequest.getTelNo());
    refusal.setContact(contact);

    // Populate address
    AddressCompact address = new AddressCompact();
    address.setAddressLine1(refusalRequest.getAddressLine1());
    address.setAddressLine2(refusalRequest.getAddressLine2());
    address.setAddressLine3(refusalRequest.getAddressLine3());
    address.setTownName(refusalRequest.getTownName());
    address.setPostcode(refusalRequest.getPostcode());
    address.setRegion(refusalRequest.getRegion().name());
    address.setUprn(Long.toString(refusalRequest.getUprn().getValue()));
    refusal.setAddress(address);

    return refusal;
  }

  private String mapToType(Reason reason) throws CTPException {
    switch (reason) {
      case HARD:
        return "HARD_REFUSAL";
      case EXTRAORDINARY:
        return "EXTRAORDINARY_REFUSAL";
      default:
        throw new CTPException(Fault.SYSTEM_ERROR, "Unexpected refusal reason: %s", reason);
    }
  }
}
