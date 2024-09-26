package org.collectionspace.config;

import java.io.FileInputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.sheets.v4.model.AddProtectedRangeRequest;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.AddSheetResponse;
import com.google.api.services.sheets.v4.model.AppendCellsRequest;
import com.google.api.services.sheets.v4.model.AutoResizeDimensionsRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.CellFormat;
import com.google.api.services.sheets.v4.model.DimensionProperties;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.Editors;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.GridProperties;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.ProtectedRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.TextFormat;
import com.google.api.services.sheets.v4.model.UpdateDimensionPropertiesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import org.apache.commons.lang3.StringUtils;

public class GoogleSheetWriter {
  private static final String VERSION = "8.1";
  private static final String CREDENTIAL_FILE = "/home/collectionspace/ray-dev-9b15b19210bc.json";
  private static final String CONFIG_FOLDER_ID = "1YLNRN2QQiYVqvlhutmGognpcHUl50evC";
  private static final String COMBINED_SPREADSHEET_NAME = "all profiles";
  private static final int QUOTA_TIME_SECS = 60;
  private static final int QUOTA_WRITE_REQUEST_LIMIT = 50;
  // private static final String SPREADSHEET_ID = "1fax09p6PuQORdOEv1BkBwxGQHFaaA8cv4Mql88Jg18c";

  private static final CellFormat DEFAULT_CELL_FORMAT = new CellFormat()
    .setWrapStrategy("CLIP")
    .setVerticalAlignment("TOP");

  private static final CellFormat HEADER_CELL_FORMAT = DEFAULT_CELL_FORMAT.clone()
    .setWrapStrategy("WRAP")
    .setHorizontalAlignment("CENTER")
    .setVerticalAlignment("MIDDLE")
    .setTextFormat(
      new TextFormat().setBold(true)
    );

  private static final String COL_TITLE_PROFILE = "Profile";
  private static final String COL_TITLE_RECORD_TYPE_NAME = "Record Type Name";
  private static final String COL_TITLE_COMPONENT_ID = "Component ID";
  private static final String COL_TITLE_COMPONENT_NAME = "Component Name";
  private static final String COL_TITLE_COMPONENT_FULL_NAME = "Component Full Name";
  private static final String COL_TITLE_PROFILE_AND_COMPONENT_ID = "Profile:ID";
  private static final String COL_TITLE_COMPONENT_HELP_TEXT = "Component Help Text";
  private static final String COL_TITLE_COMPONENT_EFFECTIVE_HELP_TEXT = "Effective Component Help Text";

  private Drive driveService;
  private Sheets sheetsService;
  private Map<String, Map<String, Integer>> componentRowNumbersByRecordType;
  private Map<String, List<RecordType>> recordTypesByTenantShortName = new LinkedHashMap<>();
  private Map<String, Map<String, Integer>> columnIndexesByRecordType = new HashMap<>();
  private int requestSincePauseCount = 0;
  private String accountId;

  public GoogleSheetWriter() {
    this.driveService = buildDriveService();
    this.sheetsService = buildSheetsService();
  }

  public void addTenant(String tenantShortName, List<RecordType> recordTypes) {
    recordTypesByTenantShortName.put(tenantShortName, recordTypes);
  }

  public void write() {
    if (sheetsService == null) {
      return;
    }

    String versionFolderId = findOrCreateFolder(CONFIG_FOLDER_ID, VERSION);
    String spreadsheetId = findOrCreateSpreadsheet(versionFolderId, COMBINED_SPREADSHEET_NAME);

    writeSpreadsheet(spreadsheetId);
  }

  public void writeSpreadsheet(String spreadsheetId) {
    Set<String> recordTypeIds = collectRecordTypes();

    for (String recordTypeId : recordTypeIds) {
      writeRecordType(spreadsheetId, recordTypeId);
    }
  }

  private Set<String> collectRecordTypes() {
    Set<String> result = new LinkedHashSet<>();

    for (String tenantShortName : recordTypesByTenantShortName.keySet()) {
      List<RecordType> recordTypes = recordTypesByTenantShortName.get(tenantShortName);

      for (RecordType recordType : recordTypes) {
        if (shouldIncludeRecordType(recordType)) {
          result.add(recordType.getId());
        }
      }
    }

    return result;
  }

  private Map<String, Sheet> getSheetsByRecordType(String spreadsheetId) {
    Map<String, Sheet> sheetsByRecordType = new LinkedHashMap<>();
    Spreadsheet sp = null;

    try {
      sp = sheetsService.spreadsheets().get(spreadsheetId).execute();
    } catch (Exception e) {
      System.err.println(e);
    }

    if (sp != null) {
      List<Sheet> sheets = sp.getSheets();

      for (Sheet sheet : sheets) {
        String recordTypeId = getSheetRecordTypeId(sheet);

        sheetsByRecordType.put(recordTypeId, sheet);
      }
    }

    return sheetsByRecordType;
  }

  private String getSheetRecordTypeId(Sheet sheet) {
    String title = sheet.getProperties().getTitle();
    Pattern pattern = Pattern.compile("^.*? \\((.*?)\\)$");
    Matcher matcher = pattern.matcher(title);

    if (matcher.find()) {
      return matcher.group(1);
    }

    return title;
  }

  public void writeRecordType(String spreadsheetId, String recordTypeId) {
    System.out.println("Writing record type " + recordTypeId);

    if (sheetsService == null) {
      return;
    }

    Integer sheetId = findSheet(spreadsheetId, recordTypeId);

    if (sheetId != null) {
      // TODO: Update an existing sheet

      System.out.println("Sheet for record type " + recordTypeId + " already exists, skipping");

      return;
    }

    if (sheetId == null) {
      sheetId = createSheet(spreadsheetId, recordTypeId);

      if (sheetId != null) {
        writeSheetHeaders(spreadsheetId, sheetId, recordTypeId);
      }
    }

    if (sheetId != null) {
      List<Request> requests = new ArrayList<>();

      for (String tenantShortName : recordTypesByTenantShortName.keySet()) {
        List<RecordType> recordTypes = recordTypesByTenantShortName.get(tenantShortName);
        RecordType recordType = null;

        for (RecordType candidateRecordType : recordTypes) {
          if (candidateRecordType.getId().equals(recordTypeId)) {
            recordType = candidateRecordType;

            break;
          }
        }

        if (recordType != null) {
          requests.addAll(getRecordTypeUpdateRequests(sheetId, tenantShortName, recordType));
        }
      }

      int nameColumnIndex = getColumnIndex(recordTypeId, COL_TITLE_COMPONENT_NAME);
      int helpTextColumnIndex = getColumnIndex(recordTypeId, COL_TITLE_COMPONENT_HELP_TEXT);
      int profileAndIdColumnIndex = getColumnIndex(recordTypeId, COL_TITLE_PROFILE_AND_COMPONENT_ID);

      requests.add(
        new Request().setAutoResizeDimensions(
          new AutoResizeDimensionsRequest().setDimensions(
            new DimensionRange()
              .setSheetId(sheetId)
              .setDimension("COLUMNS")
              .setStartIndex(nameColumnIndex)
              .setEndIndex(nameColumnIndex + 2)
          )
        )
      );

      requests.add(
        new Request().setUpdateDimensionProperties(
          new UpdateDimensionPropertiesRequest()
            .setRange(
              new DimensionRange()
                .setSheetId(sheetId)
                .setDimension("COLUMNS")
                .setStartIndex(helpTextColumnIndex)
                .setEndIndex(helpTextColumnIndex + 2)
            )
            .setFields("*")
            .setProperties(
              new DimensionProperties()
                .setPixelSize(300)
            )
        )
      );

      requests.add(
        new Request().setUpdateDimensionProperties(
          new UpdateDimensionPropertiesRequest()
            .setRange(
              new DimensionRange()
                .setSheetId(sheetId)
                .setDimension("COLUMNS")
                .setStartIndex(profileAndIdColumnIndex)
                .setEndIndex(profileAndIdColumnIndex + 1)
            )
            .setFields("*")
            .setProperties(
              new DimensionProperties()
                .setHiddenByUser(true)
            )
        )
      );

      requests.add(
        new Request().setAddProtectedRange(
          new AddProtectedRangeRequest()
            .setProtectedRange(
              new ProtectedRange()
                .setRange(
                  new GridRange()
                    .setSheetId(sheetId)
                )
                .setUnprotectedRanges(
                  Arrays.asList(
                    new GridRange()
                      .setSheetId(sheetId)
                      .setStartColumnIndex(helpTextColumnIndex)
                      .setEndColumnIndex(helpTextColumnIndex + 1)
                      .setStartRowIndex(0)
                  )
                )
                .setEditors(
                  new Editors()
                    .setUsers(Arrays.asList(accountId))
                )
                .setDescription("These cells are informational only. Do not edit.")
                .setRequestingUserCanEdit(true)
            )
        )
      );

      batchUpdateSpreadsheet(spreadsheetId, requests);
    }
  }

  private List<Request> getRecordTypeUpdateRequests(Integer sheetId, String tenantShortName, RecordType recordType) {
    List<Request> requests = new ArrayList<>();
    String profileId = tenantShortName.equals("core") ? "_" + tenantShortName : tenantShortName;
    String recordTypeId = recordType.getId();

    System.out.println("Generating update requests for record type " + recordTypeId + " in tenant " + tenantShortName);

    List<RowData> newRows = new ArrayList<RowData>();

    for (ConfigurableFormComponent formComponent : recordType.getFormComponents().values()) {
      Map<String, Integer> templatePositions = formComponent.getTemplatePositions();
      List<CellData> rowCells = initializeRow(recordTypeId);

      rowCells.set(
        getColumnIndex(recordTypeId, COL_TITLE_PROFILE),
        new CellData()
          .setUserEnteredFormat(DEFAULT_CELL_FORMAT)
          .setUserEnteredValue(
            new ExtendedValue().setStringValue(profileId)
          )
      );

      rowCells.set(
        getColumnIndex(recordTypeId, COL_TITLE_RECORD_TYPE_NAME),
        new CellData()
          .setUserEnteredFormat(DEFAULT_CELL_FORMAT)
          .setUserEnteredValue(
            new ExtendedValue().setStringValue(recordType.getName())
          )
      );

      rowCells.set(
        getColumnIndex(recordTypeId, COL_TITLE_COMPONENT_ID),
        new CellData()
          .setUserEnteredFormat(
            DEFAULT_CELL_FORMAT.clone()
              .setHorizontalAlignment("RIGHT")
          )
          .setUserEnteredValue(
            new ExtendedValue().setStringValue(formComponent.getId())
          )
      );

      rowCells.set(
        getColumnIndex(recordTypeId, COL_TITLE_COMPONENT_NAME),
        new CellData()
          .setUserEnteredFormat(DEFAULT_CELL_FORMAT)
          .setUserEnteredValue(
            new ExtendedValue().setStringValue(formComponent.getName())
          )
      );

      rowCells.set(
        getColumnIndex(recordTypeId, COL_TITLE_COMPONENT_FULL_NAME),
        new CellData()
          .setUserEnteredFormat(DEFAULT_CELL_FORMAT)
          .setUserEnteredValue(
            new ExtendedValue().setStringValue(formComponent.getFullName())
          )
      );

      rowCells.set(
        getColumnIndex(recordTypeId, COL_TITLE_PROFILE_AND_COMPONENT_ID),
        new CellData()
          .setUserEnteredFormat(
            DEFAULT_CELL_FORMAT.clone()
              .setHorizontalAlignment("RIGHT")
          )
          .setUserEnteredValue(
            new ExtendedValue().setStringValue(profileId + ":" + formComponent.getId())
          )
      );

      rowCells.set(
        getColumnIndex(recordTypeId, COL_TITLE_COMPONENT_HELP_TEXT),
        new CellData()
          .setUserEnteredFormat(
            DEFAULT_CELL_FORMAT.clone()
              .setWrapStrategy("WRAP")
          )
      );

      String componentIdLetter = getColumnLetter(recordTypeId, COL_TITLE_COMPONENT_ID);
      String profileAndIdLetter = getColumnLetter(recordTypeId, COL_TITLE_PROFILE_AND_COMPONENT_ID);
      String helpTextLetter = getColumnLetter(recordTypeId, COL_TITLE_COMPONENT_HELP_TEXT);

      rowCells.set(
        getColumnIndex(recordTypeId, COL_TITLE_COMPONENT_EFFECTIVE_HELP_TEXT),
        new CellData()
          .setUserEnteredFormat(
            DEFAULT_CELL_FORMAT.clone()
              .setWrapStrategy("WRAP")
          )
          .setUserEnteredValue(
            new ExtendedValue().setFormulaValue(String.format(
              "=IF(ISBLANK(%s:%s), VLOOKUP(\"_core:\" & %s:%s, %s:%s, 2, FALSE), %s:%s)",
              helpTextLetter, helpTextLetter,
              componentIdLetter, componentIdLetter,
              profileAndIdLetter, helpTextLetter,
              helpTextLetter, helpTextLetter
            ))
          )
      );

      for (Form form : recordType.getForms()) {
        Integer position = templatePositions.get(form.getId());
        Integer number = (position == null) ?  null : Integer.valueOf(position);
        String linkTarget = null;

        if (number != null) {
          try {
            linkTarget = String.format("https://%s.dev.collectionspace.org/cspace/%s/record/%s?template=%s&focus=%s",
              tenantShortName, tenantShortName, recordTypeId, form.getId(), URLEncoder.encode(formComponent.getId(), "UTF-8"));
          } catch (Exception e) {
            linkTarget = null;
          }
        }

        rowCells.set(
          getColumnIndex(recordTypeId, getFormPositionHeader(form)),
          new CellData()
            .setUserEnteredFormat(DEFAULT_CELL_FORMAT)
            .setUserEnteredValue(
              number == null
                ? null
                : new ExtendedValue().setFormulaValue(String.format("=HYPERLINK(\"%s\", %d)", linkTarget, number))
            )
        );
      }

      newRows.add(new RowData().setValues(rowCells));
    }

    if (newRows.size() > 0) {
      requests.add(
        new Request().setAppendCells(
          new AppendCellsRequest()
            .setSheetId(sheetId)
            .setFields("*")
            .setRows(newRows)
        )
      );
    }

    return requests;
  }

  public Set<String> getUniqueFormPositionColumnHeaders(String recordTypeId) {
    Set<String> result = new LinkedHashSet<>();

    for (List<RecordType> recordTypes : recordTypesByTenantShortName.values()) {
      for (RecordType recordType : recordTypes) {
        if (shouldIncludeRecordType(recordType) && recordType.getId().equals(recordTypeId)) {
          for (Form form : recordType.getForms()) {
            result.add(getFormPositionHeader(form));
          }
        }
      }
    }

    return result;
  }

  public String getFormPositionHeader(Form form) {
    return form.getName() + " Position (" + form.getId() + ")";
  }

  public Integer findOrCreateSheet(String spreadsheetId, String title, SheetCreatedHandler sheetCreatedHandler) {
    if (sheetsService == null) {
      return null;
    }

    Integer sheetId = findSheet(spreadsheetId, title);

    if (sheetId != null) {
      return sheetId;
    }

    sheetId = createSheet(spreadsheetId, title);

    if (sheetId != null && sheetCreatedHandler != null) {
      sheetCreatedHandler.onSheetCreated(spreadsheetId, sheetId);
    }

    return sheetId;
  }

  public Integer findSheet(String spreadsheetId, String title) {
    if (sheetsService == null) {
      return null;
    }

    Spreadsheet sp = null;

    try {
      sp = sheetsService.spreadsheets().get(spreadsheetId).execute();
    } catch (Exception e) {
      System.err.println(e);
    }

    if (sp != null) {
      List<Sheet> sheets = sp.getSheets();

      for (Sheet sheet : sheets) {
        if (sheet.getProperties().getTitle().equals(title)) {
          return sheet.getProperties().getSheetId();
        }
      }
    }

    return null;
  }

  public Integer createSheet(String spreadsheetId, String title) {
    if (sheetsService == null) {
      return null;
    }

    List<Request> requests = new ArrayList<>();

    requests.add(
      new Request().setAddSheet(
        new AddSheetRequest().setProperties(
          new SheetProperties()
            .setTitle(title)
            .setGridProperties(
              new GridProperties().setFrozenRowCount(1)
            )
        )
      )
    );

    BatchUpdateSpreadsheetResponse batchUpdateResponse = batchUpdateSpreadsheet(spreadsheetId, requests);

    if (batchUpdateResponse == null) {
      return null;
    }

    AddSheetResponse addSheetResponse = batchUpdateResponse.getReplies().get(0).getAddSheet();

    return addSheetResponse.getProperties().getSheetId();
  }

  public void writeSheetHeaders(String spreadsheetId, Integer sheetId, String recordTypeId) {
    if (sheetsService == null) {
      return;
    }

    List<Request> requests = new ArrayList<>();
    List<RowData> newRows = new ArrayList<RowData>();
    List<CellData> headerCells = new ArrayList<>();

    headerCells.addAll(Arrays.asList(
      new CellData()
        .setUserEnteredFormat(HEADER_CELL_FORMAT)
        .setUserEnteredValue(
          new ExtendedValue().setStringValue(COL_TITLE_PROFILE)
        ),
      new CellData()
        .setUserEnteredFormat(HEADER_CELL_FORMAT)
        .setUserEnteredValue(
          new ExtendedValue().setStringValue(COL_TITLE_RECORD_TYPE_NAME)
        ),
      new CellData()
        .setUserEnteredFormat(HEADER_CELL_FORMAT)
        .setUserEnteredValue(
          new ExtendedValue().setStringValue(COL_TITLE_COMPONENT_ID)
        ),
      new CellData()
        .setUserEnteredFormat(HEADER_CELL_FORMAT)
        .setUserEnteredValue(
          new ExtendedValue().setStringValue(COL_TITLE_COMPONENT_NAME)
        ),
      new CellData()
        .setUserEnteredFormat(HEADER_CELL_FORMAT)
        .setUserEnteredValue(
          new ExtendedValue().setStringValue(COL_TITLE_COMPONENT_FULL_NAME)
        ),
      new CellData()
        .setUserEnteredFormat(HEADER_CELL_FORMAT)
        .setUserEnteredValue(
          new ExtendedValue().setStringValue(COL_TITLE_PROFILE_AND_COMPONENT_ID)
        ),
      new CellData()
        .setUserEnteredFormat(HEADER_CELL_FORMAT)
        .setUserEnteredValue(
          new ExtendedValue().setStringValue(COL_TITLE_COMPONENT_HELP_TEXT)
        ),
      new CellData()
        .setUserEnteredFormat(HEADER_CELL_FORMAT)
        .setUserEnteredValue(
          new ExtendedValue().setStringValue(COL_TITLE_COMPONENT_EFFECTIVE_HELP_TEXT)
        )
    ));

    for (String headerText : getUniqueFormPositionColumnHeaders(recordTypeId)) {
      headerCells.add(
        new CellData()
          .setUserEnteredFormat(HEADER_CELL_FORMAT)
          .setUserEnteredValue(
            new ExtendedValue().setStringValue(headerText)
          )
      );
    }

    saveColumnIndexes(recordTypeId, headerCells);

    newRows.add(new RowData().setValues(headerCells));

    requests.add(
      new Request().setAppendCells(
        new AppendCellsRequest()
          .setSheetId(sheetId)
          .setFields("*")
          .setRows(newRows)
      )
    );

    batchUpdateSpreadsheet(spreadsheetId, requests);
  }

  public void saveColumnIndexes(String recordTypeId, List<CellData> headerCells) {
    Map<String, Integer> columnIndexesByName = columnIndexesByRecordType.get(recordTypeId);

    if (columnIndexesByName == null) {
      columnIndexesByName = new HashMap<String, Integer>();

      columnIndexesByRecordType.put(recordTypeId, columnIndexesByName);
    }

    for (int i = 0; i < headerCells.size(); i++) {
      columnIndexesByName.put(headerCells.get(i).getUserEnteredValue().getStringValue(), i);
    }
  }

  public int getColumnIndex(String recordTypeId, String columnName) {
    Map<String, Integer> columnIndexesByName = columnIndexesByRecordType.get(recordTypeId);

    if (columnIndexesByName != null) {
      return columnIndexesByName.get(columnName);
    }

    return -1;
  }

  public String getColumnLetter(String recordTypeId, String columnName) {
    int index = getColumnIndex(recordTypeId, columnName);

    if (index >= 0) {
      return String.valueOf((char) ('A' + index));
    }

    return null;
  }

  public List<CellData> initializeRow(String recordTypeId) {
    Map<String, Integer> columnIndexesByName = columnIndexesByRecordType.get(recordTypeId);
    List<CellData> rowCells = new ArrayList<>();

    if (columnIndexesByName != null) {
      for (int i = 0; i < columnIndexesByName.size(); i++) {
        rowCells.add(new CellData());
      }
    }

    return rowCells;
  }

  public boolean shouldIncludeRecordType(RecordType recordType) {
    String serviceType = recordType.getServiceType();

    return (
      serviceType.equals("object")
      || serviceType.equals("procedure")
      || serviceType.equals("authority")
    );
  }

  // public void readSheetForRecordType(String spreadsheetId, Sheet sheet, RecordType recordType) {
  //   if (sheetsService == null) {
  //     return;
  //   }

  //   String sheetTitle = sheet.getProperties().getTitle();
  //   String headerRowRange = "'" + sheetTitle + "'!1:1";
  //   ValueRange headerRowResult = null;

  //   try {
  //     headerRowResult = sheetsService.spreadsheets().values().get(spreadsheetId, headerRowRange).execute();
  //   } catch (Exception e) {
  //     System.err.println(e);
  //   }

  //   if (headerRowResult == null) {
  //     return;
  //   }

  //   List<List<Object>> values = headerRowResult.getValues();
  //   List<Object> headerRow = (values == null) ? new ArrayList<Object>() : values.get(0);
  //   int idColumnIndex = (headerRow == null) ? -1 : headerRow.indexOf("ID");

  //   if (idColumnIndex < 0) {
  //     return;
  //   }

  //   char idColumnChar = (char) (65 + idColumnIndex);
  //   String idColumnRange = "'" + sheetTitle + "'!" + idColumnChar + ":" + idColumnChar;
  //   ValueRange idColumnResult = null;

  //   try {
  //     idColumnResult = sheetsService.spreadsheets().values().get(spreadsheetId, idColumnRange).execute();
  //   } catch (Exception e) {
  //     System.err.println(e);
  //   }

  //   if (idColumnResult == null) {
  //     return;
  //   }

  //   Map<String, Integer> componentRowNumbers = componentRowNumbersByRecordType.get(recordType.getId());

  //   if (componentRowNumbers == null) {
  //     componentRowNumbers = new HashMap<String, Integer>();

  //     componentRowNumbersByRecordType.put(recordType.getId(), componentRowNumbers);
  //   }

  //   List<List<Object>> rows = idColumnResult.getValues();

  //   for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
  //     List<Object> row = rows.get(rowIndex);
  //     String componentId = (String) row.get(0);

  //     componentRowNumbers.put(componentId, rowIndex + 1);
  //   }
  // }

  // public void read() {
  //   if (sheetsService == null) {
  //     return;
  //   }

  //   String range = "Group";

  //   ValueRange result = null;

  //   try {
  //     result = sheetsService.spreadsheets().values().get(SPREADSHEET_ID, range).execute();
  //     List<List<Object>> values = result.getValues();

  //     // int numRows = result.getValues() != null ? result.getValues().size() : 0;
  //     // System.out.printf("%d rows retrieved.\n", numRows);

  //     for (List<Object> row : values) {
  //       for (Object cell : row) {
  //         System.out.println(cell.toString());
  //       }
  //     }
  //   } catch (GoogleJsonResponseException e) {
  //     GoogleJsonError error = e.getDetails();

  //     if (error.getCode() == 404) {
  //       System.err.printf("Spreadsheet not found with id '%s'.\n", SPREADSHEET_ID);
  //     } else {
  //       System.err.println(e);
  //     }
  //   } catch (Exception e) {
  //     System.err.println(e);
  //   }
  // }

  public Sheets buildSheetsService() {
    GoogleCredential credential = null;

    try {
      credential = GoogleCredential
        .fromStream(new FileInputStream(CREDENTIAL_FILE))
        .createScoped(Arrays.asList(SheetsScopes.SPREADSHEETS));
    } catch (Exception e) {
      System.err.println(e);
    }

    if (credential == null) {
      return null;
    }

    accountId = credential.getServiceAccountId();

    System.out.println("Using account ID: " + accountId);

    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    NetHttpTransport httpTransport = null;

    try {
      httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    } catch (Exception e) {
      System.err.println(e);
    }

    if (httpTransport == null) {
      return null;
    }

    Sheets sheetsService = new Sheets.Builder(httpTransport, jsonFactory, credential)
      .setApplicationName("CSpaceConfig")
      .build();

    return sheetsService;
  }

  public String findOrCreateSpreadsheet(String parentId, String name) {
    return findOrCreateFile(parentId, name, "application/vnd.google-apps.spreadsheet");
  }

  public String findOrCreateFolder(String parentId, String name) {
    return findOrCreateFile(parentId, name, "application/vnd.google-apps.folder");
  }

  public String findOrCreateFile(String parentId, String name, String mimeType) {
    if (driveService == null || parentId == null || name == null || mimeType == null) {
      return null;
    }

    FileList result = null;

    try {
      result = driveService.files().list()
        .setQ("name='" + name + "' and mimeType = '" + mimeType + "' and '" + parentId + "' in parents")
        .setSpaces("drive")
        .setFields("files(id)")
        .setPageSize(1)
        .execute();
    } catch (Exception e) {
      System.err.println(e);
    }

    if (result != null) {
      List<File> files = result.getFiles();

      if (files.size() > 0) {
        File file = files.get(0);
        String id = file.getId();

        System.out.printf("Found file with name %s in %s: %s\n", name, parentId, id);

        return id;
      }
    }

    File fileMetadata = new File();

    fileMetadata.setName(name);
    fileMetadata.setMimeType(mimeType);
    fileMetadata.setParents(Arrays.asList(parentId));

    File createdFile = null;

    try {
      createdFile = driveService.files().create(fileMetadata)
        .setFields("id")
        .execute();
    } catch (Exception e) {
      System.err.println(e);
    }

    if (createdFile != null) {
      String id = createdFile.getId();

      System.out.printf("Created file with name %s in %s: %s\n", name, parentId, id);

      return id;
    }

    return null;
  }

  public Drive buildDriveService() {
    GoogleCredential credential = null;

    try {
      credential = GoogleCredential
        .fromStream(new FileInputStream(CREDENTIAL_FILE))
        .createScoped(Arrays.asList(DriveScopes.DRIVE));
    } catch (Exception e) {
      System.err.println(e);
    }

    if (credential == null) {
      return null;
    }

    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    NetHttpTransport httpTransport = null;

    try {
      httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    } catch (Exception e) {
      System.err.println(e);
    }

    if (httpTransport == null) {
      return null;
    }

    Drive driveService = new Drive.Builder(httpTransport, jsonFactory, credential)
      .setApplicationName("CSpaceConfig")
      .build();

    return driveService;
  }

  public BatchUpdateSpreadsheetResponse batchUpdateSpreadsheet(String spreadsheetId, List<Request> requests) {
    BatchUpdateSpreadsheetResponse batchUpdateResponse = null;

    if (requests.size() > 0) {
      if (requestSincePauseCount >= QUOTA_WRITE_REQUEST_LIMIT) {
        System.out.println("Pausing for quota at " + requestSincePauseCount + " requests");

        try {
          Thread.sleep((QUOTA_TIME_SECS + 1) * 1000);
        } catch (Exception e) {
          System.err.println(e);
        }

        requestSincePauseCount = 0;
      }

      System.out.println("Sending batch update of " + requests.size() + " requests");

      BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest().setRequests(requests);

      try {
        batchUpdateResponse = sheetsService.spreadsheets().batchUpdate(spreadsheetId, body).execute();
        requestSincePauseCount += requests.size();
      } catch(Exception e) {
        System.err.println(e);
        System.out.println("Batch update failed. Waiting 30 seconds to retry.");

        try {
          Thread.sleep(30 * 1000);
        } catch (Exception ex) {
          System.err.println(ex);
        }

        try {
          batchUpdateResponse = sheetsService.spreadsheets().batchUpdate(spreadsheetId, body).execute();
          requestSincePauseCount += requests.size();
        } catch (Exception ex) {
          System.err.println(ex);
        }
      }
    }

    return batchUpdateResponse;
  }
}
