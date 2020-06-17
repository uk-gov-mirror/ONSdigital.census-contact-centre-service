package uk.gov.ons.ctp.integration.contactcentresvc.repository.impl;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import uk.gov.ons.ctp.common.cloud.CloudDataStore;
import uk.gov.ons.ctp.common.cloud.DataStoreContentionException;
import uk.gov.ons.ctp.common.domain.UniquePropertyReferenceNumber;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.common.error.CTPException.Fault;
import uk.gov.ons.ctp.integration.contactcentresvc.cloud.CachedCase;
import uk.gov.ons.ctp.integration.contactcentresvc.repository.CaseDataRepository;

@Service
public class CaseDataRepositoryImpl implements CaseDataRepository {

  private static final Logger log = LoggerFactory.getLogger(CaseDataRepositoryImpl.class);

  @Value("${GOOGLE_CLOUD_PROJECT}")
  private String gcpProject;

  @Value("${cloud-storage.case-schema-name}")
  private String caseSchemaName;

  private String caseSchema;

  @Autowired private CloudDataStore cloudDataStore;

  // This is the name of the document that is used to create and retain the new-case collection
  private static String PLACEHOLDER_CASE_NAME = "placeholder";

  @PostConstruct
  @Override
  public void init() throws CTPException {
    caseSchema = gcpProject + "-" + caseSchemaName.toLowerCase();
    this.cloudDataStore.connect();
    ensureCollectionExists(caseSchema);
  }

  private void ensureCollectionExists(String collectionName) throws CTPException {
    log.with("collectionName", collectionName).info("Checking if collection exists");

    Set<String> collectionNames = cloudDataStore.getCollectionNames();

    if (!collectionNames.contains(collectionName)) {
      log.with("collectionName", collectionName).info("Creating collection");

      try {
        // Force collection creation by adding an object.
        // Firestore doesn't have the concept of an empty collection. A collection only exists
        // when it holds at least one document. So we therefore have to leave the placeholder
        // object to keep the collection.
        CachedCase dummyCase = new CachedCase();
        cloudDataStore.storeObject(collectionName, PLACEHOLDER_CASE_NAME, dummyCase);
      } catch (Exception e) {
        log.error("Failed to create collection", e);
        throw new CTPException(Fault.SYSTEM_ERROR, e);
      }
    }

    log.with("collectionName", collectionName).info("Collection check completed");
  }

  @Retryable(
      label = "writeNewCase",
      include = DataStoreContentionException.class,
      backoff =
          @Backoff(
              delayExpression = "#{${cloud-storage.backoff-initial}}",
              multiplierExpression = "#{${cloud-storage.backoff-multiplier}}",
              maxDelayExpression = "#{${cloud-storage.backoff-max}}"),
      maxAttemptsExpression = "#{${cloud-storage.backoff-max-attempts}}",
      listeners = "cloudRetryListener")
  @Override
  public void writeCachedCase(final CachedCase caze)
      throws CTPException, DataStoreContentionException {
    cloudDataStore.storeObject(caseSchema, caze.getId(), caze);
  }

  @Override
  public Optional<CachedCase> readCachedCaseByUPRN(final UniquePropertyReferenceNumber uprn)
      throws CTPException {

    String key = String.valueOf(uprn.getValue());
    String[] searchByUprnPath = new String[] {"uprn"};
    List<CachedCase> results =
        cloudDataStore.search(CachedCase.class, caseSchema, searchByUprnPath, key);

    if (results.isEmpty()) {
      return Optional.empty();
    } else if (results.size() > 1) {
      log.with("uprn", key).error("More than one cached skeleton case for UPRN");
      throw new CTPException(
          Fault.SYSTEM_ERROR, "More than one cached skeleton case for UPRN: " + key);
    } else {
      return Optional.ofNullable(results.get(0));
    }
  }

  @Override
  public Optional<CachedCase> readCachedCaseById(final UUID caseId) throws CTPException {
    return cloudDataStore.retrieveObject(CachedCase.class, caseSchema, caseId.toString());
  }
}
