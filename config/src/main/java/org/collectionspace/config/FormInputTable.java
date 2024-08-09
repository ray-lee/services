package org.collectionspace.config;

public class FormInputTable extends ConfigurableFormComponent {
  public FormInputTable(String id) {
    super(id);
  }

  @Override
  public String getType() {
    return "InputTable";
  }
}
