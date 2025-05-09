/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package ddf.test.itests.catalog;

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.semantics.Action.bytesContent;
import static com.xebialabs.restito.semantics.Action.composite;
import static com.xebialabs.restito.semantics.Action.contentType;
import static com.xebialabs.restito.semantics.Action.header;
import static com.xebialabs.restito.semantics.Action.ok;
import static com.xebialabs.restito.semantics.Action.success;
import static com.xebialabs.restito.semantics.Condition.withPostBodyContaining;
import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.path.json.JsonPath.with;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.codice.ddf.itests.common.AbstractIntegrationTest.DynamicUrl.INSECURE_ROOT;
import static org.codice.ddf.itests.common.catalog.CatalogTestCommons.ingest;
import static org.codice.ddf.itests.common.csw.CswTestCommons.CSW_FEDERATED_SOURCE_FACTORY_PID;
import static org.codice.ddf.itests.common.csw.CswTestCommons.GMD_CSW_FEDERATED_SOURCE_FACTORY_PID;
import static org.codice.ddf.itests.common.csw.CswTestCommons.getCswQuery;
import static org.codice.ddf.itests.common.csw.CswTestCommons.getCswSourceProperties;
import static org.codice.ddf.itests.common.csw.CswTestCommons.getCswSubscription;
import static org.codice.ddf.itests.common.opensearch.OpenSearchTestCommons.OPENSEARCH_FACTORY_PID;
import static org.codice.ddf.itests.common.opensearch.OpenSearchTestCommons.getOpenSearch;
import static org.codice.ddf.itests.common.opensearch.OpenSearchTestCommons.getOpenSearchSourceProperties;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.hasXPath;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;

import com.google.common.collect.ImmutableMap;
import com.xebialabs.restito.semantics.Action;
import com.xebialabs.restito.semantics.Call;
import com.xebialabs.restito.semantics.Condition;
import com.xebialabs.restito.server.StubServer;
import com.xebialabs.restito.server.secure.SecureStubServer;
import ddf.catalog.data.Metacard;
import ddf.catalog.endpoint.CatalogEndpoint;
import ddf.catalog.endpoint.impl.CatalogEndpointImpl;
import io.restassured.http.ContentType;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.ValidatableResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.karaf.bundle.core.BundleService;
import org.codice.ddf.itests.common.AbstractIntegrationTest;
import org.codice.ddf.itests.common.annotations.ConditionalIgnoreRule;
import org.codice.ddf.itests.common.config.UrlResourceReaderConfigurator;
import org.codice.ddf.itests.common.csw.CswQueryBuilder;
import org.codice.ddf.itests.common.csw.mock.FederatedCswMockServer;
import org.codice.ddf.itests.common.restito.ChunkedContent;
import org.codice.ddf.spatial.ogc.csw.catalog.common.CswConstants;
import org.codice.ddf.test.common.LoggingUtils;
import org.codice.ddf.test.common.annotations.AfterExam;
import org.codice.ddf.test.common.annotations.BeforeExam;
import org.glassfish.grizzly.http.Method;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.osgi.framework.Bundle;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tests Federation aspects. */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class TestFederation extends AbstractIntegrationTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestFederation.class);

  private static final String SAMPLE_DATA = "sample data";

  private static final String SUBSCRIBER = "/subscriber";

  private static final int EVENT_UPDATE_WAIT_INTERVAL = 200;

  private static final int XML_RECORD_INDEX = 1;

  private static final int GEOJSON_RECORD_INDEX = 0;

  private static final String DEFAULT_KEYWORD = "text";

  private static final String RECORD_TITLE_1 = "myTitle";

  private static final String RECORD_TITLE_2 = "myXmlTitle";

  private static final String CONNECTED_SOURCE_ID = "cswConnectedSource";

  private static final String CSW_STUB_SOURCE_ID = "cswStubServer";

  private static final String CSW_SOURCE_WITH_METACARD_XML_ID = "cswSource2";

  private static final String GMD_SOURCE_ID = "gmdSource";

  private static final String DEFAULT_URL_RESOURCE_READER_ROOT_RESOURCE_DIRS = "data/products";

  private static final String DEFAULT_SAMPLE_PRODUCT_FILE_NAME = "sample.txt";

  private static final DynamicPort RESTITO_STUB_SERVER_PORT = new DynamicPort(6);

  private static final DynamicUrl RESTITO_STUB_SERVER =
      new DynamicUrl("https://localhost:", RESTITO_STUB_SERVER_PORT, SUBSCRIBER);

  private static final Path PRODUCT_CACHE = Paths.get("data", "Product_Cache");

  private static final DynamicPort CSW_STUB_SERVER_PORT = new DynamicPort(7);

  private static final DynamicUrl CSW_STUB_SERVER_PATH =
      new DynamicUrl(INSECURE_ROOT, CSW_STUB_SERVER_PORT, "/services/csw");

  private static final int NO_RETRIES = 0;

  private static final String POLL_INTERVAL = "pollInterval";

  private static final String ADMIN_USERNAME = "admin";

  private static final String ADMIN_PASSWORD = "admin";

  private static final String LOCALHOST_USERNAME = "localhost";

  private static final String LOCALHOST_PASSWORD = "localhost";

  private static final int CSW_SOURCE_POLL_INTERVAL = 10;

  private static final int MAX_DOWNLOAD_RETRY_ATTEMPTS = 3;

  private static final String[] METACARD_IDS = new String[2];

  private static StubServer server;

  private static FederatedCswMockServer cswServer;

  private static String openSearchPid;

  private static String cswPid;

  private static String cswPid2;

  private static String cswPid3;

  private static String gmdPid;

  private static String connectedPid;

  @Rule public TestName testName = new TestName();

  @Rule public ConditionalIgnoreRule rule = new ConditionalIgnoreRule();

  private final List<String> resourcesToDelete = new ArrayList<>();

  private UrlResourceReaderConfigurator urlResourceReaderConfigurator;

  public static String getSimpleXml(String uri) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
        + getFileContent(
            XML_RECORD_RESOURCE_PATH + "/SimpleXmlNoDecMetacard", ImmutableMap.of("uri", uri));
  }

  @BeforeExam
  public void beforeExam() {
    try {
      getSecurityPolicy().configureRestForGuest();

      getCatalogBundle().setupMaxDownloadRetryAttempts(MAX_DOWNLOAD_RETRY_ATTEMPTS);

      Map<String, Object> openSearchProperties =
          getOpenSearchSourceProperties(
              OPENSEARCH_SOURCE_ID, OPENSEARCH_PATH.getUrl(), getServiceManager());
      openSearchProperties.put("username", "localhost");
      openSearchProperties.put("password", "localhost");
      openSearchPid =
          getServiceManager()
              .createManagedService(OPENSEARCH_FACTORY_PID, openSearchProperties)
              .getPid();

      cswServer =
          new FederatedCswMockServer(
              CSW_STUB_SOURCE_ID, INSECURE_ROOT, Integer.parseInt(CSW_STUB_SERVER_PORT.getPort()));
      cswServer.start();

      Map<String, Object> cswStubServerProperties =
          getCswSourceProperties(CSW_STUB_SOURCE_ID, CSW_PATH.getUrl(), getServiceManager());
      cswStubServerProperties.put("cswUrl", CSW_STUB_SERVER_PATH.getUrl());
      cswStubServerProperties.put(POLL_INTERVAL, CSW_SOURCE_POLL_INTERVAL);
      cswPid =
          getServiceManager()
              .createManagedService(CSW_FEDERATED_SOURCE_FACTORY_PID, cswStubServerProperties)
              .getPid();

      getServiceManager().waitForHttpEndpoint(CSW_PATH + "?_wadl");

      Map<String, Object> cswProperties =
          getCswSourceProperties(CSW_SOURCE_ID, CSW_PATH.getUrl(), getServiceManager());

      cswProperties.put(POLL_INTERVAL, CSW_SOURCE_POLL_INTERVAL);
      cswProperties.put("username", "localhost");
      cswProperties.put("password", "localhost");
      cswPid2 =
          getServiceManager()
              .createManagedService(CSW_FEDERATED_SOURCE_FACTORY_PID, cswProperties)
              .getPid();

      Map<String, Object> cswProperties2 =
          getCswSourceProperties(
              CSW_SOURCE_WITH_METACARD_XML_ID, CSW_PATH.getUrl(), getServiceManager());
      cswProperties2.put("outputSchema", "urn:catalog:metacard");
      cswProperties2.put(POLL_INTERVAL, CSW_SOURCE_POLL_INTERVAL);
      cswProperties2.put("username", "localhost");
      cswProperties2.put("password", "localhost");
      cswPid3 =
          getServiceManager()
              .createManagedService(CSW_FEDERATED_SOURCE_FACTORY_PID, cswProperties2)
              .getPid();

      Map<String, Object> gmdProperties =
          getCswSourceProperties(
              GMD_SOURCE_ID,
              GMD_CSW_FEDERATED_SOURCE_FACTORY_PID,
              CSW_PATH.getUrl(),
              getServiceManager());

      gmdProperties.put(POLL_INTERVAL, CSW_SOURCE_POLL_INTERVAL);
      gmdPid =
          getServiceManager()
              .createManagedService(GMD_CSW_FEDERATED_SOURCE_FACTORY_PID, gmdProperties)
              .getPid();

      getCatalogBundle().waitForFederatedSource(OPENSEARCH_SOURCE_ID);
      getCatalogBundle().waitForFederatedSource(CSW_STUB_SOURCE_ID);
      getCatalogBundle().waitForFederatedSource(CSW_SOURCE_ID);
      getCatalogBundle().waitForFederatedSource(CSW_SOURCE_WITH_METACARD_XML_ID);
      getCatalogBundle().waitForFederatedSource(GMD_SOURCE_ID);

      getServiceManager()
          .waitForSourcesToBeAvailable(
              REST_PATH.getUrl(),
              OPENSEARCH_SOURCE_ID,
              CSW_STUB_SOURCE_ID,
              CSW_SOURCE_ID,
              CSW_SOURCE_WITH_METACARD_XML_ID,
              GMD_SOURCE_ID);

      LOGGER.info("Source status: \n{}", get(REST_PATH.getUrl() + "sources").body().prettyPrint());

    } catch (Exception e) {
      LoggingUtils.failWithThrowableStacktrace(e, "Failed in @BeforeExam: ");
    }
  }

  @AfterExam
  public void afterExam() throws Exception {
    if (openSearchPid != null) {
      getServiceManager().stopManagedService(openSearchPid);
    }
    if (cswPid != null) {
      getServiceManager().stopManagedService(cswPid);
    }
    if (cswPid2 != null) {
      getServiceManager().stopManagedService(cswPid2);
    }
    if (cswPid3 != null) {
      getServiceManager().stopManagedService(cswPid3);
    }
    if (gmdPid != null) {
      getServiceManager().stopManagedService(gmdPid);
    }
    if (cswServer != null) {
      cswServer.stop();
    }
  }

  @Before
  public void setup() throws Exception {

    getCatalogBundle().setDownloadRetryDelayInSeconds(1);

    getCatalogBundle().setupCaching(false);
    urlResourceReaderConfigurator = getUrlResourceReaderConfigurator();

    server = new SecureStubServer(Integer.parseInt(RESTITO_STUB_SERVER_PORT.getPort())).run();
    server.start();

    METACARD_IDS[GEOJSON_RECORD_INDEX] =
        ingest(
            getFileContent(JSON_RECORD_RESOURCE_PATH + "/SimpleGeoJsonRecord"), "application/json");

    METACARD_IDS[XML_RECORD_INDEX] = ingestXmlWithProduct(DEFAULT_SAMPLE_PRODUCT_FILE_NAME);

    cswServer.reset();
  }

  @After
  public void tearDown() throws Exception {

    clearCatalog();

    urlResourceReaderConfigurator.setUrlResourceReaderRootDirs(
        DEFAULT_URL_RESOURCE_READER_ROOT_RESOURCE_DIRS);

    for (String resource : resourcesToDelete) {
      FileUtils.deleteQuietly(new File(resource));
    }

    resourcesToDelete.clear();

    cswServer.stop();

    getSecurityPolicy().configureRestForGuest();

    if (server != null) {
      server.stop();
    }
  }

  /**
   * Given what was ingested in beforeTest(), tests that a Federated wildcard search will return all
   * appropriate record(s).
   */
  @Test
  public void testFederatedQueryByWildCardSearchPhrase() {
    ValidatableResponse response =
        getOpenSearch("xml", null, null, "q=*", "src=" + OPENSEARCH_SOURCE_ID);

    response
        .assertThat()
        .body(
            hasXPath(
                "/metacards/metacard/string[@name='"
                    + Metacard.TITLE
                    + "']/value[text()='"
                    + RECORD_TITLE_1
                    + "']"),
            hasXPath("/metacards/metacard/geometry/value"),
            hasXPath(
                "/metacards/metacard/string[@name='"
                    + Metacard.TITLE
                    + "']/value[text()='"
                    + RECORD_TITLE_2
                    + "']"),
            hasXPath("/metacards/metacard/stringxml"));
  }

  /**
   * Given what was ingested in beforeTest(), tests that a Federated wildcard search will return all
   * appropriate record(s) in ATOM format.
   */
  @Test
  public void testAtomFederatedQueryByWildCardSearchPhrase() {
    ValidatableResponse response =
        getOpenSearch("atom", null, null, "q=*", "src=" + OPENSEARCH_SOURCE_ID);

    response
        .assertThat()
        .body(
            hasXPath("/feed/entry/title[text()='" + RECORD_TITLE_1 + "']"),
            hasXPath("/feed/entry/title[text()='" + RECORD_TITLE_2 + "']"),
            hasXPath("/feed/entry/content/metacard/geometry/value"));
  }

  /**
   * Given what was ingested in beforeTest(), tests that a Federated search phrase will return the
   * appropriate record(s).
   */
  @Test
  public void testFederatedQueryBySearchPhrase() {
    ValidatableResponse response =
        getOpenSearch("xml", null, null, "q=" + DEFAULT_KEYWORD, "src=" + OPENSEARCH_SOURCE_ID);

    response
        .assertThat()
        .body(
            hasXPath(
                "/metacards/metacard/string[@name='"
                    + Metacard.TITLE
                    + "']/value[text()='"
                    + RECORD_TITLE_1
                    + "']"),
            hasXPath(
                "/metacards/metacard/string[@name='"
                    + Metacard.TITLE
                    + "']/value[text()='"
                    + RECORD_TITLE_2
                    + "']"));
  }

  /** Tests given bad spatial query, no result should be returned */
  @Test
  public void testFederatedNegativeSpatial() {
    ValidatableResponse response =
        getOpenSearch(
            "xml",
            null,
            null,
            "lat=-10.0",
            "lon=-30.0",
            "radius=1",
            "spatialType=POINT_RADIUS",
            "src=" + OPENSEARCH_SOURCE_ID);

    response
        .assertThat()
        .body(not(containsString(RECORD_TITLE_1)), not(containsString(RECORD_TITLE_2)));
  }

  /** Tests that given a bad test phrase, no records should have been returned. */
  @Test
  public void testFederatedQueryByNegativeSearchPhrase() {
    String negativeSearchPhrase = "negative";
    ValidatableResponse response =
        getOpenSearch(
            "xml", null, null, "q=" + negativeSearchPhrase, "src=" + OPENSEARCH_SOURCE_ID);

    response
        .assertThat()
        .body(not(containsString(RECORD_TITLE_1)), not(containsString(RECORD_TITLE_2)));
  }

  /** Tests that a federated search by ID will return the right record. */
  @Test
  public void testFederatedQueryById() {
    String restUrl =
        REST_PATH.getUrl()
            + "sources/"
            + OPENSEARCH_SOURCE_ID
            + "/"
            + METACARD_IDS[GEOJSON_RECORD_INDEX];

    when()
        .get(restUrl)
        .then()
        .assertThat()
        .body(
            hasXPath(
                "/metacard/string[@name='"
                    + Metacard.TITLE
                    + "']/value[text()='"
                    + RECORD_TITLE_1
                    + "']"),
            not(containsString(RECORD_TITLE_2)));
  }

  /**
   * Tests that a federated search by ID will return the right record after we change the id.
   *
   * @throws Exception
   */
  @Test
  public void testFederatedQueryByIdAfterIdChange() throws Exception {
    Configuration openSourceConfig = null;
    String newOpenSearchSourceId = OPENSEARCH_SOURCE_ID + "2";
    try {
      // change the opensearch source id
      Map<String, Object> openSearchProperties =
          getOpenSearchSourceProperties(
              newOpenSearchSourceId, OPENSEARCH_PATH.getUrl(), getServiceManager());
      Configuration[] configs =
          configAdmin.listConfigurations(
              String.format(
                  "(%s=%s)", ConfigurationAdmin.SERVICE_FACTORYPID, OPENSEARCH_FACTORY_PID));
      openSourceConfig = configs[0];
      Dictionary<String, ?> configProps = new Hashtable<>(openSearchProperties);
      openSourceConfig.update(configProps);
      getServiceManager().waitForAllBundles();

      String restUrl =
          REST_PATH.getUrl()
              + "sources/"
              + newOpenSearchSourceId
              + "/"
              + METACARD_IDS[GEOJSON_RECORD_INDEX];

      when()
          .get(restUrl)
          .then()
          .assertThat()
          .body(
              hasXPath(
                  "/metacard/string[@name='"
                      + Metacard.TITLE
                      + "']/value[text()='"
                      + RECORD_TITLE_1
                      + "']"),
              not(containsString(RECORD_TITLE_2)));
    } finally {
      // reset the opensearch source id
      Map<String, Object> openSearchProperties =
          getOpenSearchSourceProperties(
              OPENSEARCH_SOURCE_ID, OPENSEARCH_PATH.getUrl(), getServiceManager());
      Dictionary<String, ?> configProps = new Hashtable<>(openSearchProperties);
      Objects.requireNonNull(openSourceConfig).update(configProps);
      getServiceManager().waitForAllBundles();
    }
  }

  /**
   * Tests Source can retrieve existing product. The product is located in one of the
   * URLResourceReader's root resource directories, so it can be downloaded.
   *
   * @throws Exception
   */
  @Test
  public void testFederatedRetrieveExistingProduct() throws Exception {
    String fileName = testName.getMethodName() + ".txt";
    String metacardId = ingestXmlWithProduct(fileName);
    String productDirectory = new File(fileName).getAbsoluteFile().getParent();
    urlResourceReaderConfigurator.setUrlResourceReaderRootDirs(
        DEFAULT_URL_RESOURCE_READER_ROOT_RESOURCE_DIRS, productDirectory);

    String restUrl =
        REST_PATH.getUrl()
            + "sources/"
            + OPENSEARCH_SOURCE_ID
            + "/"
            + metacardId
            + "?transform=resource";

    // Perform Test and Verify
    when().get(restUrl).then().assertThat().body(is(SAMPLE_DATA));
  }

  /**
   * Tests Source can retrieve existing product. The product is located in one of the
   * URLResourceReader's root resource directories, so it can be downloaded.
   *
   * @throws Exception
   */
  @Test
  public void testFederatedRetrieveExistingProductWithRange() throws Exception {
    String fileName = testName.getMethodName() + ".txt";
    String metacardId = ingestXmlWithProduct(fileName);
    String productDirectory = new File(fileName).getAbsoluteFile().getParent();
    urlResourceReaderConfigurator.setUrlResourceReaderRootDirs(
        DEFAULT_URL_RESOURCE_READER_ROOT_RESOURCE_DIRS, productDirectory);

    int offset = 4;
    byte[] sampleDataByteArray = SAMPLE_DATA.getBytes();
    String partialSampleData =
        new String(Arrays.copyOfRange(sampleDataByteArray, offset, sampleDataByteArray.length));

    String restUrl =
        REST_PATH.getUrl()
            + "sources/"
            + OPENSEARCH_SOURCE_ID
            + "/"
            + metacardId
            + "?transform=resource";

    // Perform Test and Verify
    given()
        .header(CswConstants.RANGE_HEADER, String.format("bytes=%s-", offset))
        .get(restUrl)
        .then()
        .assertThat()
        .contentType("text/plain")
        .body(is(partialSampleData));
  }

  /**
   * Tests Source CANNOT retrieve existing product. The product is NOT located in one of the
   * URLResourceReader's root resource directories, so it CANNOT be downloaded.
   *
   * @throws Exception
   */
  @Test
  public void testFederatedRetrieveProductInvalidResourceUrl() throws Exception {
    // Setup
    String fileName = testName.getMethodName() + ".txt";
    String metacardId = ingestXmlWithProduct(fileName);
    urlResourceReaderConfigurator.setUrlResourceReaderRootDirs(
        DEFAULT_URL_RESOURCE_READER_ROOT_RESOURCE_DIRS);

    String restUrl =
        REST_PATH.getUrl()
            + "sources/"
            + OPENSEARCH_SOURCE_ID
            + "/"
            + metacardId
            + "?transform=resource";

    // Perform Test and Verify
    when()
        .get(restUrl)
        .then()
        .assertThat()
        .contentType("text/html")
        .statusCode(equalTo(500))
        .body(containsString("Unable to transform Metacard."));
  }

  @Test
  public void testFederatedRetrieveExistingProductCsw() throws Exception {
    String productDirectory =
        new File(DEFAULT_SAMPLE_PRODUCT_FILE_NAME).getAbsoluteFile().getParent();
    urlResourceReaderConfigurator.setUrlResourceReaderRootDirs(
        DEFAULT_URL_RESOURCE_READER_ROOT_RESOURCE_DIRS, productDirectory);

    String restUrl =
        REST_PATH.getUrl()
            + "sources/"
            + CSW_SOURCE_ID
            + "/"
            + METACARD_IDS[XML_RECORD_INDEX]
            + "?transform=resource";

    when().get(restUrl).then().assertThat().contentType("text/plain").body(is(SAMPLE_DATA));
  }

  /**
   * Tests Source can retrieve nonexistent product.
   *
   * @throws Exception
   */
  @Test
  public void testFederatedRetrieveNoProduct() throws Exception {
    // Setup
    urlResourceReaderConfigurator.setUrlResourceReaderRootDirs(
        DEFAULT_URL_RESOURCE_READER_ROOT_RESOURCE_DIRS);
    String restUrl =
        REST_PATH.getUrl()
            + "sources/"
            + OPENSEARCH_SOURCE_ID
            + "/"
            + METACARD_IDS[GEOJSON_RECORD_INDEX]
            + "?transform=resource";

    // Perform Test and Verify
    when().get(restUrl).then().assertThat().statusCode(equalTo(500));
  }

  @Test
  public void testCswQueryByWildCardSearchPhrase() {
    String wildcardQuery =
        getCswQuery("AnyText", "*", "application/xml", "http://www.opengis.net/cat/csw/2.0.2");

    given()
        .contentType(ContentType.XML)
        .body(wildcardQuery)
        .when()
        .post(CSW_PATH.getUrl())
        .then()
        .assertThat()
        .body(
            hasXPath(
                "/GetRecordsResponse/SearchResults/Record/identifier[text()='"
                    + METACARD_IDS[GEOJSON_RECORD_INDEX]
                    + "']"),
            hasXPath(
                "/GetRecordsResponse/SearchResults/Record/identifier[text()='"
                    + METACARD_IDS[XML_RECORD_INDEX]
                    + "']"),
            hasXPath("/GetRecordsResponse/SearchResults/@numberOfRecordsReturned", is("2")),
            hasXPath(
                "/GetRecordsResponse/SearchResults/Record/relation",
                containsString("/services/catalog/sources/")));
  }

  @Test
  public void testCswQueryWithValidationCheckerPlugin() {

    // Construct a query to search for all metacards
    String query =
        new CswQueryBuilder()
            .addAttributeFilter(CswQueryBuilder.PROPERTY_IS_LIKE, "AnyText", "*")
            .getQuery();

    // Declare array of matchers so we can be sure we use the same matchers in each assertion
    Matcher[] assertion =
        new Matcher[] {
          hasXPath(
              "/GetRecordsResponse/SearchResults/Record/identifier[text()='"
                  + METACARD_IDS[GEOJSON_RECORD_INDEX]
                  + "']"),
          hasXPath(
              "/GetRecordsResponse/SearchResults/Record/identifier[text()='"
                  + METACARD_IDS[XML_RECORD_INDEX]
                  + "']"),
          hasXPath("/GetRecordsResponse/SearchResults/@numberOfRecordsReturned", is("2")),
          hasXPath(
              "/GetRecordsResponse/SearchResults/Record/relation",
              containsString("/services/catalog/sources/"))
        };

    // Run a normal federated query to the CSW source and assert response
    given()
        .contentType(ContentType.XML)
        .body(query)
        .when()
        .post(CSW_PATH.getUrl())
        .then()
        .assertThat()
        .body(assertion[0], assertion);

    // Assert that response is the same as without the plugin
    given()
        .contentType(ContentType.XML)
        .body(query)
        .when()
        .post(CSW_PATH.getUrl())
        .then()
        .assertThat()
        .body(assertion[0], assertion);
  }

  @Test
  public void testCswQueryForMetacardXml() {
    String titleQuery = getCswQuery("title", "myTitle", "application/xml", "urn:catalog:metacard");

    given()
        .contentType(ContentType.XML)
        .body(titleQuery)
        .when()
        .post(CSW_PATH.getUrl())
        .then()
        .assertThat()
        .body(
            hasXPath(
                "/GetRecordsResponse/SearchResults/metacard/@id",
                is(METACARD_IDS[GEOJSON_RECORD_INDEX])),
            hasXPath("/GetRecordsResponse/SearchResults/@numberOfRecordsReturned", is("1")),
            hasXPath(
                "/GetRecordsResponse/SearchResults/@recordSchema", is("urn:catalog:metacard")));
  }

  @Test
  public void testCswQueryForJson() {
    String titleQuery = getCswQuery("title", "myTitle", "application/json", null);

    given()
        .headers("Accept", "application/json", "Content-Type", "application/xml")
        .body(titleQuery)
        .when()
        .post(CSW_PATH.getUrl())
        .then()
        .assertThat()
        .contentType(ContentType.JSON)
        .body("results[0].metacard.properties.title", equalTo(RECORD_TITLE_1));
  }

  @Test
  public void testOpensearchToCswSourceToCswEndpointQuerywithCswRecordXml() {
    ValidatableResponse response =
        getOpenSearch("xml", null, null, "q=" + DEFAULT_KEYWORD, "src=" + CSW_SOURCE_ID);

    response
        .assertThat()
        .body(
            containsString(RECORD_TITLE_1),
            containsString(RECORD_TITLE_2),
            hasXPath(
                "/metacards/metacard/string[@name='" + Metacard.RESOURCE_DOWNLOAD_URL + "']",
                containsString("/services/catalog/sources/" + CSW_SOURCE_ID)));
  }

  @Test
  public void testOpensearchToCswSourceToCswEndpointQuerywithMetacardXml() {
    ValidatableResponse response =
        getOpenSearch(
            "xml", null, null, "q=" + DEFAULT_KEYWORD, "src=" + CSW_SOURCE_WITH_METACARD_XML_ID);

    response
        .assertThat()
        .body(
            containsString(RECORD_TITLE_1),
            containsString(RECORD_TITLE_2),
            hasXPath(
                "/metacards/metacard/string[@name='" + Metacard.RESOURCE_DOWNLOAD_URL + "']",
                containsString("/services/catalog/sources/" + CSW_SOURCE_ID)));
  }

  @Test
  public void testOpensearchToGmdSourceToGmdEndpointQuery() {
    ValidatableResponse response =
        getOpenSearch("xml", null, null, "q=" + RECORD_TITLE_1, "src=" + GMD_SOURCE_ID);

    response
        .assertThat()
        .body(
            containsString(RECORD_TITLE_1),
            hasXPath(
                "/metacards/metacard/stringxml/value/MD_Metadata/fileIdentifier/CharacterString",
                is(METACARD_IDS[GEOJSON_RECORD_INDEX])));
  }

  @Test
  public void testListAllSourceInfo() throws Exception {

    // TODO: Connected csw/wfs sources are broken. Ticket: DDF-1366
    /*
    try {
        setupConnectedSources();
    } catch (IOException e) {
        logger.error("Couldn't create connected sources: {}", e.searchMessages());
    }
    */

    try {
      getSecurityPolicy().configureRestForBasic();
      given()
          .auth()
          .preemptive()
          .basic(ADMIN_USERNAME, ADMIN_PASSWORD)
          .header("X-Requested-With", "XMLHttpRequest")
          .header("Origin", ADMIN_ALL_SOURCES_PATH.getUrl())
          .when()
          .get(ADMIN_ALL_SOURCES_PATH.getUrl())
          .then()
          .assertThat()
          .body(
              containsString("\"fpid\":\"OpenSearchSource\""),
              containsString("\"fpid\":\"Csw_Federated_Source\"") /*,
                  containsString("\"fpid\":\"Csw_Connected_Source\"")*/);
    } finally {
      getSecurityPolicy().configureRestForGuest();
    }
  }

  @Test
  public void testFederatedSourceStatus() throws Exception {

    try {
      getSecurityPolicy().configureRestForBasic();

      // Find and test OpenSearch Federated Source
      String json =
          given()
              .auth()
              .preemptive()
              .basic(ADMIN_USERNAME, ADMIN_PASSWORD)
              .header("X-Requested-With", "XMLHttpRequest")
              .header("Origin", ADMIN_ALL_SOURCES_PATH.getUrl())
              .when()
              .get(ADMIN_ALL_SOURCES_PATH.getUrl())
              .asString();

      List<Map<String, Object>> sources =
          with(json)
              .param("name", "OpenSearchSource")
              .get("value.findAll { source -> source.id == name}");
      String openSearchPid =
          (String)
              ((ArrayList<Map<String, Object>>) (sources.get(0).get("configurations")))
                  .get(0)
                  .get("id");

      given()
          .auth()
          .basic(ADMIN_USERNAME, ADMIN_PASSWORD)
          .header("X-Requested-With", "XMLHttpRequest")
          .header("Origin", ADMIN_STATUS_PATH.getUrl())
          .when()
          .get(ADMIN_STATUS_PATH.getUrl() + openSearchPid)
          .then()
          .assertThat()
          .body(containsString("\"value\":true"));
    } finally {
      getSecurityPolicy().configureRestForGuest();
    }
  }

  @Test
  public void testCatalogEndpointExposure() {
    // Check the service references
    ArrayList<String> expectedEndpoints = new ArrayList<>();
    expectedEndpoints.add("endpointUrl");
    expectedEndpoints.add("cswUrl");

    CatalogEndpoint endpoint = getServiceManager().getService(CatalogEndpoint.class);
    String urlBindingName =
        endpoint.getEndpointProperties().get(CatalogEndpointImpl.URL_BINDING_NAME_KEY);

    assertTrue(
        "Catalog endpoint url binding name: '" + urlBindingName + "' is expected.",
        expectedEndpoints.contains(urlBindingName));
  }

  @Test
  public void testCswSubscriptionByWildCardSearchPhrase() {
    whenHttp(server).match(Condition.post(SUBSCRIBER)).then(success());
    whenHttp(server).match(Condition.get(SUBSCRIBER)).then(success());
    whenHttp(server).match(Condition.delete(SUBSCRIBER)).then(success());
    whenHttp(server).match(Condition.put(SUBSCRIBER)).then(success());

    String wildcardQuery = getCswSubscription("xml", "*", RESTITO_STUB_SERVER.getUrl());

    String subscriptionId =
        given()
            .contentType(ContentType.XML)
            .body(wildcardQuery)
            .when()
            .post(CSW_SUBSCRIPTION_PATH.getUrl())
            .then()
            .assertThat()
            .body(hasXPath("/Acknowledgement/RequestId"))
            .extract()
            .body()
            .xmlPath()
            .get("Acknowledgement.RequestId")
            .toString();

    given()
        .contentType(ContentType.XML)
        .when()
        .get(CSW_SUBSCRIPTION_PATH.getUrl() + "/" + subscriptionId)
        .then()
        .assertThat()
        .body(hasXPath("/Acknowledgement/RequestId"));

    String metacardId =
        ingest(
            getFileContent(JSON_RECORD_RESOURCE_PATH + "/SimpleGeoJsonRecord"), "application/json");

    String[] subscrptionIds = {subscriptionId};

    verifyEvents(
        Collections.singleton(metacardId),
        new HashSet<>(0),
        new HashSet<>(Arrays.asList(subscrptionIds)));

    given()
        .contentType(ContentType.XML)
        .when()
        .delete(CSW_SUBSCRIPTION_PATH.getUrl() + "/" + subscriptionId)
        .then()
        .assertThat()
        .body(hasXPath("/Acknowledgement/RequestId"));

    given()
        .contentType(ContentType.XML)
        .when()
        .get(CSW_SUBSCRIPTION_PATH.getUrl() + "/" + subscriptionId)
        .then()
        .assertThat()
        .statusCode(404);
  }

  @Test
  public void testCswDurableSubscription() throws Exception {
    whenHttp(server).match(Condition.post(SUBSCRIBER)).then(success());
    whenHttp(server).match(Condition.get(SUBSCRIBER)).then(success());
    whenHttp(server).match(Condition.delete(SUBSCRIBER)).then(success());
    whenHttp(server).match(Condition.put(SUBSCRIBER)).then(success());

    String wildcardQuery = getCswSubscription("xml", "*", RESTITO_STUB_SERVER.getUrl());

    // CswSubscribe
    String subscriptionId =
        given()
            .contentType(ContentType.XML)
            .body(wildcardQuery)
            .when()
            .post(CSW_SUBSCRIPTION_PATH.getUrl())
            .then()
            .assertThat()
            .body(hasXPath("/Acknowledgement/RequestId"))
            .extract()
            .body()
            .xmlPath()
            .get("Acknowledgement.RequestId")
            .toString();

    BundleService bundleService = getServiceManager().getService(BundleService.class);
    Bundle bundle = bundleService.getBundle("spatial-csw-endpoint");
    bundle.stop();

    await("Waiting for bundle to resolve")
        .atMost(1, MINUTES)
        .pollDelay(1, SECONDS)
        .until(() -> bundle.getState() == Bundle.RESOLVED);

    bundle.start();
    await("Waiting for bundle to start")
        .atMost(1, MINUTES)
        .pollDelay(1, SECONDS)
        .until(() -> bundle.getState() == Bundle.ACTIVE);

    getServiceManager().waitForHttpEndpoint(CSW_SUBSCRIPTION_PATH + "?_wadl");
    // get subscription
    given()
        .contentType(ContentType.XML)
        .when()
        .get(CSW_SUBSCRIPTION_PATH.getUrl() + "/" + subscriptionId)
        .then()
        .assertThat()
        .body(hasXPath("/Acknowledgement/RequestId"));

    String metacardId =
        ingest(
            getFileContent(JSON_RECORD_RESOURCE_PATH + "/SimpleGeoJsonRecord"), "application/json");

    String[] subscrptionIds = {subscriptionId};

    verifyEvents(
        Collections.singleton(metacardId),
        new HashSet<>(0),
        new HashSet<>(Arrays.asList(subscrptionIds)));

    given()
        .contentType(ContentType.XML)
        .when()
        .delete(CSW_SUBSCRIPTION_PATH.getUrl() + "/" + subscriptionId)
        .then()
        .assertThat()
        .body(hasXPath("/Acknowledgement/RequestId"));

    given()
        .contentType(ContentType.XML)
        .when()
        .get(CSW_SUBSCRIPTION_PATH.getUrl() + "/" + subscriptionId)
        .then()
        .assertThat()
        .statusCode(404);
  }

  @Test
  public void testCswCreateEventEndpoint() {
    whenHttp(server).match(Condition.post(SUBSCRIBER)).then(success());
    whenHttp(server).match(Condition.get(SUBSCRIBER)).then(success());
    whenHttp(server).match(Condition.delete(SUBSCRIBER)).then(success());
    whenHttp(server).match(Condition.put(SUBSCRIBER)).then(success());

    String wildcardQuery = getCswSubscription("xml", "*", RESTITO_STUB_SERVER.getUrl());

    String metacardId = "5b1688fa85fd46268e4ab7402a1750e0";
    String event = getFileContent("get-records-response.xml");

    String subscriptionId =
        given()
            .contentType(ContentType.XML)
            .body(wildcardQuery)
            .when()
            .post(CSW_SUBSCRIPTION_PATH.getUrl())
            .then()
            .assertThat()
            .body(hasXPath("/Acknowledgement/RequestId"))
            .extract()
            .body()
            .xmlPath()
            .get("Acknowledgement.RequestId")
            .toString();

    given()
        .contentType(ContentType.XML)
        .when()
        .get(CSW_SUBSCRIPTION_PATH.getUrl() + "/" + subscriptionId)
        .then()
        .assertThat()
        .body(hasXPath("/Acknowledgement/RequestId"));

    given()
        .contentType(ContentType.XML)
        .body(event)
        .when()
        .post(CSW_EVENT_PATH.getUrl())
        .then()
        .assertThat()
        .statusCode(200);

    String[] subscrptionIds = {subscriptionId};

    verifyEvents(
        Collections.singleton(metacardId),
        new HashSet<>(0),
        new HashSet<>(Arrays.asList(subscrptionIds)));

    given()
        .contentType(ContentType.XML)
        .when()
        .delete(CSW_SUBSCRIPTION_PATH.getUrl() + "/" + subscriptionId)
        .then()
        .assertThat()
        .body(hasXPath("/Acknowledgement/RequestId"));

    given()
        .contentType(ContentType.XML)
        .when()
        .get(CSW_SUBSCRIPTION_PATH.getUrl() + "/" + subscriptionId)
        .then()
        .assertThat()
        .statusCode(404);
  }

  /** Tests basic download from the live federated csw source */
  @Test
  public void testDownloadFromFederatedCswSource() {

    String filename = "product1.txt";
    String metacardId = generateUniqueMetacardId();
    String resourceData = getResourceData(metacardId);
    Action response = new ChunkedContent.ChunkedContentBuilder(resourceData).build();

    cswServer
        .whenHttp()
        .match(
            Condition.post("/services/csw"),
            withPostBodyContaining("GetRecords"),
            withPostBodyContaining(metacardId))
        .then(
            ok(),
            contentType("text/xml"),
            bytesContent(getCswQueryResponse(metacardId).getBytes()));

    cswServer
        .whenHttp()
        .match(
            Condition.get("/services/csw"),
            Condition.parameter("request", "GetRecordById"),
            Condition.parameter("id", metacardId))
        .then(getCswRetrievalHeaders(filename), response);

    String restUrl =
        REST_PATH.getUrl()
            + "sources/"
            + CSW_STUB_SOURCE_ID
            + "/"
            + metacardId
            + "?transform=resource";

    // Verify that the testData from the csw stub server is returned.

    when().get(restUrl).then().assertThat().contentType("text/plain").body(is(resourceData));
  }

  /**
   * Tests that if the endpoint disconnects twice, the retrieval retries both times
   *
   * @throws Exception
   */
  @Test
  public void testRetrievalReliablility() throws Exception {
    try {
      getSecurityPolicy().configureWebContextPolicy("PKI|BASIC", "PKI|BASIC", null, null);

      String filename = "product2.txt";
      String metacardId = generateUniqueMetacardId();
      String resourceData = getResourceData(metacardId);
      Action response =
          new ChunkedContent.ChunkedContentBuilder(resourceData)
              .delayBetweenChunks(Duration.ofMillis(200))
              .fail(2)
              .build();

      cswServer
          .whenHttp()
          .match(
              Condition.post("/services/csw"),
              withPostBodyContaining("GetRecords"),
              withPostBodyContaining(metacardId))
          .then(
              ok(),
              contentType("text/xml"),
              bytesContent(getCswQueryResponse(metacardId).getBytes()));

      cswServer
          .whenHttp()
          .match(
              Condition.get("/services/csw"),
              Condition.parameter("request", "GetRecordById"),
              Condition.parameter("id", metacardId))
          .then(getCswRetrievalHeaders(filename), response);

      String restUrl =
          REST_PATH.getUrl()
              + "sources/"
              + CSW_STUB_SOURCE_ID
              + "/"
              + metacardId
              + "?transform=resource";

      // Verify that the testData from the csw stub server is returned.
      given()
          .auth()
          .preemptive()
          .basic(LOCALHOST_USERNAME, LOCALHOST_PASSWORD)
          .get(restUrl)
          .then()
          .assertThat()
          .contentType("text/plain")
          .body(is(resourceData));

      cswServer
          .verifyHttp()
          .times(
              3,
              Condition.uri("/services/csw"),
              Condition.parameter("request", "GetRecordById"),
              Condition.parameter("id", metacardId));
    } finally {
      getSecurityPolicy().configureRestForGuest();
    }
  }

  /**
   * Tests that if the endpoint disconnects twice, the retrieval retries both times This test will
   * respond with the correct Partial Content when a range header is sent in the request
   */
  @Test
  public void testRetrievalWithByteOffset() {

    String filename = "product2.txt";
    String metacardId = generateUniqueMetacardId();
    String resourceData = getResourceData(metacardId);
    Action response =
        new ChunkedContent.ChunkedContentBuilder(resourceData)
            .delayBetweenChunks(Duration.ofMillis(200))
            .fail(2)
            .build();

    cswServer
        .whenHttp()
        .match(
            Condition.post("/services/csw"),
            withPostBodyContaining("GetRecords"),
            withPostBodyContaining(metacardId))
        .then(
            ok(),
            contentType("text/xml"),
            bytesContent(getCswQueryResponse(metacardId).getBytes()));

    cswServer
        .whenHttp()
        .match(
            Condition.get("/services/csw"),
            Condition.parameter("request", "GetRecordById"),
            Condition.parameter("id", metacardId))
        .then(getCswRetrievalHeaders(filename), response);

    String restUrl =
        REST_PATH.getUrl()
            + "sources/"
            + CSW_STUB_SOURCE_ID
            + "/"
            + metacardId
            + "?transform=resource";

    // Verify that the testData from the csw stub server is returned.
    when().get(restUrl).then().assertThat().contentType("text/plain").body(is(resourceData));

    cswServer
        .verifyHttp()
        .times(
            3,
            Condition.uri("/services/csw"),
            Condition.parameter("request", "GetRecordById"),
            Condition.parameter("id", metacardId));
  }

  /** Tests that if the endpoint disconnects 3 times, the retrieval fails after 3 attempts */
  @Test
  public void testRetrievalReliabilityFails() {

    String filename = "product3.txt";
    String metacardId = generateUniqueMetacardId();
    String resourceData = getResourceData(metacardId);
    Action response =
        new ChunkedContent.ChunkedContentBuilder(resourceData)
            .delayBetweenChunks(Duration.ofMillis(200))
            .fail(3)
            .build();

    cswServer
        .whenHttp()
        .match(
            Condition.post("/services/csw"),
            withPostBodyContaining("GetRecords"),
            withPostBodyContaining(metacardId))
        .then(
            ok(),
            contentType("text/xml"),
            bytesContent(getCswQueryResponse(metacardId).getBytes()));

    cswServer
        .whenHttp()
        .match(
            Condition.get("/services/csw"),
            Condition.parameter("request", "GetRecordById"),
            Condition.parameter("id", metacardId))
        .then(getCswRetrievalHeaders(filename), response);

    String restUrl =
        REST_PATH.getUrl()
            + "sources/"
            + CSW_STUB_SOURCE_ID
            + "/"
            + metacardId
            + "?transform=resource";

    // Verify that product retrieval fails from the csw stub server.
    when()
        .get(restUrl)
        .then()
        .assertThat()
        .statusCode(500)
        .contentType("text/plain")
        .body(containsString("cannot retrieve product"));
  }

  @Test
  public void testEnterpriseSearch() throws Exception {

    try {
      getSecurityPolicy().configureRestForGuest();

      String queryUrl = OPENSEARCH_PATH.getUrl() + "?q=" + RECORD_TITLE_1 + "&format=xml";
      given()
          .get(queryUrl)
          .then()
          .statusCode(200)
          .assertThat()
          .body(
              hasXPath("/metacards/metacard/source[text()='ddf.distribution']"),
              hasXPath("/metacards/metacard/source[text()='" + OPENSEARCH_SOURCE_ID + "']"),
              hasXPath("/metacards/metacard/source[text()='" + CSW_SOURCE_ID + "']"));
    } finally {
      getSecurityPolicy().configureRestForGuest();
    }
  }

  /** Tests that ddf will redownload a product if the remote metacard has changed */
  @Test
  public void testCacheIsUpdatedIfRemoteProductChanges() {
    String filename = "product5.txt";
    String metacardId = generateUniqueMetacardId();
    String resourceData = getResourceData(metacardId);
    Action response = new ChunkedContent.ChunkedContentBuilder(resourceData).build();

    cswServer
        .whenHttp()
        .match(
            Condition.post("/services/csw"),
            withPostBodyContaining("GetRecords"),
            withPostBodyContaining(metacardId))
        .then(
            ok(),
            contentType("text/xml"),
            bytesContent(getCswQueryResponse(metacardId).getBytes()));

    cswServer
        .whenHttp()
        .match(
            Condition.get("/services/csw"),
            Condition.parameter("request", "GetRecordById"),
            Condition.parameter("id", metacardId))
        .then(getCswRetrievalHeaders(filename), response);

    String restUrl =
        REST_PATH.getUrl()
            + "sources/"
            + CSW_STUB_SOURCE_ID
            + "/"
            + metacardId
            + "?transform=resource";

    // Download product twice, and change metacard on stub server between calls.
    when().get(restUrl).then().assertThat().contentType("text/plain").body(is(resourceData));
    cswServer
        .whenHttp()
        .match(
            Condition.post("/services/csw"),
            withPostBodyContaining("GetRecords"),
            withPostBodyContaining(metacardId))
        .then(
            ok(),
            contentType("text/xml"),
            bytesContent(getCswQueryResponse(metacardId, OffsetDateTime.now()).getBytes()));
    when().get(restUrl).then().assertThat().contentType("text/plain").body(is(resourceData));

    cswServer
        .verifyHttp()
        .times(
            2,
            Condition.uri("/services/csw"),
            Condition.parameter("request", "GetRecordById"),
            Condition.parameter("id", metacardId));
  }

  @Test
  public void testProductDownloadWithTwoUsers() throws Exception {
    try {
      getSecurityPolicy().configureWebContextPolicy("PKI|BASIC", "PKI|BASIC", null, null);

      String filename1 = "product4.txt";
      String metacardId1 = generateUniqueMetacardId();
      String resourceData1 = getResourceData(metacardId1);
      Action response1 = new ChunkedContent.ChunkedContentBuilder(resourceData1).build();

      cswServer
          .whenHttp()
          .match(
              Condition.post("/services/csw"),
              withPostBodyContaining("GetRecords"),
              withPostBodyContaining(metacardId1))
          .then(
              ok(),
              contentType("text/xml"),
              bytesContent(getCswQueryResponse(metacardId1).getBytes()));

      cswServer
          .whenHttp()
          .match(
              Condition.get("/services/csw"),
              Condition.parameter("request", "GetRecordById"),
              Condition.parameter("id", metacardId1))
          .then(getCswRetrievalHeaders(filename1), response1);

      String filename2 = "product5.txt";
      String metacardId2 = generateUniqueMetacardId();
      String resourceData2 = getResourceData(metacardId2);
      Action response2 = new ChunkedContent.ChunkedContentBuilder(resourceData2).build();

      cswServer
          .whenHttp()
          .match(
              Condition.post("/services/csw"),
              withPostBodyContaining("GetRecords"),
              withPostBodyContaining(metacardId2))
          .then(
              ok(),
              contentType("text/xml"),
              bytesContent(getCswQueryResponse(metacardId2).getBytes()));

      cswServer
          .whenHttp()
          .match(
              Condition.get("/services/csw"),
              Condition.parameter("request", "GetRecordById"),
              Condition.parameter("id", metacardId2))
          .then(getCswRetrievalHeaders(filename2), response2);

      String resourceDownloadUrlAdminUser =
          String.format(
              "%ssources/%s/%s?transform=resource",
              REST_PATH.getUrl(), CSW_STUB_SOURCE_ID, metacardId1);

      String resourceDownloadUrlLocalhostUser =
          String.format(
              "%ssources/%s/%s?transform=resource",
              REST_PATH.getUrl(), CSW_STUB_SOURCE_ID, metacardId2);

      given()
          .auth()
          .preemptive()
          .basic(ADMIN_USERNAME, ADMIN_PASSWORD)
          .when()
          .get(resourceDownloadUrlAdminUser)
          .then()
          .assertThat()
          .contentType("text/plain")
          .body(is(resourceData1));
      given()
          .auth()
          .preemptive()
          .basic(LOCALHOST_USERNAME, LOCALHOST_PASSWORD)
          .get(resourceDownloadUrlLocalhostUser)
          .then()
          .assertThat()
          .contentType("text/plain")
          .body(is(resourceData2));

      cswServer
          .verifyHttp()
          .times(
              1,
              Condition.uri("/services/csw"),
              Condition.parameter("request", "GetRecordById"),
              Condition.parameter("id", metacardId1));

      cswServer
          .verifyHttp()
          .times(
              1,
              Condition.uri("/services/csw"),
              Condition.parameter("request", "GetRecordById"),
              Condition.parameter("id", metacardId2));
    } finally {
      getCatalogBundle().setupCaching(false);
      getSecurityPolicy().configureRestForGuest();
      getSecurityPolicy().waitForGuestAuthReady(SERVICE_ROOT.getUrl());
    }
  }

  private String generateUniqueMetacardId() {
    return UUID.randomUUID().toString();
  }

  private String getCswQueryResponse(String metacardId) {
    return getCswQueryResponse(
        metacardId, OffsetDateTime.of(2016, 6, 15, 12, 30, 25, 100, ZoneOffset.ofHours(-7)));
  }

  private String getCswQueryResponse(String metacardId, OffsetDateTime modifiedTimestamp) {
    String modifiedTime =
        modifiedTimestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));

    return getFileContent(
        "csw-query-response.xml",
        ImmutableMap.of(
            "sourceId",
            CSW_STUB_SOURCE_ID,
            "httpRoot",
            INSECURE_ROOT,
            "port",
            CSW_STUB_SERVER_PORT.getPort(),
            "modifiedTime",
            modifiedTime,
            "metacardId",
            metacardId));
  }

  private void verifyEvents(
      Set<String> metacardIdsExpected,
      Set<String> metacardIdsNotExpected,
      Set<String> subscriptionIds) {

    await("Waiting for events to be received")
        .atMost(2, MINUTES)
        .pollDelay(EVENT_UPDATE_WAIT_INTERVAL, TimeUnit.MILLISECONDS)
        .until(
            () ->
                verifySubscriptions(subscriptionIds, metacardIdsExpected, metacardIdsNotExpected));
  }

  private boolean verifySubscriptions(
      Set<String> subscriptionIds, Set<String> expected, Set<String> unexpected) {
    return subscriptionIds.stream()
        .map(this::getEvents)
        .allMatch(ids -> areIdsCorrect(ids, expected, unexpected));
  }

  private boolean areIdsCorrect(Set<String> result, Set<String> expected, Set<String> unexpected) {
    return result.containsAll(expected) && !result.removeAll(unexpected);
  }

  private Set<String> getEvents(String subscriptionId) {

    HashSet<String> foundIds = new HashSet<>();
    List<Call> calls = new ArrayList<>(server.getCalls());

    if (CollectionUtils.isNotEmpty(calls)) {
      for (Call call : calls) {

        if (call.getMethod().matchesMethod(Method.POST.getMethodString())
            && StringUtils.isNotEmpty(call.getPostBody())) {
          LOGGER.debug("Event received '{}'", call.getPostBody());

          XmlPath xmlPath = new XmlPath(call.getPostBody());
          String id;
          try {
            String foundSubscriptionId = xmlPath.get("GetRecordsResponse.RequestId");

            if (StringUtils.isNotBlank(foundSubscriptionId)
                && subscriptionId.equals(foundSubscriptionId)) {
              id = xmlPath.get("GetRecordsResponse.SearchResults.Record.identifier");

              if (StringUtils.isNotEmpty(id)) {
                foundIds.add(StringUtils.trim(id));
              }
            } else {
              LOGGER.info("event for id {} not found.", subscriptionId);
            }
          } catch (ClassCastException e) {
            // not necessarily a problem that an particular path (event) wasn't found
            LOGGER.info("Unable to evaluate path for event {}", subscriptionId);
          }
        }
      }

      LOGGER.debug(
          "Id {}, Event Found Ids: {}", subscriptionId, Arrays.toString(foundIds.toArray()));
    }
    return foundIds;
  }

  private String ingestXmlWithProduct(String fileName) throws IOException {
    Path path = Paths.get(fileName);
    if (Files.exists(path)) {
      Files.delete(path);
    }

    Files.createFile(path);
    Files.write(path, SAMPLE_DATA.getBytes());

    String fileLocation = path.toUri().toURL().toString();
    LOGGER.debug("File Location: {}", fileLocation);
    return ingest(getSimpleXml(fileLocation), "text/xml");
  }

  private Action getCswRetrievalHeaders(String filename) {
    return composite(
        header("X-Csw-Product", "true"), header("Content-Disposition", "filename=" + filename));
  }

  private String getResourceData(String metacardId) {
    return String.format("Data for metacard ID %s", metacardId);
  }

  @Override
  protected Option[] configureCustom() {
    return combineOptions(
        super.configureCustom(),
        options(mavenBundle("ddf.thirdparty", "restito").versionAsInProject()));
  }
}
