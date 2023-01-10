package io.camunda.connector;

import com.google.gson.Gson;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.http.HttpJsonFunction;
import io.camunda.connector.http.model.HttpJsonRequest;
import io.camunda.connector.http.model.HttpJsonResult;
import io.camunda.connector.http.auth.Authentication;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@OutboundConnector(
    name = "UiPathConnector",
    inputVariables = {"organizationName", "organizationId", "tenant", "clientId", "clientKey", "packageName", "pollingInterval"},
    type = UiPathConnectorFunction.TYPE_CONNECTOR)

public class UiPathConnectorFunction extends HttpJsonFunction implements OutboundConnectorFunction {

  public static final String TYPE_CONNECTOR = "io.camunda:uipath";
  private static final Logger LOGGER = LoggerFactory.getLogger(UiPathConnectorFunction.class);
  private static final String BASE_UIPATH_URL = "https://cloud.uipath.com/";
  private static final String UIPATH_AUTH_URL = "https://account.uipath.com/oauth/token";
  private static final String UIPATH_CONTENT_TYPE = "application/json";

  //@Override
  public UiPathConnectorResult execute(OutboundConnectorContext context) throws java.io.IOException {
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
      String bodyString = "{\n" +
            "    \"grant_type\": \"refresh_token\",\n" +
            "    \"client_id\": \""+connectorRequest.getClientId()+"\",\n" +
            "    \"refresh_token\": \""+connectorRequest.getClientKey()+"\"\n" +
            "}";

      Gson gson = new Gson();
      Map body = gson.fromJson(bodyString, Map.class);

      HashMap<String, String> headers = new HashMap<>();
      headers.put("Content-Type", UIPATH_CONTENT_TYPE);

      io.camunda.connector.http.auth.Authentication auth =  new io.camunda.connector.http.auth.NoAuthentication();

      Map respMap = this.makeRESTCall(UIPATH_AUTH_URL,"POST", auth, headers, body);

      String access_token = respMap.get("access_token").toString();

      // Set other headers for next calls
      headers.put("Authorization", "Bearer "+access_token);
      headers.put("X-UIPATH-OrganizationUnitId", connectorRequest.getOrganizationId());

      // Set bearer token authorization
      auth = new io.camunda.connector.http.auth.BearerAuthentication();

      // Search for releases based on provided package name in UiPath
      body = gson.fromJson("", Map.class);
      respMap = this.makeRESTCall(BASE_UIPATH_URL+connectorRequest.getOrganizationName()+"/"+connectorRequest.getTenant()+"/orchestrator_/odata/Releases?$filter=Name%20eq%20'"+connectorRequest.getPackageName()+"'","GET", auth, headers, body);

      // Get release key for next call. Need to make sure at least one has been returned
      JSONArray value = new JSONObject(respMap).getJSONArray("value");
      String releaseKey = value.getJSONObject(0).getString("Key");

      // Now start the job
      body = gson.fromJson("{\"startInfo\":{\"ReleaseKey\":\""+releaseKey+"\",\"Strategy\":\"JobsCount\",\"JobsCount\":\"1\",\"InputArguments\":"+JSONObject.valueToString(connectorRequest.getRobotInput().toString())+"}}", Map.class);

      respMap = this.makeRESTCall(BASE_UIPATH_URL+connectorRequest.getOrganizationName()+"/"+connectorRequest.getTenant()+"/orchestrator_/odata/Jobs/UiPath.Server.Configuration.OData.StartJobs","POST", auth, headers, body);

      // Get Job ID for next call. Need to make sure at least one has been returned
      value = new JSONObject(respMap).getJSONArray("value");
      String jobId = value.getJSONObject(0).getBigInteger("Id").toString();

      // Now poll for successful job completion. Retrieve output, if any, and send back to Camunda
      String state = "Pending";
      body = gson.fromJson("", Map.class);

      while(state.equals("Running") || state.equals("Pending")) {
        respMap = this.makeRESTCall(BASE_UIPATH_URL+connectorRequest.getOrganizationName()+"/"+connectorRequest.getTenant()+"/orchestrator_/odata/Jobs("+jobId+")","GET", auth, headers, body);

        state = new JSONObject(respMap).getString("State");
        Thread.sleep(connectorRequest.getPollingInterval() * 1000);
      }

      // Get output
      output = new JSONObject(respMap).getString("OutputArguments");
      
    } catch(Exception e) {
      LOGGER.error("Error in UiPath connector "+e);
    }

    // Send output back to Camunda
    var result = new UiPathConnectorResult();
    result.setResult(output);
    return result;
  }

  private Map makeRESTCall(String url, String method, Authentication auth, Map headers, Map body){
    HttpJsonRequest httpJsonRequest = new HttpJsonRequest();
    httpJsonRequest.setUrl(url);
    httpJsonRequest.setMethod(method);
    httpJsonRequest.setAuthentication(auth);
    httpJsonRequest.setHeaders(headers);
    httpJsonRequest.setBody(body);

    Map respMap = new HashMap();

    try {

      HttpJsonResult httpJsonResult = this.executeRequestDirectly(httpJsonRequest);
      respMap = (Map) httpJsonResult.getBody();

    } catch (IOException ioe) {
      LOGGER.error("Error in UiPath API call "+ioe);
    }

    return respMap;
  }
}
