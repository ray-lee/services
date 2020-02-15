package org.collectionspace.services.nuxeo.elasticsearch;

import java.io.IOException;
import java.util.Map;

import javax.ws.rs.core.HttpHeaders;

import org.codehaus.jackson.JsonGenerator;

import org.nuxeo.ecm.automation.jaxrs.io.documents.JsonESDocumentWriter;
import org.nuxeo.ecm.core.api.DocumentModel;

public class DefaultESDocumentWriter extends JsonESDocumentWriter {
	@Override
	public void writeDoc(JsonGenerator jg, DocumentModel doc, String[] schemas,
			Map<String, String> contextParameters, HttpHeaders headers)
			throws IOException {

		super.writeDoc(jg, doc, schemas, contextParameters, headers);
	}
}
