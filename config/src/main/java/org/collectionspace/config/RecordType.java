package org.collectionspace.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RecordType {
  private String id;
  private String name;
  private String serviceType;
  private List<Form> forms;
  private Map<String, ConfigurableFormComponent> formComponents;

  private static int serviceTypeOrder(String serviceType) {
    switch(serviceType) {
      case "object": return 1;
      case "procedure": return 2;
      case "authority": return 3;
      default: return 4;
    }
  }

  public RecordType(String id, String serviceType) {
    this.id = id;
    this.serviceType = serviceType;
  }

  public String getId() {
    return this.id;
  }

  public String getName() {
    return this.name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getServiceType() {
    return this.serviceType;
  }

  public void setForms(List<Form> forms) {
    this.forms = forms;
  }

  public List<Form> getForms() {
    return this.forms;
  }

  public void setFormComponents(Map<String, ConfigurableFormComponent> formComponents) {
    this.formComponents = formComponents;
  }

  public Map<String, ConfigurableFormComponent> getFormComponents() {
    return this.formComponents;
  }

  public int compareTo(RecordType recordType) {
    if (!this.getServiceType().equals(recordType.getServiceType())) {
      return Integer.compare(serviceTypeOrder(this.getServiceType()), serviceTypeOrder(recordType.getServiceType()));
    }

    return this.getName().compareTo(recordType.getName());
  }
}
