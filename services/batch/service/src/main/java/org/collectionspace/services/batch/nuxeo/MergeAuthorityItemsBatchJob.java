package org.collectionspace.services.batch.nuxeo;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.collectionspace.services.client.PayloadOutputPart;
import org.collectionspace.services.client.PoxPayloadOut;
import org.collectionspace.services.client.workflow.WorkflowClient;
import org.collectionspace.services.common.api.RefNameUtils;
import org.collectionspace.services.common.api.RefNameUtils.AuthorityTermInfo;
import org.collectionspace.services.common.authorityref.AuthorityRefDocList;
import org.collectionspace.services.common.invocable.InvocationContext.Params.Param;
import org.collectionspace.services.common.invocable.InvocationResults;
import org.collectionspace.services.common.vocabulary.AuthorityResource;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.nuxeo.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A batch job that merges authority items. The single and list contexts are
 * supported.
 *
 * The merge target is a record into which one or more source records will be
 * merged. A merge source is a record that will be merged into the target, as
 * follows: Each term in a source record is added to the target as a non-
 * preferred term, if that term does not already exist in the target. If a term
 * in the source already exists in the target, each non-blank term field is
 * copied to the target, if that field is empty in the target. If the field is
 * non-empty in the target, and differs from the source field, a warning is
 * emitted and no action is taken. If a source is successfully merged into the
 * target, the source record is soft-deleted, and all references to the source
 * are transferred to the target.
 * 
 * The context (singleCSID or listCSIDs of the batch invocation payload
 * specifies the source record(s).
 * 
 * The following parameters are allowed:
 * 
 * targetCSID: The csid of the target record. Only one target may be supplied.
 *
 * @author ray
 */
public class MergeAuthorityItemsBatchJob extends AbstractBatchJob {
	final Logger log = LoggerFactory.getLogger(MergeAuthorityItemsBatchJob.class);

	public MergeAuthorityItemsBatchJob() {
		setSupportedInvocationModes(Arrays.asList(INVOCATION_MODE_SINGLE, INVOCATION_MODE_LIST));
	}

	@Override
	public void run() {
		setCompletionStatus(STATUS_MIN_PROGRESS);

		try {
			String docType = getInvocationContext().getDocType();

			if (docType == null || docType.equals("")) {
				throw new Exception("a docType must be supplied");
			}
			
			String targetCsid = null;

			for (Param param : this.getParams()) {
				if (param.getKey().equals("targetCSID")) {
					targetCsid = param.getValue();
					break;
				}
			}

			if (targetCsid == null || targetCsid.equals("")) {
				throw new Exception("a target csid parameter (targetCSID) must be supplied");
			}

			List<String> sourceCsids;
			
			if (this.requestIsForInvocationModeSingle()) {
				sourceCsids = Arrays.asList(getInvocationContext().getSingleCSID());
			}
			else if (this.requestIsForInvocationModeList()) {
				sourceCsids = getInvocationContext().getListCSIDs().getCsid();
			}
			else {
				throw new Exception("unsupported invocation mode: " + this.getInvocationContext().getMode());
			}
			
			if (sourceCsids.size() == 0) {
				throw new Exception("a source csid must be supplied");
			}
			
			InvocationResults results = merge(docType, targetCsid, sourceCsids);

			setResults(results);
			setCompletionStatus(STATUS_COMPLETE);
		}
		catch (Exception e) {
			setCompletionStatus(STATUS_ERROR);
			setErrorInfo(new InvocationError(INT_ERROR_STATUS, e.getMessage()));
		}
	}

	public InvocationResults merge(String docType, String targetCsid, String sourceCsid) throws URISyntaxException, DocumentException {
		return merge(docType, targetCsid, Arrays.asList(sourceCsid));
	}
	
	public InvocationResults merge(String docType, String targetCsid, List<String> sourceCsids) throws URISyntaxException, DocumentException {
		String serviceName = getAuthorityServiceNameForDocType(docType);
		
		PoxPayloadOut targetItemPayload = findAuthorityItemByCsid(serviceName, targetCsid);
		List<PoxPayloadOut> sourceItemPayloads = new ArrayList<PoxPayloadOut>();

		for (String sourceCsid : sourceCsids) {
			sourceItemPayloads.add(findAuthorityItemByCsid(serviceName, sourceCsid));
		}

		return merge(docType, targetItemPayload, sourceItemPayloads);
	}
	
	private InvocationResults merge(String docType, PoxPayloadOut targetItemPayload, List<PoxPayloadOut> sourceItemPayloads) throws URISyntaxException, DocumentException {
		int numAffected = 0;
		List<String> userNotes = new ArrayList<String>();

		Element targetTermGroupListElement = getTermGroupListElement(targetItemPayload);
		Element mergedTermGroupListElement = targetTermGroupListElement.createCopy();

		String targetCsid = getCsid(targetItemPayload);
		String targetRefName = getRefName(targetItemPayload);
		String inAuthority = getFieldValue(targetItemPayload, "inAuthority");
			
		for (PoxPayloadOut sourceItemPayload : sourceItemPayloads) {
			String sourceCsid = getCsid(sourceItemPayload);
			Element sourceTermGroupListElement = getTermGroupListElement(sourceItemPayload);
			
			try {
				mergeTermGroupLists(mergedTermGroupListElement, sourceTermGroupListElement);
			}
			catch(RuntimeException e) {
				throw new RuntimeException("Error merging source record " + sourceCsid + " into target record " + targetCsid + ": " + e.getMessage(), e);
			}
		}
		
		updateAuthorityItem(docType, inAuthority, targetCsid, getUpdatePayload(mergedTermGroupListElement));
		
		userNotes.add("Updated target record " + targetCsid + " (" + targetRefName + ")");
		numAffected++;
		
		String serviceName = getAuthorityServiceNameForDocType(docType);

		for (PoxPayloadOut sourceItemPayload : sourceItemPayloads) {
			String sourceCsid = getCsid(sourceItemPayload);
			String sourceRefName = getRefName(sourceItemPayload);
			
			updateReferences(serviceName, inAuthority, sourceCsid, sourceRefName, targetRefName);
		}
		
		for (PoxPayloadOut sourceItemPayload : sourceItemPayloads) {
			String sourceCsid = getCsid(sourceItemPayload);
			String sourceRefName = getRefName(sourceItemPayload);

			deleteAuthorityItem(docType, getFieldValue(targetItemPayload, "inAuthority"), getCsid(targetItemPayload), getUpdatePayload(mergedTermGroupListElement));
			
			userNotes.add("Deleted source record " + sourceCsid + " (" + sourceRefName + ")");
			numAffected++;
		}
		
		InvocationResults results = new InvocationResults();
		results.setNumAffected(numAffected);
		results.setUserNote(StringUtils.join(userNotes, "\n"));

		return results;
	}
	
	private void updateReferences(String serviceName, String inAuthority, String sourceCsid, String sourceRefName, String targetRefName) throws URISyntaxException, DocumentException {
		int pageNum = 0;
		int pageSize = 100;
		List<AuthorityRefDocList.AuthorityRefDocItem> items;
		
		do {
			// The pageNum/pageSize parameters don't work properly for refobj requests!
			// It should be safe to repeatedly fetch page 0 for a large-ish page size,
			// and update that page, until no references are left.
			
			items = findReferencingFields(serviceName, inAuthority, sourceCsid, null, pageNum, pageSize);
			Map<String, ReferencingRecord> referencingRecordsByCsid = new LinkedHashMap<String, ReferencingRecord>();
			
			for (AuthorityRefDocList.AuthorityRefDocItem item : items) {
				// If a record contains a reference to the record multiple times, multiple items are returned,
				// but only the first has a non-null workflow state. A bug?
				
				String itemCsid = item.getDocId();
				ReferencingRecord record = referencingRecordsByCsid.get(itemCsid);
				
				if (record == null) {
					if (item.getWorkflowState() != null && !item.getWorkflowState().equals(WorkflowClient.WORKFLOWSTATE_DELETED)) {
						record = new ReferencingRecord(item.getUri());
						referencingRecordsByCsid.put(itemCsid, record);
					}
				}
				
				if (record != null) {
					String[] sourceFieldElements = item.getSourceField().split(":");
					String partName = sourceFieldElements[0];
					String fieldName = sourceFieldElements[1];
					
					Map<String, Set<String>> fields = record.getFields();
					Set<String> fieldsInPart = fields.get(partName);
							
					if (fieldsInPart == null) {
						fieldsInPart = new HashSet<String>();
						fields.put(partName, fieldsInPart);
					}
					
					fieldsInPart.add(fieldName);
				}
			}
			
			List<ReferencingRecord> referencingRecords = new ArrayList<ReferencingRecord>(referencingRecordsByCsid.values());
			
			for (ReferencingRecord record : referencingRecords) {
				updateReferencingRecord(record, sourceRefName, targetRefName);
			}
			
			pageNum++;
		}
		//TODO: retrieve more pages
		//while (items.size() > 0);
		while (false);
	}
	
	private void updateReferencingRecord(ReferencingRecord record, String fromRefName, String toRefName) throws URISyntaxException, DocumentException {
		String fromRefNameStem = RefNameUtils.stripAuthorityTermDisplayName(fromRefName);
		String toRefNameStem = RefNameUtils.stripAuthorityTermDisplayName(toRefName);
		
		Map<String, Set<String>> fields = record.getFields();
		
		PoxPayloadOut recordPayload = findByUri(record.getUri());
		Document recordDocument = recordPayload.getDOMDocument();
		Document newDocument = (Document) recordDocument.clone();
		Element rootElement = newDocument.getRootElement();
		
		for (Element partElement : (List<Element>) rootElement.elements()) {
			String partName = partElement.getName();
			
			if (fields.containsKey(partName)) {
				for (String fieldName : fields.get(partName)) {
					List<Node> nodes = partElement.selectNodes("descendant::" + fieldName);
					
					for (Node node : nodes) {
						String text = node.getText();
						String refNameStem = null;
						
						try {
							refNameStem = RefNameUtils.stripAuthorityTermDisplayName(text);
						}
						catch(IllegalArgumentException e) {}

						if (refNameStem != null && refNameStem.equals(fromRefNameStem)) {
							AuthorityTermInfo termInfo = RefNameUtils.parseAuthorityTermInfo(text);
							String newRefName = toRefNameStem + "'" + termInfo.displayName + "'";
							
							node.setText(newRefName);
						}
					}
				}
			}
			else {
				rootElement.remove(partElement);
			}
		}
		
		String xml = newDocument.asXML();
		// TODO: PUT the update!
	}
	
	private void updateAuthorityItem(String docType, String inAuthority, String csid, String payload) throws URISyntaxException {
		String serviceName = getAuthorityServiceNameForDocType(docType);
		AuthorityResource<?, ?> resource = (AuthorityResource<?, ?>) getResourceMap().get(serviceName);
		
		resource.updateAuthorityItem(getResourceMap(), createUriInfo(), inAuthority, csid, payload);
	}
	
	private void deleteAuthorityItem(String docType, String inAuthority, String csid, String payload) throws URISyntaxException {
	}
	
	/**
	 * @param Returns a map of the term groups in term group list, keyed by display name.
	 *        If multiple groups have the same display name, an exception is thrown.
	 * @return The term groups.
	 */
	private Map<String, Element> getTermGroups(Element termGroupListElement) {
		Map<String, Element> termGroups = new LinkedHashMap<String, Element>();
		Iterator<Element> childIterator = termGroupListElement.elementIterator();
		
		while (childIterator.hasNext()) {
			Element termGroupElement = childIterator.next();
			String displayName = getDisplayName(termGroupElement);
			
			if (termGroups.containsKey(displayName)) {
				// Two term groups in the same item have identical display names.
				
				throw new RuntimeException("multiple terms have display name \"" + displayName + "\"");
			}
			else {
				termGroups.put(displayName, termGroupElement);
			}
		}
		
		return termGroups;
	}
	
	private String getDisplayName(Element termGroupElement) {
		Node displayNameNode = termGroupElement.selectSingleNode("termDisplayName");
		String displayName = (displayNameNode == null) ? "" : displayNameNode.getText();
		
		return displayName;
	}
	
	private Element getTermGroupListElement(PoxPayloadOut itemPayload) {
		Element termGroupListElement = null;
		Element commonPartElement = findCommonPartElement(itemPayload);
				
		if (commonPartElement != null) {
			termGroupListElement = findTermGroupListElement(commonPartElement);
		}
		
		return termGroupListElement;
	}
	
	private Element findCommonPartElement(PoxPayloadOut itemPayload) {
		Element commonPartElement = null;
		
		for (PayloadOutputPart candidatePart : itemPayload.getParts()) {
			Element candidatePartElement = candidatePart.asElement();
			
			if (candidatePartElement.getName().endsWith("_common")) {
				commonPartElement = candidatePartElement;
				break;
			}
		}

		return commonPartElement;
	}
	
	private Element findTermGroupListElement(Element contextElement) {
		Element termGroupListElement = null;
		Iterator<Element> childIterator = contextElement.elementIterator();
		
		while (childIterator.hasNext()) {
			Element candidateElement = childIterator.next();
			
			if (candidateElement.getName().endsWith("TermGroupList")) {
				termGroupListElement = candidateElement;
				break;
			}
		}
		
		return termGroupListElement;
	}
	
	private void mergeTermGroupLists(Element targetTermGroupListElement, Element sourceTermGroupListElement) {
		Map<String, Element> sourceTermGroups;
		
		try {
			sourceTermGroups = getTermGroups(sourceTermGroupListElement);
		}
		catch(RuntimeException e) {
			throw new RuntimeException("a problem was found in the source record: " + e.getMessage(), e);
		}
		
		for (Element targetTermGroupElement : (List<Element>) targetTermGroupListElement.elements()) {
			String displayName = getDisplayName(targetTermGroupElement);
			
			if (sourceTermGroups.containsKey(displayName)) {
				try {
					mergeTermGroups(targetTermGroupElement, sourceTermGroups.get(displayName));
				}
				catch(RuntimeException e) {
					throw new RuntimeException("could not merge term groups with display name \"" + displayName + "\": " + e.getMessage(), e);
				}
					
				sourceTermGroups.remove(displayName);
			}
		}
		
		for (Element sourceTermGroupElement : sourceTermGroups.values()) {
			targetTermGroupListElement.add(sourceTermGroupElement.createCopy());
		}
	}
	
	private void mergeTermGroups(Element targetTermGroupElement, Element sourceTermGroupElement) {
		// This function assumes there are no nested repeating groups.
	
		for (Element sourceChildElement : (List<Element>) sourceTermGroupElement.elements()) {
			String sourceValue = sourceChildElement.getText();
			
			if (sourceValue == null) {
				sourceValue = "";
			}
			
			if (sourceValue.length() > 0) {
				String name = sourceChildElement.getName();
				Element targetChildElement = targetTermGroupElement.element(name);
				
				if (targetChildElement == null) {
					targetTermGroupElement.add(sourceChildElement.createCopy());
				}
				else {
					String targetValue = targetChildElement.getText();
					
					if (targetValue == null) {
						targetValue = "";
					}
					
					if (!targetValue.equals(sourceValue)) {
						if (targetValue.length() > 0) {
							throw new RuntimeException("merge conflict in field " + name + ": source value \"" + sourceValue + "\" differs from target value \"" + targetValue +"\"");
						}
						
						targetTermGroupElement.remove(targetChildElement);
						targetTermGroupElement.add(sourceChildElement.createCopy());
					}
				}
			}
		}
	}
	
	private String getUpdatePayload(Element termGroupListElement) {
		String payload =
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
			"<document name=\"persons\">" +
				"<ns2:persons_common xmlns:ns2=\"http://collectionspace.org/services/person\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
				termGroupListElement.asXML() +
				"</ns2:persons_common>" +
			"</document>";

		return payload;
	}
	
	private class ReferencingRecord {
		private String uri;
		private Map<String, Set<String>> fields; 
		
		public ReferencingRecord(String uri) {
			this.uri = uri;
			this.fields = new HashMap<String, Set<String>>();
		}

		public String getUri() {
			return uri;
		}

		public void setUri(String uri) {
			this.uri = uri;
		}

		public Map<String, Set<String>> getFields() {
			return fields;
		}
	}
}