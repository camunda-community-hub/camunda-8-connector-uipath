package io.camunda.connector;

import org.json.JSONObject;
import javax.validation.constraints.NotEmpty;


public class UiPathConnectorRequest {

  @NotEmpty
  private String packageName;
  private JSONObject robotInput;
  private String robotOutput;
  private String clientId;
  private String clientKey;
  private String organizationName;
  private String organizationId;
  private String tenant;
  private Integer pollingInterval;

  public static final String PACKAGE_NAME = "packageName";
  public static final String ROBOT_INPUT = "robotInput";
  public static final String ROBOT_OUTPUT = "robotOutput";
  public static final String CLIENT_ID = "clientId";
  public static final String CLIENT_KEY = "clientKey";
  public static final String ORGANIZATION_NAME = "organizationName";
  public static final String ORGANIZATION_ID = "organizationId";
  public static final String TENANT = "tenant";
  public static final String POLLING_INTERVAL = "pollingInterval";

  public UiPathConnectorRequest() {
  }

  public String getPackageName() {
    return packageName;
  }

  public void setPackageName(String packageName) {
    this.packageName = packageName;
  }

  public JSONObject getRobotInput() {
    return robotInput;
  }

  public void setRobotInput(JSONObject robotInput) {
    this.robotInput = robotInput;
  }

  public String getRobotOutput() {
        return robotOutput;
    }

  public void setRobotOutput(String robotOutput) {
        this.robotOutput = robotOutput;
    }

  public String getClientId() {
      return clientId;
    }

  public void setClientId(String clientId) {
      this.clientId = clientId;
    }

  public String getClientKey() {
    return clientKey;
  }

  public void setClientKey(String clientKey) {
    this.clientKey = clientKey;
  }

  public String getOrganizationName() { return organizationName; }

  public void setOrganizationName(String organizationName) {
    this.organizationName = organizationName;
  }

  public String getOrganizationId() { return organizationId; }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getTenant() { return tenant; }

  public void setTenant(String tenant) {
    this.tenant = tenant;
  }

  public Integer getPollingInterval() {
    return pollingInterval;
  }

  public void setPollingInterval(String pollingInterval) {
    this.pollingInterval = Integer.parseInt(pollingInterval);
  }
}
