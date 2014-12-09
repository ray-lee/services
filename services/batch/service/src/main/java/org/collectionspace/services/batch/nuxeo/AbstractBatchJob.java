package org.collectionspace.services.batch.nuxeo;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.collectionspace.services.batch.AbstractBatchInvocable;
import org.collectionspace.services.client.CollectionObjectClient;
import org.collectionspace.services.client.CollectionSpaceClient;
import org.collectionspace.services.client.CollectionSpaceClientUtils;
import org.collectionspace.services.client.IRelationsManager;
import org.collectionspace.services.client.MovementClient;
import org.collectionspace.services.client.PayloadOutputPart;
import org.collectionspace.services.client.PlaceAuthorityClient;
import org.collectionspace.services.client.PoxPayloadOut;
import org.collectionspace.services.client.RelationClient;
import org.collectionspace.services.client.TaxonomyAuthorityClient;
import org.collectionspace.services.client.workflow.WorkflowClient;
import org.collectionspace.services.collectionobject.nuxeo.CollectionObjectConstants;
import org.collectionspace.services.common.ResourceBase;
import org.collectionspace.services.common.ServiceMain;
import org.collectionspace.services.common.UriTemplateRegistry;
import org.collectionspace.services.common.api.RefName;
import org.collectionspace.services.common.authorityref.AuthorityRefDocList;
import org.collectionspace.services.common.context.ServiceBindingUtils;
import org.collectionspace.services.common.relation.RelationResource;
import org.collectionspace.services.common.vocabulary.AuthorityResource;
import org.collectionspace.services.jaxb.AbstractCommonList;
import org.collectionspace.services.movement.nuxeo.MovementConstants;
import org.collectionspace.services.relation.RelationsCommonList;
import org.collectionspace.services.relation.RelationsCommonList.RelationListItem;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.jboss.resteasy.specimpl.PathSegmentImpl;
import org.jboss.resteasy.specimpl.UriInfoImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractBatchJob extends AbstractBatchInvocable {
	public final int CREATED_STATUS = Response.Status.CREATED.getStatusCode();
	public final int BAD_REQUEST_STATUS = Response.Status.BAD_REQUEST.getStatusCode();
	public final int INT_ERROR_STATUS = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();

	final Logger logger = LoggerFactory.getLogger(AbstractBatchJob.class);

	public abstract void run();
	
	protected String getFieldXml(Map<String, String> fields, String fieldName) {
		return getFieldXml(fieldName, fields.get(fieldName));
	}

	protected String getFieldXml(Map<String, String> fields) {
		StringBuffer xmlBuffer = new StringBuffer();
		
		for (String fieldName : fields.keySet()) {
			xmlBuffer.append(getFieldXml(fields, fieldName));
		}
		
		return xmlBuffer.toString();
	}
	
	protected String getFieldXml(String fieldName, String fieldValue) {
		String xml = "<" + fieldName + ">" + (fieldValue == null ? "" : StringEscapeUtils.escapeXml(fieldValue)) + "</" + fieldName + ">";
		
		return xml;
	}
	
	protected String createRelation(String subjectCsid, String subjectDocType, String objectCsid, String objectDocType, String relationshipType) throws ResourceException {
		String relationCsid = null;

		String createRelationPayload =
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
			"<document name=\"relations\">" +
				"<ns2:relations_common xmlns:ns2=\"http://collectionspace.org/services/relation\" xmlns:ns3=\"http://collectionspace.org/services/jaxb\">" +
					"<subjectCsid>" + StringEscapeUtils.escapeXml(subjectCsid) + "</subjectCsid>" +
					"<subjectDocumentType>" + StringEscapeUtils.escapeXml(subjectDocType) + "</subjectDocumentType>" +
					"<objectCsid>" + StringEscapeUtils.escapeXml(objectCsid) + "</objectCsid>" +
					"<objectDocumentType>" + StringEscapeUtils.escapeXml(objectDocType) + "</objectDocumentType>" +
					"<relationshipType>" + StringEscapeUtils.escapeXml(relationshipType) + "</relationshipType>" +
				"</ns2:relations_common>" +
			"</document>";

		ResourceBase resource = getResourceMap().get(RelationClient.SERVICE_NAME);
		Response response = resource.create(getResourceMap(), null, createRelationPayload);

		if (response.getStatus() == CREATED_STATUS) {
			relationCsid = CollectionSpaceClientUtils.extractId(response);
		}
		else {
			throw new ResourceException(response, "Error creating relation");
		}

		return relationCsid;
	}

	/**
	 * Return related records, based on the supplied search criteria. Soft-deleted relations
	 * are filtered from the list, but soft-deleted subject/object records are not.
	 * 
	 * @param subjectCsid		The csid of the subject record. If null or empty, match any subject.
	 * @param subjectDocType	The document type of the subject record. If null or empty, match any subject type.
	 * @param predicate			The predicate of the relation. If null or empty, match any predicate.
	 * @param objectCsid		The csid of the object record. If null or empty, match any object.
	 * @param objectDocType		The document type of the object record. If null or empty, match any object type.
	 * @return
	 * @throws URISyntaxException
	 */
	protected List<RelationListItem> findRelated(String subjectCsid, String subjectDocType, String predicate, String objectCsid, String objectDocType) throws URISyntaxException {
		RelationResource relationResource = (RelationResource) getResourceMap().get(RelationClient.SERVICE_NAME);
		RelationsCommonList relationList = relationResource.getList(createRelationSearchUriInfo(subjectCsid, subjectDocType, predicate, objectCsid, objectDocType));

		return relationList.getRelationListItem();
	}
	
	protected List<String> findRelatedObjects(String subjectCsid, String subjectDocType, String predicate, String objectCsid, String objectDocType) throws URISyntaxException {
		List<String> csids = new ArrayList<String>();

		for (RelationsCommonList.RelationListItem item : findRelated(subjectCsid, subjectDocType, predicate, objectCsid, objectDocType)) {
			csids.add(item.getObjectCsid());
		}
	
		return csids;
	}

	protected List<String> findRelatedSubjects(String subjectCsid, String subjectDocType, String predicate, String objectCsid, String objectDocType) throws URISyntaxException {
		List<String> csids = new ArrayList<String>();

		for (RelationsCommonList.RelationListItem item : findRelated(subjectCsid, subjectDocType, predicate, objectCsid, objectDocType)) {
			csids.add(item.getSubjectCsid());
		}
	
		return csids;
	}

	protected List<String> findRelatedCollectionObjects(String subjectCsid) throws URISyntaxException {
		return findRelatedObjects(subjectCsid, null, "affects", null, CollectionObjectConstants.NUXEO_DOCTYPE);
	}

	protected List<String> findRelatedMovements(String subjectCsid) throws URISyntaxException {
		return findRelatedObjects(subjectCsid, null, "affects", null, MovementConstants.NUXEO_DOCTYPE);
	}

	protected String findBroader(String subjectCsid) throws URISyntaxException {
		List<String> relatedObjects = findRelatedObjects(subjectCsid, null, "hasBroader", null, null);

		// There should be only one broader object.
		String broader = relatedObjects.size() > 0 ? relatedObjects.get(0) : null;
		
		return broader;
	}

	protected List<String> findNarrower(String subjectCsid) throws URISyntaxException {
		return findRelatedSubjects(null, null, "hasBroader", subjectCsid, null);
	}

	/**
	 * Returns the movement record related to the specified record, if there is only one.
	 * Returns null if there are zero or more than one related movement records.
	 * 
	 * @param subjectCsid	The csid of the record
	 * @return
	 * @throws URISyntaxException
	 * @throws DocumentException
	 */
	protected String findSingleRelatedMovement(String subjectCsid) throws URISyntaxException, DocumentException {
		String foundMovementCsid = null;
		List<String> movementCsids = findRelatedMovements(subjectCsid);
		
		for (String movementCsid : movementCsids) {
			PoxPayloadOut movementPayload = findMovementByCsid(movementCsid);
			String movementWorkflowState = getFieldValue(movementPayload, CollectionSpaceClient.COLLECTIONSPACE_CORE_SCHEMA, CollectionSpaceClient.COLLECTIONSPACE_CORE_WORKFLOWSTATE);
		
			if (!movementWorkflowState.equals(WorkflowClient.WORKFLOWSTATE_DELETED)) {
				if (foundMovementCsid != null) {
					return null;
				}
				
				foundMovementCsid = movementCsid;
			}
		}

		return foundMovementCsid;
	}

	protected PoxPayloadOut findByCsid(String serviceName, String csid) throws URISyntaxException, DocumentException {
		ResourceBase resource = getResourceMap().get(serviceName);
		byte[] response = resource.get(null, createUriInfo(), csid);

		PoxPayloadOut payload = new PoxPayloadOut(response);

		return payload;
	}

	protected PoxPayloadOut findCollectionObjectByCsid(String csid) throws URISyntaxException, DocumentException {
		return findByCsid(CollectionObjectClient.SERVICE_NAME, csid);
	}

	protected PoxPayloadOut findMovementByCsid(String csid) throws URISyntaxException, DocumentException {
		return findByCsid(MovementClient.SERVICE_NAME, csid);
	}
	
	protected List<String> findAll(String serviceName, int pageSize, int pageNum) throws URISyntaxException, DocumentException {
		return findAll(serviceName, pageSize, pageNum, null);
	}
	
	protected List<String> findAll(String serviceName, int pageSize, int pageNum, String sortBy) throws URISyntaxException, DocumentException {
		ResourceBase resource = getResourceMap().get(serviceName);	

		return findAll(resource, pageSize, pageNum, null);
	}
	
	protected List<String> findAll(ResourceBase resource, int pageSize, int pageNum, String sortBy) throws URISyntaxException, DocumentException {
		AbstractCommonList list = resource.getList(createPagedListUriInfo(resource.getServiceName(), pageNum, pageSize, sortBy));
		List<String> csids = new ArrayList<String>();

		if (list instanceof RelationsCommonList) {
			for (RelationListItem item : ((RelationsCommonList) list).getRelationListItem()) {
				csids.add(item.getCsid());
			}
		}
		else {
			for (AbstractCommonList.ListItem item : list.getListItem()) {
				for (org.w3c.dom.Element element : item.getAny()) {
					
					if (element.getTagName().equals("csid")) {
						csids.add(element.getTextContent());
						break;
					}
				}
			}
		}
		
		return csids;
	}	
	
	protected List<String> findAllCollectionObjects(int pageSize, int pageNum) throws URISyntaxException, DocumentException {
		return findAll(CollectionObjectClient.SERVICE_NAME, pageSize, pageNum);
	}
	
	protected List<String> getVocabularyCsids(String serviceName) throws URISyntaxException {
		AuthorityResource<?, ?> resource = (AuthorityResource<?, ?>) getResourceMap().get(serviceName);

		return getVocabularyCsids(resource);
	}

	protected List<String> getVocabularyCsids(AuthorityResource<?, ?> resource) throws URISyntaxException {
		AbstractCommonList vocabularyList = resource.getAuthorityList(createDeleteFilterUriInfo());
		List<String> csids = new ArrayList<String>();
		
		for (AbstractCommonList.ListItem item : vocabularyList.getListItem()) {
			for (org.w3c.dom.Element element : item.getAny()) {
				if (element.getTagName().equals("csid")) {
					csids.add(element.getTextContent());
					break;
				}
			}
		}

		return csids;
	}
	
	protected List<String> findAllAuthorityItems(String serviceName, String vocabularyCsid, int pageSize, int pageNum) throws URISyntaxException, DocumentException {
		return findAllAuthorityItems(serviceName, vocabularyCsid, pageSize, pageNum, null);
	}
	
	protected List<String> findAllAuthorityItems(String serviceName, String vocabularyCsid, int pageSize, int pageNum, String sortBy) throws URISyntaxException, DocumentException {
		AuthorityResource<?, ?> resource = (AuthorityResource<?, ?>) getResourceMap().get(serviceName);

		return findAllAuthorityItems(resource, vocabularyCsid, pageSize, pageNum, sortBy);
	}
	
	protected List<String> findAllAuthorityItems(AuthorityResource<?, ?> resource, String vocabularyCsid, int pageSize, int pageNum, String sortBy) throws URISyntaxException, DocumentException {
		AbstractCommonList list = resource.getAuthorityItemList(vocabularyCsid, createPagedListUriInfo(resource.getServiceName(), pageNum, pageSize, sortBy));
		List<String> csids = new ArrayList<String>();		
		
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
	
	protected PoxPayloadOut findAuthorityItemByCsid(String serviceName, String csid) throws URISyntaxException, DocumentException {
		List<String> vocabularyCsids = getVocabularyCsids(serviceName);
		PoxPayloadOut itemPayload = null;
		
		for (String vocabularyCsid : vocabularyCsids) {
			logger.debug("vocabularyCsid=" + vocabularyCsid);
			
			itemPayload = findAuthorityItemByCsid(serviceName, vocabularyCsid, csid);
			
			if (itemPayload != null) {
				break;
			}
		}
		
		return itemPayload;
	}
	
	protected PoxPayloadOut findAuthorityItemByCsid(String serviceName, String vocabularyCsid, String csid) throws URISyntaxException, DocumentException {
		AuthorityResource<?, ?> resource = (AuthorityResource<?, ?>) getResourceMap().get(serviceName);
		byte[] response = resource.getAuthorityItem(null, createDeleteFilterUriInfo(), getResourceMap(), vocabularyCsid, csid);
		 
		PoxPayloadOut payload = new PoxPayloadOut(response);

		return payload;
	}
	
	protected PoxPayloadOut findTaxonByCsid(String csid) throws URISyntaxException, DocumentException {
		return findAuthorityItemByCsid(TaxonomyAuthorityClient.SERVICE_NAME, csid);
	}
	
	protected PoxPayloadOut findAuthorityItemByShortId(String serviceName, String vocabularyShortId, String itemShortId) throws URISyntaxException, DocumentException {
		AuthorityResource<?, ?> resource = (AuthorityResource<?, ?>) getResourceMap().get(serviceName);
		byte[] response = resource.getAuthorityItem(null, createDeleteFilterUriInfo(), getResourceMap(), "urn:cspace:name(" + vocabularyShortId + ")", "urn:cspace:name(" + itemShortId + ")");
 
		PoxPayloadOut payload = new PoxPayloadOut(response);

		return payload;
	}
	
	protected PoxPayloadOut findAuthorityItemByRefName(String serviceName, String refName) throws URISyntaxException, DocumentException {
		RefName.AuthorityItem item = RefName.AuthorityItem.parse(refName);
		
		String vocabularyShortId = item.getParentShortIdentifier();
		String itemShortId = item.getShortIdentifier();
		
		return findAuthorityItemByShortId(serviceName, vocabularyShortId, itemShortId);
	}

	protected PoxPayloadOut findPlaceByRefName(String refName) throws URISyntaxException, DocumentException {
		return findAuthorityItemByRefName(PlaceAuthorityClient.SERVICE_NAME, refName);
	}
	
	protected PoxPayloadOut findTaxonByRefName(String refName) throws URISyntaxException, DocumentException {
		return findAuthorityItemByRefName(TaxonomyAuthorityClient.SERVICE_NAME, refName);
	}
	
	protected List<String> findReferencingObjects(String serviceName, String parentCsid, String csid, String type, String sourceField) throws URISyntaxException {
		logger.debug("findReferencingObjects serviceName=" + serviceName + " parentCsid=" + parentCsid + " csid=" + csid + " type=" + type + " sourceField=" + sourceField);

		AuthorityResource<?, ?> resource = (AuthorityResource<?, ?>) getResourceMap().get(serviceName);
        UriTemplateRegistry uriTemplateRegistry = ServiceMain.getInstance().getUriTemplateRegistry();
		AuthorityRefDocList refDocList = resource.getReferencingObjects(parentCsid, csid, uriTemplateRegistry, createRefSearchFilterUriInfo(type));
		List<String> csids = new ArrayList<String>();
		
		for (AuthorityRefDocList.AuthorityRefDocItem item : refDocList.getAuthorityRefDocItem()) {
			/*
			 *  If a multivalue field contains a reference to the object multiple times, the referencing object
			 *  seems to get returned multiple times in the list, but only the first has a non-null workflow state.
			 *  A bug? Handle this by discarding list items with a null workflow state.
			 */
			
			if (item.getWorkflowState() != null && !item.getWorkflowState().equals(WorkflowClient.WORKFLOWSTATE_DELETED) && (sourceField == null || item.getSourceField().equals(sourceField))) {
				csids.add(item.getDocId());
			}
		}

		return csids;
	}

	protected List<String> findReferencingObjects(String serviceName, String csid, String type, String sourceField) throws URISyntaxException, DocumentException {
		logger.debug("findReferencingObjects serviceName=" + serviceName + " csid=" + csid + " type=" + type + " sourceField=" + sourceField);

		List<String> vocabularyCsids = getVocabularyCsids(serviceName);
		String parentCsid = null;
		
		if (vocabularyCsids.size() == 1) {
			parentCsid = vocabularyCsids.get(0);
		}
		else {
			for (String vocabularyCsid : vocabularyCsids) {
				PoxPayloadOut itemPayload = findAuthorityItemByCsid(serviceName, vocabularyCsid, csid);
				
				if (itemPayload != null) {
					parentCsid = vocabularyCsid;
					break;
				}
			}
		}
		
		return findReferencingObjects(serviceName, parentCsid, csid, type, sourceField);
	}

	protected List<String> findReferencingCollectionObjects(String serviceName, String csid, String sourceField) throws URISyntaxException, DocumentException {
		return findReferencingObjects(serviceName, csid, ServiceBindingUtils.SERVICE_TYPE_OBJECT, sourceField);
	}

	protected List<String> findReferencingCollectionObjects(String serviceName, String vocabularyShortId, String csid, String sourceField) throws URISyntaxException, DocumentException {
		return findReferencingObjects(serviceName, "urn:cspace:name(" + vocabularyShortId + ")", csid, ServiceBindingUtils.SERVICE_TYPE_OBJECT, sourceField);
	}
	
	/**
	 * Create a stub UriInfo
	 * 
	 * @throws URISyntaxException 
	 */
	protected UriInfo createUriInfo() throws URISyntaxException {
		return createUriInfo("");
	}

	protected UriInfo createUriInfo(String queryString) throws URISyntaxException {
		return createUriInfo(queryString, Collections.<PathSegment> emptyList());
	}
	
	protected UriInfo createUriInfo(String queryString, List<PathSegment> pathSegments) throws URISyntaxException {
		queryString = escapeQueryString(queryString);
		
		URI	absolutePath = new URI("");
		URI	baseUri = new URI("");

		return new UriInfoImpl(absolutePath, baseUri, "", queryString, pathSegments);
	}
	
	protected UriInfo createDeleteFilterUriInfo() throws URISyntaxException {
		return createUriInfo("wf_deleted=false&pgSz=0");
	}

	protected UriInfo createKeywordSearchUriInfo(String schemaName, String fieldName, String value) throws URISyntaxException {
		return createUriInfo("kw=&as=( (" +schemaName + ":" + fieldName + " ILIKE \"" + value + "\") )&wf_deleted=false&pgSz=0");
	}

	protected UriInfo createRelationSearchUriInfo(String subjectCsid, String subjectDocType, String predicate, String objectCsid, String objectDocType) throws URISyntaxException {
		List<String> queryParams = new ArrayList<String>(6);
		
		if (StringUtils.isNotEmpty(subjectCsid)) {
			queryParams.add(IRelationsManager.SUBJECT_QP + "=" + subjectCsid);
		}

		if (StringUtils.isNotEmpty(subjectDocType)) {
			queryParams.add(IRelationsManager.SUBJECT_TYPE_QP + "=" + subjectDocType);
		}

		if (StringUtils.isNotEmpty(predicate)) {
			queryParams.add(IRelationsManager.PREDICATE_QP + "=" + predicate);
		}

		if (StringUtils.isNotEmpty(objectCsid)) {
			queryParams.add(IRelationsManager.OBJECT_QP + "=" + objectCsid);
		}
		
		if (StringUtils.isNotEmpty(objectDocType)) {
			queryParams.add(IRelationsManager.OBJECT_TYPE_QP + "=" + objectDocType);
		}
				
		queryParams.add("wf_deleted=false");
		queryParams.add("pgSz=0");
		
		return createUriInfo(StringUtils.join(queryParams, "&"));
	}
	
	protected UriInfo createRefSearchFilterUriInfo(String type) throws URISyntaxException {
		return createUriInfo("type=" + type + "&wf_deleted=false&pgSz=0");
	}

	protected UriInfo createPagedListUriInfo(String serviceName, int pageNum, int pageSize) throws URISyntaxException {
		return createPagedListUriInfo(serviceName, pageNum, pageSize, null);
	}
	
	protected UriInfo createPagedListUriInfo(String serviceName, int pageNum, int pageSize, String sortBy) throws URISyntaxException {
		List<PathSegment> pathSegments = new ArrayList<PathSegment>(1);
		pathSegments.add(new PathSegmentImpl(serviceName, false));
		
		return createUriInfo("pgSz=" + pageSize + "&pgNum=" + pageNum + (sortBy != null ? "&sortBy=" + sortBy : "") + "&wf_deleted=false", pathSegments);
	}

	protected String escapeQueryString(String queryString) throws URISyntaxException {
		URI uri =  new URI(null, null, null, queryString, null);

		return uri.getRawQuery();
	}
	
	/**
	 * Get a field value from a PoxPayloadOut, given a part name and xpath expression.
	 */
	protected String getFieldValue(PoxPayloadOut payload, String partLabel, String fieldPath) {
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

	protected boolean getBooleanFieldValue(PoxPayloadOut payload, String partLabel, String fieldPath) {
		String value = getFieldValue(payload, partLabel, fieldPath);
		
		return (value != null && value.equals("true"));
	}
	
	protected List<String> getFieldValues(PoxPayloadOut payload, String partLabel, String fieldPath) {
		List<String> values = new ArrayList<String>();
		PayloadOutputPart part = payload.getPart(partLabel);

		if (part != null) {
			Element element = part.asElement();
			List<Node> nodes = element.selectNodes(fieldPath);

			if (nodes != null) {
				for (Node node : nodes) {
					values.add(node.getText());
				}
			}
		}

		return values;
	}
	
	protected String getDisplayNameFromRefName(String refName) {
		RefName.AuthorityItem item = RefName.AuthorityItem.parse(refName);

		return (item == null ? refName : item.displayName);
	}
	
	protected String getCsid(PoxPayloadOut payload) {
		String uri = getFieldValue(payload, CollectionSpaceClient.COLLECTIONSPACE_CORE_SCHEMA, CollectionSpaceClient.COLLECTIONSPACE_CORE_URI);
		String[] elements = StringUtils.split(uri, '/');
		String csid = elements[elements.length - 1];
		
		return csid;
	}
	
	protected class ResourceException extends Exception {
		private static final long serialVersionUID = 1L;

		private Response response;
		
		public ResourceException(Response response, String message) {
			super(message);
			this.setResponse(response);
		}

		public Response getResponse() {
			return response;
		}

		public void setResponse(Response response) {
			this.response = response;
		}
	}
}
