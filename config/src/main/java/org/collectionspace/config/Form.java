package org.collectionspace.config;

public class Form {
  private String id;
  private String name;
  private Integer sortOrder;

  public Form(String id) {
    this.id = id;
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

  public Integer getSortOrder() {
    return this.sortOrder;
  }

  public void setSortOrder(Integer sortOrder) {
    this.sortOrder = sortOrder;
  }
}
