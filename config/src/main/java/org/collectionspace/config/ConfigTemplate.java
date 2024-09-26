package org.collectionspace.config;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;

public class ConfigTemplate {
  private static final List<String> TENANT_SHORT_NAMES = Arrays.asList(
    "core",
    "anthro",
    "bonsai",
    "botgarden",
    "fcart",
    "herbarium",
    "lhmc",
    "materials",
    "publicart"
  );

  public static void main(String[] args) {
    GoogleSheetWriter writer = new GoogleSheetWriter();

    for (String tenantShortName : TENANT_SHORT_NAMES) {
      writer.addTenant(tenantShortName, getRecordTypes(tenantShortName));
    }

    writer.write();
  }

  public static List<RecordType> getRecordTypes(String tenantShortName) {
    Map<String, Object> config = null;

    try {
      config = parseConfig(tenantShortName);
    }
    catch (Exception e) {
      System.err.println("Error reading config: " + e);
    }

    if (config == null) {
      return null;
    }

    Map<String, Object> messageOverridesConfig = (Map<String, Object>) config.get("messages");
    Map<String, Object> recordTypesConfig = (Map<String, Object>) config.get("recordTypes");
    List<RecordType> recordTypes = new ArrayList<>();

    for (Object recordTypeConfig : recordTypesConfig.values()) {
      recordTypes.add(getConfiguredRecordType((Map<String, Object>) recordTypeConfig, messageOverridesConfig));
    }

    recordTypes.sort(new Comparator<RecordType>() {
      @Override
      public int compare(RecordType recordType1, RecordType recordType2) {
        return recordType1.compareTo(recordType2);
      }
    });

    return recordTypes;
  }

  public static RecordType getConfiguredRecordType(
    Map<String, Object> recordTypeConfig,
    Map<String, Object> messageOverridesConfig
  ) {
    String recordTypeId = (String) recordTypeConfig.get("name");
    Map<String, Object> serviceConfig = (Map<String, Object>) recordTypeConfig.get("serviceConfig");
    String serviceType = (String) serviceConfig.get("serviceType");
    RecordType recordType = new RecordType(recordTypeId, serviceType);

    recordType.setName(getRecordTypeName(recordTypeConfig, messageOverridesConfig));
    recordType.setForms(getForms(recordTypeConfig, messageOverridesConfig));
    recordType.setFormComponents(getConfigurableFormComponents(recordTypeConfig, messageOverridesConfig));

    return recordType;
  }

  public static String getRecordTypeName(
    Map<String, Object> recordTypeConfig,
    Map<String, Object> messageOverridesConfig
  ) {
    String nameMessageId = (String) getIn(recordTypeConfig, Arrays.asList("messages", "record", "name", "id"));
    String nameMessage = (String) getIn(recordTypeConfig, Arrays.asList("messages", "record", "name", "defaultMessage"));

    if (messageOverridesConfig != null && messageOverridesConfig.containsKey(nameMessageId)) {
      nameMessage = (String) messageOverridesConfig.get(nameMessageId);
    }

    return nameMessage;
  }

  public static List<Form> getForms(
    Map<String, Object> recordTypeConfig,
    Map<String, Object> messageOverridesConfig
  ) {
    List<Form> forms = new ArrayList<>();
    Map<String, Object> formsConfig = (Map<String, Object>) recordTypeConfig.get("forms");

    if (formsConfig != null) {
      for (Map.Entry<String, Object> formsConfigEntry : formsConfig.entrySet()) {
        String formId = formsConfigEntry.getKey();
        Map<String, Object> formConfig = (Map<String, Object>) formsConfigEntry.getValue();
        Boolean disabled = (Boolean) formConfig.get("disabled");

        if (!BooleanUtils.isTrue(disabled)) {
          Integer sortOrder = (Integer) formConfig.get("sortOrder");
          Form form = new Form(formId);

          form.setName(getFormName(formConfig, messageOverridesConfig));
          form.setSortOrder(sortOrder);

          forms.add(form);
        }
      }
    }

    forms.sort(new Comparator<Form>() {
      @Override
      public int compare(Form form1, Form form2) {
        return ObjectUtils.compare(form1.getSortOrder(), form2.getSortOrder(), true);
      }
    });

    return forms;
  }

  public static String getFormName(
    Map<String, Object> formConfig,
    Map<String, Object> messageOverridesConfig
  ) {
    String nameMessageId = (String) getIn(formConfig, Arrays.asList("messages", "name", "id"));
    String nameMessage = (String) getIn(formConfig, Arrays.asList("messages", "name", "defaultMessage"));

    if (messageOverridesConfig != null && messageOverridesConfig.containsKey(nameMessageId)) {
      nameMessage = (String) messageOverridesConfig.get(nameMessageId);
    }

    return nameMessage;
  }

  public static Map<String, ConfigurableFormComponent> getConfigurableFormComponents(
    Map<String, Object> recordTypeConfig,
    Map<String, Object> messageOverridesConfig
  ) {
    Map<String, ConfigurableFormComponent> components = new LinkedHashMap<>();
    Map<String, Object> formsConfig = (Map<String, Object>) recordTypeConfig.get("forms");
    Map<String, Object> fieldsConfig = (Map<String, Object>) recordTypeConfig.get("fields");
    Map<String, Object> messagesConfig = (Map<String, Object>) recordTypeConfig.get("messages");

    if (formsConfig != null && fieldsConfig != null) {
      List<Form> forms = getForms(recordTypeConfig, messageOverridesConfig);

      for (Form form : forms) {
        String formId = form.getId();
        Map<String, Object> formConfig = (Map<String, Object>) formsConfig.get(formId);
        Map<String, Object> templateConfig = (Map<String, Object>) formConfig.get("template");

        if (templateConfig != null) {
          collectComponents(formId, 0, new ArrayList<String>(), components, fieldsConfig, templateConfig, messagesConfig, messageOverridesConfig);
        }
      }
    }

    return components;
  }

  public static int collectComponents(
    String templateId,
    int templatePos,
    List<String> fieldPath,
    Map<String, ConfigurableFormComponent> components,
    Map<String, Object> fieldsConfig,
    Map<String, Object> formComponentConfig,
    Map<String, Object> messagesConfig,
    Map<String, Object> messageOverridesConfig
  ) {
    String type = (String) formComponentConfig.get("type");
    Map<String, Object> propsConfig = (Map<String, Object>) formComponentConfig.get("props");
    String name = (String) propsConfig.get("name");
    List<String> currentFieldPath = new ArrayList<>(fieldPath);

    ConfigurableFormComponent component = null;

    if (type != null) {
      if (type.equals("Field") && name != null) {
        List<String> subpath = getSubpath(fieldPath, propsConfig, fieldsConfig);

        if (subpath != null) {
          currentFieldPath.addAll(subpath);
        }

        currentFieldPath.add(name);

        String componentId = "Field:" + String.join("/", currentFieldPath);

        component = components.get(componentId);

        if (component == null) {
          component = new FormField(componentId);

          component.setName(getFieldName(currentFieldPath, fieldsConfig, messageOverridesConfig));
          component.setFullName(getFieldFullName(currentFieldPath, fieldsConfig, messageOverridesConfig));
        }

        component.putTemplatePosition(templateId, ++templatePos);
      } else if (type.equals("CompoundInput")) {
        List<String> subpath = getSubpath(fieldPath, propsConfig, fieldsConfig);

        if (subpath != null) {
          currentFieldPath.addAll(subpath);
        }

        if (name != null) {
          currentFieldPath.add(name);
        }

        String componentId = "CompoundInput:" + String.join("/", currentFieldPath);

        component = components.get(componentId);

        if (component == null) {
          component = new FormField(componentId);

          component.setName(getFieldName(currentFieldPath, fieldsConfig, messageOverridesConfig));
          component.setFullName(getFieldFullName(currentFieldPath, fieldsConfig, messageOverridesConfig));
        }

        component.putTemplatePosition(templateId, ++templatePos);
      } else if (type.equals("Panel") && name != null) {
        String componentId = "Panel:" + name;

        component = components.get(componentId);

        if (component == null) {
          component = new FormPanel(componentId);

          component.setName(getPanelName(name, messagesConfig, messageOverridesConfig));
        }

        component.putTemplatePosition(templateId, ++templatePos);
      } else if (type.equals("InputTable") && name != null) {
        String componentId = "InputTable:" + name;

        component = components.get(componentId);

        if (component == null) {
          component = new FormInputTable(componentId);

          component.setName(getInputTableName(name, messagesConfig, messageOverridesConfig));
        }

        component.putTemplatePosition(templateId, ++templatePos);
      }
    }

    if (component != null) {
      // System.out.println(component.getId() + ": " + component.getName() + "|" + component.getFullName() + " " + component.getTemplatePositions().toString());
      components.put(component.getId(), component);
    }

    Object childrenConfig = propsConfig.get("children");

    if (childrenConfig != null) {
      if (childrenConfig instanceof List) {
        for (Object childrenConfigItem : (List<Object>) childrenConfig) {
          Map<String, Object> childConfig = (Map<String, Object>) childrenConfigItem;

          templatePos = collectComponents(templateId, templatePos, currentFieldPath, components, fieldsConfig, childConfig, messagesConfig, messageOverridesConfig);
        }
      } else if (childrenConfig instanceof Map) {
        Map<String, Object> childConfig = (Map<String, Object>) childrenConfig;

        templatePos = collectComponents(templateId, templatePos, currentFieldPath, components, fieldsConfig, childConfig, messagesConfig, messageOverridesConfig);
      }
    }

    return templatePos;
  }

  public static List<String> getSubpath (
    List<String> parentFieldPath,
    Map<String, Object> formComponentPropsConfig,
    Map<String, Object> fieldsConfig
  ) {
    List<String> result = new ArrayList<>();
    Object subpath = formComponentPropsConfig.get("subpath");

    if (subpath == null) {
      Map<String, Object> fieldConfig = (Map<String, Object>) getIn(fieldsConfig, parentFieldPath);

      if (fieldConfig != null) {
        subpath = getIn(fieldConfig, Arrays.asList("[config]", "view", "props", "defaultChildSubpath"));
      }
    }

    if (subpath != null) {
      if (subpath instanceof List) {
        for (Object pathItem : (List<Object>) subpath) {
          if (pathItem instanceof String) {
            result.add((String) pathItem);
          } else if (pathItem instanceof Integer) {
            result.add(Integer.toString((Integer) pathItem));
          }
        }
      } else if (subpath instanceof String) {
        result.add((String) subpath);
      }
    }

    return result;
  }

  public static String getFieldName(
    List<String> fieldPath,
    Map<String, Object> fieldsConfig,
    Map<String, Object> messageOverridesConfig
  ) {
    Map<String, Object> fieldConfig = (Map<String, Object>) getIn(fieldsConfig, fieldPath);

    if (fieldConfig == null) {
      return null;
    }

    String nameMessageId = (String) getIn(fieldConfig, Arrays.asList("[config]", "messages", "name", "id"));
    String nameMessage = (String) getIn(fieldConfig, Arrays.asList("[config]", "messages", "name", "defaultMessage"));

    if (messageOverridesConfig != null && messageOverridesConfig.containsKey(nameMessageId)) {
      nameMessage = (String) messageOverridesConfig.get(nameMessageId);
    }

    return nameMessage;
  }

  public static String getFieldFullName(
    List<String> fieldPath,
    Map<String, Object> fieldsConfig,
    Map<String, Object> messageOverridesConfig
  ) {
    Map<String, Object> fieldConfig = (Map<String, Object>) getIn(fieldsConfig, fieldPath);

    if (fieldConfig == null) {
      return null;
    }

    String fullNameMessageId = (String) getIn(fieldConfig, Arrays.asList("[config]", "messages", "fullName", "id"));
    String fullNameMessage = (String) getIn(fieldConfig, Arrays.asList("[config]", "messages", "fullName", "defaultMessage"));

    if (messageOverridesConfig != null && messageOverridesConfig.containsKey(fullNameMessageId)) {
      fullNameMessage = (String) messageOverridesConfig.get(fullNameMessageId);
    }

    return fullNameMessage;
  }

  public static String getPanelName(
    String panelId,
    Map<String, Object> messagesConfig,
    Map<String, Object> messageOverridesConfig
  ) {
    String nameMessageId = (String) getIn(messagesConfig, Arrays.asList("panel", panelId, "id"));
    String nameMessage = (String) getIn(messagesConfig, Arrays.asList("panel", panelId, "defaultMessage"));

    if (messageOverridesConfig != null && messageOverridesConfig.containsKey(nameMessageId)) {
      nameMessage = (String) messageOverridesConfig.get(nameMessageId);
    }

    return nameMessage;
  }

  public static String getInputTableName(
    String inputTableId,
    Map<String, Object> messagesConfig,
    Map<String, Object> messageOverridesConfig
  ) {
    String nameMessageId = (String) getIn(messagesConfig, Arrays.asList("inputTable", inputTableId, "id"));
    String nameMessage = (String) getIn(messagesConfig, Arrays.asList("inputTable", inputTableId, "defaultMessage"));

    if (messageOverridesConfig != null && messageOverridesConfig.containsKey(nameMessageId)) {
      nameMessage = (String) messageOverridesConfig.get(nameMessageId);
    }

    return nameMessage;
  }

  public static Object getIn(Map<String, Object> map, List<String> path) {
    if (map == null || path == null || path.size() == 0) {
      return null;
    }

    String key = path.get(0);
    Object value = map.get(key);

    if (path.size() > 1) {
      if (value == null || !(value instanceof Map)) {
        return null;
      }

      return getIn((Map<String, Object>) value, path.subList(1, path.size()));
    }

    return value;
  }

  public static Map<String, Object> parseConfig(String tenantShortName) throws JsonProcessingException, IOException {
    InputStream configInputStream = ConfigTemplate.class.getClassLoader().getResourceAsStream(tenantShortName + ".json");
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> map = mapper.readValue(configInputStream, new TypeReference<Map<String, Object>>() {});

    return map;
  }
}
