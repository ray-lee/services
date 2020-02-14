package org.collectionspace.services.nuxeo.elasticsearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.HttpHeaders;

import org.apache.commons.lang3.StringUtils;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.node.TextNode;
import org.collectionspace.services.client.CollectionSpaceClient;
import org.collectionspace.services.common.ServiceMain;
import org.collectionspace.services.common.api.RefNameUtils;
import org.collectionspace.services.config.tenant.TenantBindingType;
import org.nuxeo.ecm.automation.jaxrs.io.documents.JsonESDocumentWriter;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CSJsonESDocumentWriter extends JsonESDocumentWriter {
    final Logger logger = LoggerFactory.getLogger(CSJsonESDocumentWriter.class);

	@Override
	public void writeDoc(JsonGenerator jg, DocumentModel doc, String[] schemas,
			Map<String, String> contextParameters, HttpHeaders headers)
			throws IOException {

		String tenantId = (String) doc.getProperty(CollectionSpaceClient.COLLECTIONSPACE_CORE_SCHEMA, CollectionSpaceClient.COLLECTIONSPACE_CORE_TENANTID);
		TenantBindingType tenantBindingType = ServiceMain.getInstance().getTenantBindingConfigReader().getTenantBinding(tenantId);
		String documentWriterClassName = tenantBindingType.getElasticSearchDocumentWriter();

		if (StringUtils.isBlank(documentWriterClassName)) {
			return;
		}

		documentWriterClassName = documentWriterClassName.trim();

		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Class<?> documentWriterClass = null;

		try {
			documentWriterClass = loader.loadClass(documentWriterClassName);
		} catch (ClassNotFoundException e) {
			String msg = String.format("Unable to load ES document writer for tenant %s with class %s", tenantId, documentWriterClassName);

			logger.error(msg);
			logger.trace(msg, e);

			return;
		}

		if (CSJsonESDocumentWriter.class.equals(documentWriterClass)) {
			String msg = String.format("ES document writer class for tenant %s must not be CSJsonESDocumentWriter", tenantId);

			logger.error(msg);

			return;
		}

		if (!JsonESDocumentWriter.class.isAssignableFrom(documentWriterClass)) {
			String msg = String.format("ES document writer for tenant %s of class %s is not a subclass of JsonESDocumentWriter", tenantId, documentWriterClassName);

			logger.error(msg);

			return;
		}

		JsonESDocumentWriter documentWriter = null;

		try {
			documentWriter = (JsonESDocumentWriter) documentWriterClass.newInstance();
		} catch(Exception e) {
			String msg = String.format("Unable to instantiate ES document writer class: %s", documentWriterClassName);

			logger.error(msg);
			logger.trace(msg, e);

			return;
		}

		if (documentWriter != null) {
			documentWriter.writeDoc(jg, doc, schemas, contextParameters, headers);
		}
	}
}
