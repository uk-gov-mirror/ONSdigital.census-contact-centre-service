package uk.gov.ons.ctp.integration.contactcentresvc.endpoint;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.List;
import javax.validation.Valid;
import ma.glasnost.orika.MapperFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.ons.ctp.common.endpoint.CTPEndpoint;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.FulfilmentDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.FulfilmentsRequestDTO;

/** The REST controller for ContactCentreSvc Fulfilments end points */
@RestController
@RequestMapping(value = "/", produces = "application/json")
public final class FulfilmentsEndpoint implements CTPEndpoint {
  private static final Logger log = LoggerFactory.getLogger(FulfilmentsEndpoint.class);

  private MapperFacade mapperFacade;

  /** Constructor for ContactCentre Fulfilment endpoint */
  @Autowired
  public FulfilmentsEndpoint(final MapperFacade mapperFacade) {
    this.mapperFacade = mapperFacade;
  }

  /**
   * the GET end point to retrieve fulfilment ie product codes for case type and region
   *
   * @param caseType the case type (optional)
   * @param region the region (optional)
   * @return the list of fulfilments
   * @throws CTPException something went wrong
   */
  @RequestMapping(value = "/fulfilments", method = RequestMethod.GET)
  public ResponseEntity<List<FulfilmentDTO>> getFulfilments(@Valid FulfilmentsRequestDTO requestDTO)
      throws CTPException {
    log.with("caseType", requestDTO.getCaseType())
        .with("region", requestDTO.getRegion())
        .debug("Entering getFulfilments");
    return ResponseEntity.ok(null);
  }
}
