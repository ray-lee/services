package org.collectionspace.services.nuxeo.elasticsearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.HttpHeaders;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.node.TextNode;

import org.nuxeo.ecm.automation.jaxrs.io.documents.JsonESDocumentWriter;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;

public class DefaultESDocumentWriter extends JsonESDocumentWriter {
	private static ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public void writeDoc(JsonGenerator jg, DocumentModel doc, String[] schemas,
			Map<String, String> contextParameters, HttpHeaders headers)
			throws IOException {

		ObjectNode denormValues = objectMapper.createObjectNode();

		String docType = doc.getType();

		if (docType.startsWith("CollectionObject")) {
			CoreSession session = doc.getCoreSession();

			// Store the csids of media records that are related to this object.

			String csid = doc.getName();
			String tenantId = (String) doc.getProperty("collectionspace_core", "tenantId");
			String relationQuery = String.format("SELECT * FROM Relation WHERE relations_common:subjectCsid = '%s' AND relations_common:objectDocumentType = 'Media' AND ecm:currentLifeCycleState = 'project' AND collectionspace_core:tenantId = '%s'", csid, tenantId);

			DocumentModelList relationDocs = session.query(relationQuery);
			List<JsonNode> mediaCsids = new ArrayList<JsonNode>();

			if (relationDocs.size() > 0) {
				Iterator<DocumentModel> iterator = relationDocs.iterator();

				while (iterator.hasNext()) {
					DocumentModel relationDoc = iterator.next();
					String mediaCsid = (String) relationDoc.getProperty("relations_common", "objectCsid");

					if (isMediaPublished(session, tenantId, mediaCsid)) {
						mediaCsids.add(new TextNode(mediaCsid));
					}
				}
			}

			denormValues.putArray("mediaCsid").addAll(mediaCsids);

			// Compute the title of the record for the public browser, and store it so that it can
			// be used for sorting ES query results.

			String title = computeTitle(doc);

			if (title != null) {
				denormValues.put("title", title);
			}
		}

		jg.writeStartObject();

		writeSystemProperties(jg, doc);
		writeSchemas(jg, doc, schemas);
		writeContextParameters(jg, doc, contextParameters);
		writeDenormValues(jg, doc, denormValues);

		jg.writeEndObject();
		jg.flush();
	}

	public void writeDenormValues(JsonGenerator jg, DocumentModel doc, ObjectNode denormValues) throws IOException {
		if (denormValues != null && denormValues.size() > 0) {
			if (jg.getCodec() == null) {
				jg.setCodec(objectMapper);
			}

			Iterator<Map.Entry<String, JsonNode>> entries = denormValues.getFields();

			while (entries.hasNext()) {
				Map.Entry<String, JsonNode> entry = entries.next();

				jg.writeFieldName("collectionspace_denorm:" + entry.getKey());
				jg.writeTree(entry.getValue());
			}
		}
	}

	/**
	 * Compute a title for the public browser. This needs to be indexed in ES so that it can
	 * be used for sorting. (Even if it's just extracting the primary value.)
	 */
	private String computeTitle(DocumentModel doc) {
		List<Map<String, Object>> titleGroups = (List<Map<String, Object>>) doc.getProperty("collectionobjects_common", "titleGroupList");
		String primaryTitle = null;

		if (titleGroups.size() > 0) {
			Map<String, Object> primaryTitleGroup = titleGroups.get(0);
			primaryTitle = (String) primaryTitleGroup.get("title");
		}

		if (StringUtils.isNotEmpty(primaryTitle)) {
			return primaryTitle;
		}

		List<Map<String, Object>> objectNameGroups = (List<Map<String, Object>>) doc.getProperty("collectionobjects_common", "objectNameList");
		String primaryObjectName = null;

		if (objectNameGroups.size() > 0) {
			Map<String, Object> primaryObjectNameGroup = objectNameGroups.get(0);
			primaryObjectName = (String) primaryObjectNameGroup.get("objectName");
		}

		return primaryObjectName;
	}

	private boolean isMediaPublished(CoreSession session, String tenantId, String mediaCsid) {
		return true;
	}
}
