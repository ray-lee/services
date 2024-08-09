package org.collectionspace.config;

public class FormPanel extends ConfigurableFormComponent {
  public FormPanel(String id) {
    super(id);
  }

  @Override
  public String getType() {
    return "Panel";
  }
}
