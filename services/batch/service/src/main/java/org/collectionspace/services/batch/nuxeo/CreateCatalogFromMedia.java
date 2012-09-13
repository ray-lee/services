package org.collectionspace.services.batch.nuxeo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Date;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import java.util.HashMap;
import java.util.ListIterator;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.collectionspace.services.client.CollectionSpaceClientUtils;
import org.collectionspace.services.client.CollectionObjectClient;
import org.collectionspace.services.client.MediaClient;
import org.collectionspace.services.client.PayloadOutputPart;
import org.collectionspace.services.client.PoxPayload;
import org.collectionspace.services.client.PoxPayloadOut;
import org.collectionspace.services.client.RelationClient;
import org.collectionspace.services.batch.BatchInvocable;
import org.collectionspace.services.common.ResourceMap;
import org.collectionspace.services.common.ResourceBase;
import org.collectionspace.services.common.invocable.InvocationContext;
import org.collectionspace.services.common.invocable.InvocationResults;
import org.collectionspace.services.jaxb.AbstractCommonList;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;

import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.specimpl.UriInfoImpl;
import org.jboss.security.Base64Encoder;

import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class CreateCatalogFromMedia implements BatchInvocable {
	private static ArrayList<String> invocationModes = null;
	private InvocationContext context;
	private int completionStatus;
	private ResourceMap resourceMap;
	private InvocationResults results;
	private InvocationError errorInfo;
	private final String traceLogFile = "/tmp/batchtrace.txt";
	private FileWriter traceLog = null;

	private String catalogPayload = null;
	private String relPayload = null;

	private final String RELATION_TYPE = "affects";
	private final String CAT_DOCTYPE = "CollectionObject";
	private final String RELATION_PREDICATE_DISP = "affects";
	protected final int CREATED_STATUS = Response.Status.CREATED
			.getStatusCode();
	protected final int BAD_REQUEST_STATUS = Response.Status.BAD_REQUEST
			.getStatusCode();
	protected final int INT_ERROR_STATUS = Response.Status.INTERNAL_SERVER_ERROR
			.getStatusCode();

	public CreateCatalogFromMedia() {
		trace("Initialized at " + new Date().toString());
		CreateCatalogFromMedia.setupClassStatics();
		context = null;
		completionStatus = STATUS_UNSTARTED;
		resourceMap = null;
		results = new InvocationResults();
		errorInfo = null;
	}

	private static void setupClassStatics() {
		if (invocationModes == null) {
			invocationModes = new ArrayList<String>(1);
			invocationModes.add(INVOCATION_MODE_SINGLE);
			invocationModes.add(INVOCATION_MODE_LIST);
		}
	}

	/**
	 * @return a non-empty set of modes supported by this plugin. First method
	 *         called after invocation
	 */
	@Override
	public List<String> getSupportedInvocationModes() {
		trace("In getSupportedInvocationModes");
		return CreateCatalogFromMedia.invocationModes;
	}

	/**
	 * Sets the invocation context for the batch job. Second method called after
	 * invocation, before run().
	 * 
	 * @param context
	 *            an instance of InvocationContext.
	 */
	@Override
	public void setInvocationContext(InvocationContext context) {
		trace("In setInvocationContext");
		this.context = context;
	}

	@Override
	public void run() {
		trace("Entering run() method");
		completionStatus = STATUS_MIN_PROGRESS;

		printContextInfo();

		String mediaCsid = context.getSingleCSID();
		
		createCatalogFromMedia(mediaCsid);
	}
	
	public void createCatalogFromMedia(String mediaCsid) {
		trace("Looking up: " + mediaCsid);

//		MediaClient client = new MediaClient();
//		trace("Reading client: " + mediaCsid);
//		ClientResponse<String> res = client.read(mediaCsid);
//		trace("Done reading client: " + mediaCsid);
//
//		if (res.getStatus() == Response.Status.OK.getStatusCode()) {
//			trace("Reading Media with CSID " + mediaCsid + ". "
//					+ "Status code = " + res.getStatus());
//		}
//		else {
//			trace("Error reading media record. Status code = "
//					+ res.getStatus());
//			trace("Status reason:" + res.getResponseStatus().toString());
//		}
				
		// We don't have access to the ResourceBase.get method that just returns a PoxPayloadOut,
		// so we need to call the method that returns a serialized one, and deserialize it.

		ResourceBase resource = resourceMap.get(MediaClient.SERVICE_NAME);
		byte[] response = resource.get(createUriInfo(), mediaCsid);
		trace(new String(response));
		
		PoxPayloadOut payload = null;		
		
		try {
			payload = new PoxPayloadOut(response);
		} catch (DocumentException e) {
			trace(e.getMessage());
		}
		
		String identificationNumber = getFieldValue(payload, "media_common", "identificationNumber");
		String tenantId = getFieldValue(payload, "collectionspace_core", "tenantId");
		String title = getFieldValue(payload, "media_common", "title");
		String scientificTaxonomy = getFieldValue(payload, "media_ucjeps", "scientificTaxonomy");

		trace("identificationNumber: " + identificationNumber);
		trace("tenantId: " + tenantId);
		trace("title: " + title);
		trace("scientificTaxonomy: " + scientificTaxonomy);

		List<String> collectionObjectCsids = findCollectionObjectsByObjectNumber(identificationNumber);
		trace(collectionObjectCsids.size() + " existing CollectionObjects");
		
		for (String csid : collectionObjectCsids) {
			trace("csid: " + csid);
		}

		MediaInfo mediaInfo = new MediaInfo();
		mediaInfo.setCsid(mediaCsid);
		mediaInfo.setIdentificationNumber(identificationNumber);
		mediaInfo.setTenantId(tenantId);
		mediaInfo.setTitle(title);
		mediaInfo.addScientificTaxonomy(scientificTaxonomy);		
		
		String statusMsg = "Catalog and Relation creation failed.";
		completionStatus = STATUS_ERROR;

//		String mediaXML = fetchMediaXML(mediaCsid);
//		trace("mediaXML: "
//				+ ((mediaXML == null) ? "No xml document" : mediaXML));
//
//		BatchJobParserHandler handler = null;
//		MediaInfo mediaInfo = null;
//
//		if (mediaXML != null) {
//			handler = new BatchJobParserHandler(mediaXML);
//			handler.parseXMlDocument();
//			mediaInfo = handler.getMediaInfo();
//		}

		if (createCatalogRecord(mediaInfo) == STATUS_COMPLETE) {
			statusMsg = "Catalog created successfully.";
			String catalogId = results.getPrimaryURICreated();

			if (createRelation(catalogId, mediaCsid, RELATION_TYPE) == STATUS_COMPLETE) {
				statusMsg = "Catalog and Relation created successfully.";
			} else
				completionStatus = STATUS_ERROR;
		} else
			completionStatus = STATUS_ERROR;

		trace("CATALOG PAYLOAD: "
				+ ((catalogPayload == null) ? "No catalog payload"
						: catalogPayload));

		trace("RELATION PAYLOADS: "
				+ ((relPayload == null) ? "No relation payload" : relPayload));

		results.setNumAffected(1);
		results.setUserNote("Wrote output to temp file");

		// Temporary, do not leave this here.
		// completionStatus = STATUS_COMPLETE;
		trace(statusMsg);
		traceClose("Closing");		
	}
	
	private List<String> findCollectionObjectsByObjectNumber(String objectNumber) {
		List<String> csids = new ArrayList<String>();
		ResourceBase resource = resourceMap.get(CollectionObjectClient.SERVICE_NAME);
		AbstractCommonList list = resource.getList(createObjectNumberSearchUriInfo(objectNumber));
		
		for (AbstractCommonList.ListItem item : list.getListItem()) {
			for (org.w3c.dom.Element element : item.getAny()) {
				if (element.getTagName().equals("csid")) {
					csids.add(element.getTextContent());
					break;
				}
			}
		}
		
		return csids;
	}

	/**
	 * Create a stub UriInfo
	 */
	private UriInfo createUriInfo() {
		return createUriInfo("");
	}
	
	private UriInfo createUriInfo(String queryString) {
		URI absolutePath = null;
		URI baseUri = null;
		
		try {
			absolutePath = new URI("");
			baseUri = new URI("");
		} catch (URISyntaxException e) {
			trace(e.getMessage());
		}
		
		return new UriInfoImpl(absolutePath, baseUri, "", queryString, Collections.EMPTY_LIST);
	}
	
	private UriInfo createObjectNumberSearchUriInfo(String objectNumber) {
		String queryString = "as=( (collectionobjects_common:objectNumber ILIKE \"" + objectNumber + "\") )&wf_deleted=false";
		
		URI uri = null;
		
		try {
			uri = new URI(null, null, null, queryString, null);
		} catch (URISyntaxException e) {
			trace(e.getMessage());
		}
		
		trace("search query: " + uri.getRawQuery());
		
		return createUriInfo(uri.getRawQuery());		
	}

	/**
	 * Get a field value from a PoxPayloadOut, given a part name and xpath expression.
	 * This implementation uses an xpath query on the DOM, but it could in theory
	 * use a JAXB object (obtained via PoxPayload.toObject), and not deal with the
	 * DOM at all. The problem with the latter is that we haven't been creating JAXB
	 * schemas for extensions.
	 */
	private String getFieldValue(PoxPayloadOut payload, String partLabel, String fieldPath) {
		String value = null;
		PayloadOutputPart part = payload.getPart(partLabel);

		if (part != null) {
			Element element = part.asElement();
			Node node = element.selectSingleNode(fieldPath);

			if (node != null) {
				value = node.getText();
			}
		}
		
		return value;
	}

	private String fetchMediaRecord(String csid) {
		trace("In fetchMediaRecord with " + csid);
		UriInfo uriInfo = null;
		ResourceBase base = resourceMap.get(context);
		if (base == null)
			trace("Null base");
		else {
			trace("getting Bytes");
			byte bytes[] = base.get(uriInfo, csid);
			if (bytes == null)
				trace("no bytes");
			else {
				trace("got some bytes");
				String bstring = bytes.toString();
				if (bstring == null)
					trace("bytes to string was null");
				else
					trace("Got bytes: " + bstring);
			}
		}
		// return bytes.toString();
		String res = "All done in fetch";
		return res;
	}

	/*
	 * GET an XML media record from a URL
	 */
	private String fetchMediaXML(String csid) {
		trace("In fetchMediaXML with " + csid);
		// String userpass = "admin@ucjeps.cspace.berkeley.edu:1ulnaria";
		String userpass = "admin@ucjeps.cspace.berkeley.edu:Administrator";
		String host = "http://localhost";
		String port = "8180";
		String srvc = "cspace-services/media";
		String mediaURL = host + ":" + port + "/" + srvc + "/" + csid;
		trace("getting " + mediaURL);
		URL url;
		HttpURLConnection conn = null;
		BufferedReader br;
		String line;
		StringBuilder result = new StringBuilder();

		try {
			url = new URL(mediaURL);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");

			String basicAuth = "Basic " + encodeBase64(userpass.getBytes());
			trace(basicAuth);
			conn.setRequestProperty("Authorization", basicAuth);
			br = new BufferedReader(
					new InputStreamReader(conn.getInputStream()));

			while ((line = br.readLine()) != null) {
				result.append(line);
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
			return null;
		} finally {
			// if( conn != null )
			// conn.disconnect();
		}
		return result.toString();
	}

	/*
	 * Utility method for base64 encoding byte arrays
	 */
	public String encodeBase64(byte bytes[]) {
		trace("In encodeBase64");
		String base64 = null;
		try {
			base64 = Base64Encoder.encode(bytes);
		} catch (Exception e) {
			base64 = null;
		}
		return base64;
	}

	/*
	 * Create the payload String used by createCatalogRecord
	 */
	private String createCatalogRecordPayload(MediaInfo mediaInfo) {
		trace("In createCatalogRecordPayload");

		String objectNumber = mediaInfo.getIdentificationNumber();
		String title = mediaInfo.getTitle();
		String taxon = mediaInfo.getScientificTaxonomy(0);

		String payload = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<document name=\""
				+ CollectionObjectClient.SERVICE_NAME
				+ "\">\n"
				+ "<nh:collectionobjects_naturalhistory "
				+ "xmlns:nh=\"http://collectionspace.org/services/collectionobject/domain/naturalhistory\">\n"
				+ "<taxonomicIdentGroupList>"
				+ "<taxonomicIdentGroup>"
				+ "<taxon>"
				+ taxon
				+ "</taxon>"
				+ "</taxonomicIdentGroup>"
				+ "</taxonomicIdentGroupList>\n"
				+ "</nh:collectionobjects_naturalhistory>"
				+ "<ns2:collectionobjects_common "
				+ "xmlns:ns2=\"http://collectionspace.org/services/collectionobject\"\n"
				+ "xmlns:ns3=\"http://collectionspace.org/services/jaxb\">\n"
				+ "<objectNumber>"
				+ objectNumber
				+ "</objectNumber>\n"
				+ "<titleGroupList><titleGroup>\n"
				+ "<title>"
				+ title
				+ "</title>\n"
				+ "</titleGroup></titleGroupList>\n"
				+ "</ns2:collectionobjects_common>\n" + "</document>";

		return payload;
	}

	/*
	 * Create the payload String used by createRelation
	 */
	private String createRelationPayload(String subjectId, String subjectType,
			String objectId, String objectType, String relType) {
		trace("In createRelationPayload");

		String payload = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<document name=\""
				+ RelationClient.SERVICE_NAME
				+ "\">\n"
				+ "<ns2:relations_common "
				+ "xmlns:ns2=\"http://collectionspace.org/services/relation\"\n"
				+ "xmlns:ns3=\"http://collectionspace.org/services/jaxb\">\n"
				+ "<subjectCsid>"
				+ subjectId
				+ "</subjectCsid>\n"
				+ "<subjectDocumentType>"
				+ subjectType
				+ "</subjectDocumentType>\n"
				+ "<objectCsid>"
				+ objectId
				+ "</objectCsid>\n"
				+ "<objectDocumentType>"
				+ objectType
				+ "</objectDocumentType>\n"
				+ "<relationshipType>"
				+ relType
				+ "</relationshipType>\n"
				+ "<predicateDisplayName>"
				+ relType
				+ "</predicateDisplayName>\n"
				+ "</ns2:relations_common></document>\n";

		return payload;
	}

	private int createCatalogRecord(MediaInfo mediaInfo) {
		trace("In createCatalogRecord");

		catalogPayload = createCatalogRecordPayload(mediaInfo);

		if (catalogPayload == null) {
			trace("Null catalogPayload");
			completionStatus = STATUS_ERROR;
			errorInfo = new InvocationError(INT_ERROR_STATUS,
					"CreateCatalogFromMediaBatch problem creating payload!");
			results.setUserNote(errorInfo.getMessage());
			return completionStatus;
		} else
			trace("Got catalogPayload " + catalogPayload);

		ResourceBase resource = resourceMap
				.get(CollectionObjectClient.SERVICE_NAME);
		Response response = resource.create(resourceMap, null, catalogPayload);

		if (response.getStatus() != CREATED_STATUS) {
			completionStatus = STATUS_ERROR;
			errorInfo = new InvocationError(INT_ERROR_STATUS,
					"CreateCatalogFromMediaBatch problem creating new Catalog record!");
			results.setUserNote(errorInfo.getMessage());
		} else {
			String newId = CollectionSpaceClientUtils.extractId(response);
			results.setPrimaryURICreated(newId);
			trace("Create " + newId);
			completionStatus = STATUS_COMPLETE;
		}

		return completionStatus;
	}

	private int createRelation(String catalogId, String mediaId, String relType) {
		trace("In createRelation");
		String catalogType = "CatalogRecord";
		String mediaType = "Media";

		String payload1 = createRelationPayload(catalogId, catalogType,
				mediaId, mediaType, relType);
		String payload2 = createRelationPayload(mediaId, mediaType, catalogId,
				catalogType, relType);

		relPayload = payload1 + "\n" + payload2;

		ResourceBase resource = resourceMap.get(RelationClient.SERVICE_NAME);
		Response response = resource.create(resourceMap, null, payload1);

		if (response.getStatus() != CREATED_STATUS) {
			completionStatus = STATUS_ERROR;
			errorInfo = new InvocationError(INT_ERROR_STATUS,
					"CreateCatalogFromMediaBatchJob problem creating new relation!");
			results.setUserNote(errorInfo.getMessage());
		} else {
			response = resource.create(resourceMap, null, payload2);
			if (response.getStatus() != CREATED_STATUS) {
				completionStatus = STATUS_ERROR;
				errorInfo = new InvocationError(INT_ERROR_STATUS,
						"CreateCatalogFromMediaBatchJob problem creating new reverse relation!");
				results.setUserNote(errorInfo.getMessage());
			}
		}
		completionStatus = STATUS_COMPLETE;
		return completionStatus;
	}

	private void parseXMLRecord(String document, HashMap<String, String> tags) {

	}

	/**
	 * @return one of the STATUS_* constants, or a value from 1-99 to indicate
	 *         progress. Implementations need not support partial completion
	 *         (progress) values, and can transition from STATUS_MIN_PROGRESS to
	 *         STATUS_COMPLETE.
	 */
	@Override
	public int getCompletionStatus() {
		trace("In getCompletionStatus");
		return completionStatus;
	}

	/**
	 * @return information about the batch job actions and results
	 */
	@Override
	public InvocationResults getResults() {
		trace("In getResults");
		if (completionStatus != STATUS_COMPLETE)
			return null;
		return results;
	}

	/**
	 * @return a user-presentable note when an error occurs in batch processing.
	 *         Will only be called if getCompletionStatus() returns
	 *         STATUS_ERROR.
	 */
	@Override
	public InvocationError getErrorInfo() {
		trace("In getErrorInfo");
		return errorInfo;
	}

	/**
	 * Sets the invocation context for the batch job. Third method called after
	 * invocation, before run().
	 * 
	 * @param context
	 *            an instance of InvocationContext.
	 */
	@Override
	public void setResourceMap(ResourceMap resourceMap) {
		trace("In setResourceMap");
		this.resourceMap = resourceMap;
	}

	// Utility methods for tracing during dev
	private void printContextInfo() {
		trace("In printContextInfo");
		if (context == null)
			trace("Context is null");
		else {
			trace("Context is NOT null");
			InvocationContext.Params params = context.getParams();

			if (params != null) {
				List<InvocationContext.Params.Param> paramList = params
						.getParam();
				if (paramList.isEmpty())
					trace("Params: empty list");
				else {
					ListIterator<InvocationContext.Params.Param> paramIter = paramList
							.listIterator();
					while (paramIter.hasNext()) {
						trace(paramIter.next().getKey());
					}
				}
			} else
				trace("Null params");

			String cmode = context.getMode();
			trace("Mode: " + (cmode == null ? "Empty mode" : cmode));
			String docType = context.getDocType();
			trace("docType: "
					+ (docType == null ? "Empty docType" : "|" + docType + "|"));
			String sCSID = context.getSingleCSID();
			trace("singleCSID: " + (sCSID == null ? "Empty singleCSID" : sCSID));
			String gCSID = context.getGroupCSID();
			trace("groupCSID: " + (gCSID == null ? "Empty groupCSID" : gCSID));
			trace("Getting ListCSIDs");
			InvocationContext.ListCSIDs listCSIDs = context.getListCSIDs();

			if (listCSIDs != null) {
				trace("Beginning ListCSIDS");
				ListIterator<String> csids = listCSIDs.getCsid().listIterator();

				while (csids.hasNext()) {
					trace(csids.next());
				}
				trace("End of ListCSIDS");
			} else
				trace("Null ListCSIDS");

			// String statusMsg = "Catalog and Relation creation failed.";
			//
			// if( createCatalogRecord( "MH-10030", "My Object" ) ==
			// STATUS_COMPLETE )
			// {
			// statusMsg = "Catalog created successfully.";
			// if( createRelation( "IDNUMBER1", "IDNUMBER2", "affects" ) ==
			// STATUS_COMPLETE )
			// {
			// completionStatus = STATUS_COMPLETE;
			// statusMsg = "Catalog and Relation created successfully.";
			// }
			// else
			// completionStatus = STATUS_ERROR;
			// }
			// else
			// completionStatus = STATUS_ERROR;
			//
			// trace( "CATALOG PAYLOAD: " +
			// ( catalogPayload == null ?
			// "No catalog payload" : catalogPayload ));
			// trace( "RELATION PAYLOADS: " +
			// ( relPayload == null ?
			// "No relation payload" : relPayload ));
			// trace( statusMsg );
		}
	}
	
	private void trace(String msg) {
		if (traceLog == null) {
			try {
				traceLog = new FileWriter(traceLogFile, true);
			} catch (IOException e) {
				traceLog = null;
				System.err.println("Unable to open traceLog");
				return;
			}
		}

		if (traceLog != null) {
			try {
				traceLog.write(((msg == null) ? "NULL" : msg) + "\n");
			} catch (IOException e) {
				System.err.println("IOException writing to logfile");
			}
		}
	}

	private void traceClose(String msg) {
		if (traceLog != null) {
			try {
				trace(msg);
				traceLog.close();
			} catch (IOException e) {
				System.err.println("Exception closing log; " + e.getMessage());
			} finally {
				traceLog = null;
			}
		}
	}

	// Used for testing
	public String getRelationPayload(String id1, String type1, String id2,
			String type2, String relType) {
		trace("getRelationPayload");
		return createRelationPayload(id1, type1, id2, type2, relType);
	}

	public ArrayList<String> getServicesNames() {
		trace("getServicesNames");
		ArrayList<String> names = new ArrayList<String>();
		names.add(RelationClient.SERVICE_NAME);
		names.add(CollectionObjectClient.SERVICE_NAME);
		return names;
	}

	private class BatchJobParserHandler extends DefaultHandler {
		private SAXParserFactory spf;
		private SAXParser saxParser;
		private XMLReader xmlReader;
		private String xmlDocument;
		private boolean inUri;
		private boolean inIdentificationNumber;
		private boolean inScientificTaxonomy;
		private boolean inTenantId;
		private boolean inTitle;
		private boolean inCommon;
		private boolean inCore;
		private boolean inUcjeps;
		private MediaInfo mi;

		public BatchJobParserHandler(String xmlDocument) {
			this.xmlDocument = xmlDocument;
		}

		public MediaInfo getMediaInfo() {
			return mi;
		}

		public void parseXMlDocument() {
			try {
				spf = SAXParserFactory.newInstance();
				spf.setNamespaceAware(true);
				saxParser = spf.newSAXParser();
				xmlReader = saxParser.getXMLReader();
				xmlReader.setContentHandler(this);
				xmlReader.setErrorHandler(this);
				StringReader r = new StringReader(xmlDocument);
				xmlReader.parse(new InputSource(r));
			} catch (ParserConfigurationException e) {
				System.err.println("ParserConfigurationException: "
						+ e.getMessage());
			} catch (SAXException e) {
				System.err.println("SAXException: " + e.getMessage());
			} catch (IOException e) {
				System.err.println("IOException: " + e.getMessage());
			}
		}

		public void startDocument() {
			trace("Start document");
			inUri = false;
			inIdentificationNumber = false;
			inScientificTaxonomy = false;
			inTenantId = false;
			inTitle = false;
			inCommon = false;
			inCore = false;
			inUcjeps = false;
			mi = new MediaInfo();
		}

		public void endDocument() {
			trace("End document ");
		}

		public void startElement(String uri, String localName, String qName,
				Attributes attr) {
			trace("Start element: name=" + localName + ", qName=" + qName
					+ ", uri=" + uri);
			if (qName.equalsIgnoreCase("ns2:media_common"))
				inCommon = true;
			else if (qName.equalsIgnoreCase("ns2:collectionspace_core"))
				inCore = true;
			else if (qName.equalsIgnoreCase("ns2:media_ucjeps"))
				inUcjeps = true;
			else if (inCommon
					&& (qName.equalsIgnoreCase("identificationNumber")))
				inIdentificationNumber = true;
			else if (inUcjeps && (qName.equalsIgnoreCase("scientificTaxonomy")))
				inScientificTaxonomy = true;
			else if (inCore && (qName.equalsIgnoreCase("tenantId")))
				inTenantId = true;
			else if (inCommon && (qName.equalsIgnoreCase("title")))
				inTitle = true;
			else if (inCore && (qName.equalsIgnoreCase("uri")))
				inUri = true;
			else
				return;
		}

		public void endElement(String uri, String localName, String qName) {
			trace("End element: name=" + localName + " qName: " + qName
					+ " uri: " + uri);
			if (qName.equalsIgnoreCase("ns2:media_common"))
				inCommon = false;
			if (qName.equalsIgnoreCase("ns2:collectionspace_core"))
				inCore = false;
			else if (qName.equalsIgnoreCase("ns2:media_ucjeps"))
				inUcjeps = false;
			else if (qName.equalsIgnoreCase("identificationnumber"))
				inIdentificationNumber = false;
			else if (qName.equalsIgnoreCase("scientificTaxonomy"))
				inScientificTaxonomy = false;
			else if (qName.equalsIgnoreCase("tenantId"))
				inTenantId = false;
			else if (qName.equalsIgnoreCase("title"))
				inTitle = false;
			else if (qName.equalsIgnoreCase("uri"))
				inUri = false;
			else
				return;
		}

		public void characters(char[] ch, int start, int length) {
			trace("In characters");
			String theText = new String(ch, start, length);
			trace("In characters with string: " + theText);

			if (inIdentificationNumber)
				mi.setIdentificationNumber(theText);
			else if (inTenantId)
				mi.setTenantId(theText);
			else if (inTitle)
				mi.setTitle(theText);
			else if (inScientificTaxonomy)
				mi.addScientificTaxonomy(theText);
			else if (inUri) {
				mi.setCsid(theText.substring(theText.lastIndexOf('/') + 1));
			}
		}

		public void ignorableWhitespace(char[] ch, int start, int length) {
			trace("Ignorable whitespace: " + new String(ch, start, length));
		}
	}

	class MediaInfo {
		private String csid;
		private String title;
		private String tenantId;
		private String identificationNumber;
		private ArrayList<String> scientificTaxonomy;

		public MediaInfo() {
			this(null);
		}

		public MediaInfo(String csid) {
			this.csid = csid;
			tenantId = null;
			title = null;
			identificationNumber = null;
			scientificTaxonomy = new ArrayList<String>();
		}

		public String getCsid() {
			return csid;
		}

		public void setCsid(String csid) {
			this.csid = csid;
		}

		public String getTenantId() {
			return tenantId;
		}

		public void setTenantId(String tenantId) {
			this.tenantId = tenantId;
		}

		public String getIdentificationNumber() {
			return identificationNumber;
		}

		public void setIdentificationNumber(String identificationNumber) {
			this.identificationNumber = identificationNumber;
		}

		public ArrayList<String> getScientificTaxonomy() {
			return scientificTaxonomy;
		}

		public String getScientificTaxonomy(int ndx) {
			if (ndx >= scientificTaxonomy.size())
				return null;
			else
				return scientificTaxonomy.get(ndx);
		}

		public void addScientificTaxonomy(String scientificTaxonomy) {
			this.scientificTaxonomy.add(scientificTaxonomy);
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}
	}
}
