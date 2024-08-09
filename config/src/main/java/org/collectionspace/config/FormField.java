package org.collectionspace.config;

public class FormField extends ConfigurableFormComponent {
  public FormField(String id) {
    super(id);
  }

  @Override
  public String getType() {
    return "Field";
  }
}
