package uk.gov.ons.ctp.integration.contactcentresvc.client.caseservice.model;

import java.util.Date;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Domain model object */
@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ResponseDTO {
  private String inboundChannel;

  private Date dateTime;
}
