package org.collectionspace.config;

import java.util.HashMap;
import java.util.Map;

public abstract class ConfigurableFormComponent {
  private String type;
  private String id;
  private String name;
  private String fullName;
  private Map<String, Integer> templatePositions = new HashMap<>();

  public ConfigurableFormComponent(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getFullName() {
    return ((fullName == null) ? name : fullName);
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public Map<String, Integer> getTemplatePositions() {
    return templatePositions;
  }

  public void putTemplatePosition(String templateId, Integer position) {
    this.templatePositions.put(templateId, position);
  }

  public abstract String getType();
}
