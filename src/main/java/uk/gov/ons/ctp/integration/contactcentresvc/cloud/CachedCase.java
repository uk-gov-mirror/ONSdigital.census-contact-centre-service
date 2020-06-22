package uk.gov.ons.ctp.integration.contactcentresvc.cloud;

import java.util.Date;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.ons.ctp.common.domain.CaseType;

@Data
@NoArgsConstructor
public class CachedCase {

  private String id;

  private String uprn;

  private Date createdDateTime;

  private String formattedAddress;

  private String addressLine1;

  private String addressLine2;

  private String addressLine3;

  private String townName;

  private String postcode;

  private String addressType;

  private CaseType caseType;

  private String estabType; // Holds value of EstabType.code

  private String region;
  
  private String ceOrgName;
}
