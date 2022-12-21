package io.camunda.connector;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

@OutboundConnector(
    name = "UiPathConnector",
    inputVariables = {"organizationName", "organizationId", "tenant", "clientId", "clientKey", "packageName", "pollingInterval", "robotOutput"},
    type = UiPathConnectorFunction.TYPE_CONNECTOR)

public class UiPathConnectorFunction implements OutboundConnectorFunction {

  public static final String TYPE_CONNECTOR = "io.camunda:uipath";
  private static final Logger LOGGER = LoggerFactory.getLogger(UiPathConnectorFunction.class);
  private static final String BASE_UIPATH_URL = "https://cloud.uipath.com/";
  private static final String UIPATH_AUTH_URL = "https://account.uipath.com/oauth/token";
  private static final String UIPATH_CONTENT_TYPE = "application/json";

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    var connectorRequest = context.getVariablesAsType(UiPathConnectorRequest.class);
    String jsonString = context.getVariables();
    JSONObject json = new JSONObject(jsonString);
    JSONObject robotInput = json.getJSONObject("robotInput");
    connectorRequest.setRobotInput(robotInput);

    context.validate(connectorRequest);
    context.replaceSecrets(connectorRequest);

    return executeConnector(connectorRequest);
  }

  private UiPathConnectorResult executeConnector(final UiPathConnectorRequest connectorRequest) {

    String output = ""; // Output from UiPath bot

    try {
      // Get access token
      String body = "{\n" +
              "    \"grant_type\": \"refresh_token\",\n" +
              "    \"client_id\": \""+connectorRequest.getClientId()+"\",\n" +
              "    \"refresh_token\": \""+connectorRequest.getClientKey()+"\"\n" +
              "}";

      HashMap<String, String> map = new HashMap<>();
      map.put("Content-Type", UIPATH_CONTENT_TYPE);

      HttpResponse<String> response = this.makePOSTCall(UIPATH_AUTH_URL, body, map);

      String access_token = new JSONObject(response.body()).getString("access_token");

      // Set other headers for next calls
      map.put("Authorization", "Bearer "+access_token);
      map.put("X-UIPATH-OrganizationUnitId", connectorRequest.getOrganizationId());

      // Search for releases based on provided package name in UiPath
      response = this.makeGETCall(BASE_UIPATH_URL+connectorRequest.getOrganizationName()+"/"+connectorRequest.getTenant()+"/orchestrator_/odata/Releases?$filter=Name%20eq%20'"+connectorRequest.getPackageName()+"'", map);

      // Get release key for next call. Need to make sure at least one has been returned
      JSONArray value = new JSONObject(response.body()).getJSONArray("value");
      String releaseKey = value.getJSONObject(0).getString("Key");

      // Now start the job
      body = "{\"startInfo\":{\"ReleaseKey\":\""+releaseKey+"\",\"Strategy\":\"JobsCount\",\"JobsCount\":1,\"InputArguments\":"+JSONObject.valueToString(connectorRequest.getRobotInput().toString())+"}}";

      response = this.makePOSTCall(BASE_UIPATH_URL+connectorRequest.getOrganizationName()+"/"+connectorRequest.getTenant()+"/orchestrator_/odata/Jobs/UiPath.Server.Configuration.OData.StartJobs", body, map);

      // Get Job ID for next call. Need to make sure at least one has been returned
      value = new JSONObject(response.body()).getJSONArray("value");
      String jobId = value.getJSONObject(0).getBigInteger("Id").toString();

      // Now poll for successful job completion. Retrieve output, if any, and send back to Camunda
      String state = "Pending";

      while(state.equals("Running") || state.equals("Pending")) {
        response = this.makeGETCall(BASE_UIPATH_URL+connectorRequest.getOrganizationName()+"/"+connectorRequest.getTenant()+"/orchestrator_/odata/Jobs("+jobId+")", map);

        state = new JSONObject(response.body()).getString("State");
        Thread.sleep(connectorRequest.getPollingInterval() * 1000);
      }

      // Get output
      output = new JSONObject(response.body()).getString("OutputArguments");


    } catch(Exception e) {
      LOGGER.error("Error in UiPath connector "+e);
    }

    // Send output back to Camunda
    var result = new UiPathConnectorResult();
    result.setResult(output);
    return result;
  }

  private HttpResponse<String> makePOSTCall(String url, String body, HashMap headers) {

    HttpResponse<String> response = null;

    try {
      HttpClient client = HttpClient.newHttpClient();

      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .headers(this.createHeaderStringArray(headers))
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();

      response = client.send(request, HttpResponse.BodyHandlers.ofString());

    } catch (IOException | InterruptedException e) {
        LOGGER.error("Error when executing POST: "+e);
    }
    return response;
  }

  private HttpResponse<String> makeGETCall(String url, HashMap headers) {

    HttpResponse<String> response = null;
    try {
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create(url))
              .headers(this.createHeaderStringArray(headers))
              .GET()
              .build();

      response = client.send(request, HttpResponse.BodyHandlers.ofString());

    } catch (IOException | InterruptedException e) {
      LOGGER.error("Error when executing GET: "+e);
    }
    return response;
  }

  private String[] createHeaderStringArray (HashMap map) {
    ArrayList<String> headers = new ArrayList<String>();

    map.forEach((key, value) -> {
      headers.add(key.toString());
      headers.add(value.toString());
    });

    String[] output = Arrays.copyOf(headers.toArray(), headers.size(), String[].class);
    return output;
  }
}
