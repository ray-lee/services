package org.collectionspace.config;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private static final int QUOTA_TIME_SECS = 60;
  private static final int QUOTA_WRITE_REQUEST_LIMIT = 55;
  // private static final String SPREADSHEET_ID = "1fax09p6PuQORdOEv1BkBwxGQHFaaA8cv4Mql88Jg18c";

  private Drive driveService;
  private Sheets sheetsService;
  private Map<String, Sheet> sheetsByRecordType;
  private Map<String, Map<String, Integer>> componentRowNumbersByRecordType;
  private Map<String, RecordType> coreRecordTypes;

  private int requestCount = 0;
  private int requestSincePauseCount = 0;

  public GoogleSheetWriter() {
    this.driveService = buildDriveService();
    this.sheetsService = buildSheetsService();
  }

  public void write(String name, List<RecordType> recordTypes) {
    if (sheetsService == null) {
      return;
    }

    sheetsByRecordType = new HashMap<String, Sheet>();
    componentRowNumbersByRecordType = new HashMap<String, Map<String, Integer>>();

    String versionFolderId = findOrCreateFolder(CONFIG_FOLDER_ID, VERSION);
    String spreadsheetId = findOrCreateSpreadsheet(versionFolderId, name);

    writeSpreadsheet(spreadsheetId, recordTypes);
  }

  public void writeSpreadsheet(String spreadsheetId, List<RecordType> recordTypes) {
    writeNotes(spreadsheetId, recordTypes);

    sheetsByRecordType = getSheetsByRecordType(spreadsheetId);

    for (RecordType recordType : recordTypes) {
      String serviceType = recordType.getServiceType();

      if (
        serviceType.equals("object")
        || serviceType.equals("procedure")
        || serviceType.equals("authority")
      ) {
        int recordTypeRequestCount = writeRecordType(spreadsheetId, recordType);

        requestCount = requestCount + recordTypeRequestCount;
        requestSincePauseCount = requestSincePauseCount + recordTypeRequestCount;

        System.out.println("Requests sent: " + requestCount);

        if (requestSincePauseCount >= QUOTA_WRITE_REQUEST_LIMIT) {
          System.out.println("Pausing for quota at " + requestCount + " requests");

          try {
            Thread.sleep((QUOTA_TIME_SECS + 1) * 1000);
          } catch (Exception e) {
            System.err.println(e);
          }

          requestSincePauseCount = 0;
        }
      }
    }
  }

  public void writeNotes(String spreadsheetId, List<RecordType> recordTypes) {
    if (sheetsService == null) {
      return;
    }

    String notesSheetName = "Sheet1";
    Spreadsheet sp = null;

    try {
      sp = sheetsService.spreadsheets().get(spreadsheetId).execute();
    } catch (Exception e) {
      System.err.println(e);
    }

    Integer sheetId = null;

    if (sp != null) {
      List<Sheet> sheets = sp.getSheets();

      for (Sheet sheet : sheets) {
        if (sheet.getProperties().getTitle().equals(notesSheetName)) {
          sheetId = sheet.getProperties().getSheetId();

          break;
        }
      }
    }

    if (sheetId == null) {
      sheetId = createSheet(spreadsheetId, notesSheetName);
    }

    List<RecordType> customizedRecordTypes = findCustomizedRecordTypes(recordTypes);

    if (customizedRecordTypes.size() > 0) {
      String desc = "";

      for (RecordType recordType : customizedRecordTypes) {
        desc += recordType.getName() + " (" + recordType.getId() + ")\n";
      }

      List<Request> requests = new ArrayList<>();
      List<RowData> newRows = new ArrayList<RowData>();
      List<CellData> rowCells = new ArrayList<>();

      rowCells.add(
        new CellData()
          .setUserEnteredValue(
            new ExtendedValue().setStringValue(
              "Record types have been customized with fields that don't exist in core, or fields that are relabeled from core:\n" + desc
            )
          )
      );

      newRows.add(new RowData().setValues(rowCells));

      requests.add(
        new Request().setAppendCells(
          new AppendCellsRequest()
            .setSheetId(sheetId)
            .setFields("*")
            .setRows(newRows)
        )
      );

      BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest().setRequests(requests);

      try {
        sheetsService.spreadsheets().batchUpdate(spreadsheetId, body).execute();
      } catch(Exception e) {
        System.err.println(e);
      }
    }
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

  private List<RecordType> findCustomizedRecordTypes(List<RecordType> recordTypes) {
    List<RecordType> customizedRecordTypes = new ArrayList<>();

    for (RecordType recordType : recordTypes) {
      String serviceType = recordType.getServiceType();

      if (
        serviceType.equals("object")
        || serviceType.equals("procedure")
        || serviceType.equals("authority")
      ) {
        if (isCustomizedFromCore(recordType)) {
          customizedRecordTypes.add(recordType);
        }
      }
    }

    return customizedRecordTypes;
  }

  private boolean isCustomizedFromCore(RecordType recordType) {
    for (ConfigurableFormComponent formComponent : recordType.getFormComponents().values()) {
      if (getCoreNameIfDiffers(recordType, formComponent) != null) {
        return true;
      };

      if (!isExistsInCore(recordType, formComponent)) {
        return true;
      }
    };

    return false;
  }

  public int writeRecordType(String spreadsheetId, RecordType recordType) {
    System.out.println("Writing record type: " + recordType.getId());

    if (sheetsService == null) {
      return 0;
    }

    String recordTypeId = recordType.getId();
    Sheet sheet = sheetsByRecordType.get(recordTypeId);
    Integer sheetId;
    boolean newSheetCreated = false;

    if (sheet == null) {
      sheetId = createSheetForRecordType(spreadsheetId, recordType);
      newSheetCreated = true;

      componentRowNumbersByRecordType.put(recordType.getId(), new HashMap<String, Integer>());
    } else {
      sheetId = sheet.getProperties().getSheetId();

      readSheetForRecordType(spreadsheetId, sheet, recordType);
    }

    List<Request> requests = new ArrayList<>();

    if (sheetId != null) {
      List<RowData> newRows = new ArrayList<RowData>();

      CellFormat defaultFormat = new CellFormat()
        .setWrapStrategy("CLIP")
        .setVerticalAlignment("TOP");

      CellFormat headerFormat = defaultFormat.clone()
        .setWrapStrategy("WRAP")
        .setHorizontalAlignment("CENTER")
        .setVerticalAlignment("MIDDLE")
        .setTextFormat(
          new TextFormat().setBold(true)
        );

      if (newSheetCreated) {
        List<CellData> headerCells = new ArrayList<>();

        for (Form form : recordType.getForms()) {
          headerCells.add(
            new CellData()
              .setUserEnteredFormat(headerFormat)
              .setUserEnteredValue(
                new ExtendedValue().setStringValue(form.getName() + " Position (" + form.getId() + ")")
              )
          );
        }

        headerCells.addAll(Arrays.asList(
          new CellData()
            .setUserEnteredFormat(headerFormat)
            .setUserEnteredValue(
              new ExtendedValue().setStringValue("ID")
            ),
          new CellData()
            .setUserEnteredFormat(headerFormat)
            .setUserEnteredValue(
              new ExtendedValue().setStringValue("In Core")
            ),
          new CellData()
            .setUserEnteredFormat(headerFormat)
            .setUserEnteredValue(
              new ExtendedValue().setStringValue("Core Name/Full Name (if different)")
            ),
          new CellData()
            .setUserEnteredFormat(headerFormat)
            .setUserEnteredValue(
              new ExtendedValue().setStringValue("Name")
            ),
          new CellData()
            .setUserEnteredFormat(headerFormat)
            .setUserEnteredValue(
              new ExtendedValue().setStringValue("Full Name")
            ),
          new CellData()
            .setUserEnteredFormat(headerFormat)
            .setUserEnteredValue(
              new ExtendedValue().setStringValue("Help Text")
            )
        ));

        newRows.add(new RowData().setValues(headerCells));
      }

      for (ConfigurableFormComponent formComponent : recordType.getFormComponents().values()) {
        Map<String, Integer> componentRowNumbers = componentRowNumbersByRecordType.get(recordType.getId());

        if (componentRowNumbers != null && componentRowNumbers.containsKey(formComponent.getId())) {
          continue;
        }

        Map<String, Integer> templatePositions = formComponent.getTemplatePositions();
        List<CellData> rowCells = new ArrayList<>();

        for (Form form : recordType.getForms()) {
          Integer position = templatePositions.get(form.getId());
          Double number = (position == null) ?  null : Double.valueOf(position);

          rowCells.add(
            new CellData()
              .setUserEnteredFormat(defaultFormat)
              .setUserEnteredValue(
                new ExtendedValue().setNumberValue(number)
              )
          );
        }

        String coreNameIfDiffers = getCoreNameIfDiffers(recordType, formComponent);
        Boolean existsInCore = isExistsInCore(recordType, formComponent);

        rowCells.addAll(Arrays.asList(
          new CellData()
            .setUserEnteredFormat(
              defaultFormat.clone()
                .setHorizontalAlignment("RIGHT")
            )
            .setUserEnteredValue(
              new ExtendedValue().setStringValue(formComponent.getId())
            ),
          new CellData()
            .setUserEnteredFormat(defaultFormat)
            .setUserEnteredValue(
              new ExtendedValue().setBoolValue(existsInCore)
          ),
            new CellData()
            .setUserEnteredFormat(defaultFormat)
            .setUserEnteredValue(
              new ExtendedValue().setStringValue(coreNameIfDiffers)
            ),
          new CellData()
            .setUserEnteredFormat(defaultFormat)
            .setUserEnteredValue(
              new ExtendedValue().setStringValue(formComponent.getName())
            ),
          new CellData()
            .setUserEnteredFormat(defaultFormat)
            .setUserEnteredValue(
              new ExtendedValue().setStringValue(formComponent.getFullName())
            ),
          new CellData()
            .setUserEnteredFormat(
              defaultFormat.clone()
                .setWrapStrategy("WRAP")
            )
        ));

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

      int nameColumnIndex = recordType.getForms().size() + 1;
      int helpTextColumnIndex = nameColumnIndex + 2;

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
                .setEndIndex(helpTextColumnIndex + 1)
            )
            .setFields("*")
            .setProperties(
              new DimensionProperties()
                .setPixelSize(300)
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
                    .setStartColumnIndex(0)
                    .setEndColumnIndex(helpTextColumnIndex)
                    .setStartRowIndex(0)
                )
                .setDescription("These cells are informational only. Do not edit.")
                .setRequestingUserCanEdit(true)
            )
        )
      );
    }

    if (requests.size() > 0) {
      BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest().setRequests(requests);

      try {
        sheetsService.spreadsheets().batchUpdate(spreadsheetId, body).execute();

        return requests.size();
      } catch(Exception e) {
        System.err.println(e);
      }
    }

    return 0;
  }

  private boolean isExistsInCore(RecordType recordType, ConfigurableFormComponent formComponent) {
    if (coreRecordTypes == null) {
      return false;
    }

    RecordType coreRecordType = coreRecordTypes.get(recordType.getId());

    if (coreRecordType == null) {
      return false;
    }

    ConfigurableFormComponent coreFormComponent = coreRecordType.getFormComponents().get(formComponent.getId());

    return (coreFormComponent != null);
  }

  private String getCoreNameIfDiffers(RecordType recordType, ConfigurableFormComponent formComponent) {
    if (coreRecordTypes == null) {
      return null;
    }

    RecordType coreRecordType = coreRecordTypes.get(recordType.getId());

    if (coreRecordType == null) {
      return null;
    }

    ConfigurableFormComponent coreFormComponent = coreRecordType.getFormComponents().get(formComponent.getId());

    if (
      coreFormComponent != null
      && (
        !StringUtils.equals(coreFormComponent.getName(), formComponent.getName())
        || !StringUtils.equals(coreFormComponent.getFullName(), formComponent.getFullName())
      )
    ) {
      return (coreFormComponent.getName() + "/" + coreFormComponent.getFullName());
    }

    return null;
  }

  public Integer createSheetForRecordType(String spreadsheetId, RecordType recordType) {
    return createSheet(spreadsheetId, recordType.getName() + " (" + recordType.getId() + ")");
  }

  public Integer createSheet(String spreadsheetId, String name) {
    List<Request> requests = new ArrayList<>();

    requests.add(
      new Request().setAddSheet(
        new AddSheetRequest().setProperties(
          new SheetProperties()
            .setTitle(name)
            .setGridProperties(
              new GridProperties().setFrozenRowCount(1)
            )
        )
      )
    );

    BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest().setRequests(requests);
    BatchUpdateSpreadsheetResponse batchUpdateResponse = null;

    try {
      batchUpdateResponse = sheetsService.spreadsheets().batchUpdate(spreadsheetId, body).execute();
    } catch(Exception e) {
      System.err.println(e);
    }

    if (batchUpdateResponse == null) {
      return null;
    }

    AddSheetResponse addSheetResponse = batchUpdateResponse.getReplies().get(0).getAddSheet();

    return addSheetResponse.getProperties().getSheetId();
  }

  public void readSheetForRecordType(String spreadsheetId, Sheet sheet, RecordType recordType) {
    if (sheetsService == null) {
      return;
    }

    String sheetTitle = sheet.getProperties().getTitle();
    String headerRowRange = "'" + sheetTitle + "'!1:1";
    ValueRange headerRowResult = null;

    try {
      headerRowResult = sheetsService.spreadsheets().values().get(spreadsheetId, headerRowRange).execute();
    } catch (Exception e) {
      System.err.println(e);
    }

    if (headerRowResult == null) {
      return;
    }

    List<List<Object>> values = headerRowResult.getValues();
    List<Object> headerRow = (values == null) ? new ArrayList<Object>() : values.get(0);
    int idColumnIndex = (headerRow == null) ? -1 : headerRow.indexOf("ID");

    if (idColumnIndex < 0) {
      return;
    }

    char idColumnChar = (char) (65 + idColumnIndex);
    String idColumnRange = "'" + sheetTitle + "'!" + idColumnChar + ":" + idColumnChar;
    ValueRange idColumnResult = null;

    try {
      idColumnResult = sheetsService.spreadsheets().values().get(spreadsheetId, idColumnRange).execute();
    } catch (Exception e) {
      System.err.println(e);
    }

    if (idColumnResult == null) {
      return;
    }

    Map<String, Integer> componentRowNumbers = componentRowNumbersByRecordType.get(recordType.getId());

    if (componentRowNumbers == null) {
      componentRowNumbers = new HashMap<String, Integer>();

      componentRowNumbersByRecordType.put(recordType.getId(), componentRowNumbers);
    }

    List<List<Object>> rows = idColumnResult.getValues();

    for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
      List<Object> row = rows.get(rowIndex);
      String componentId = (String) row.get(0);

      componentRowNumbers.put(componentId, rowIndex + 1);
    }
  }

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

  public void setCoreRecordTypes(Map<String, RecordType> coreRecordTypes) {
    this.coreRecordTypes = coreRecordTypes;
  }
}
