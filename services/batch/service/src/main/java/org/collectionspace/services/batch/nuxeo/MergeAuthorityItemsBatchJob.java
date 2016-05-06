package org.collectionspace.services.batch.nuxeo;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.collectionspace.services.client.PayloadOutputPart;
import org.collectionspace.services.client.PoxPayloadOut;
import org.collectionspace.services.common.ResourceBase;
import org.collectionspace.services.common.invocable.InvocationContext.Params.Param;
import org.collectionspace.services.common.invocable.InvocationResults;
import org.collectionspace.services.common.vocabulary.AuthorityResource;
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

	public InvocationResults merge(String docType, String targetCsid, List<String> sourceCsids) throws Exception {
		String serviceName = getAuthorityServiceNameForDocType(docType);
		
		PoxPayloadOut targetItemPayload = findAuthorityItemByCsid(serviceName, targetCsid);
		List<PoxPayloadOut> sourceItemPayloads = new ArrayList<PoxPayloadOut>();

		for (String sourceCsid : sourceCsids) {
			sourceItemPayloads.add(findAuthorityItemByCsid(serviceName, sourceCsid));
		}

		return merge(docType, targetItemPayload, sourceItemPayloads);
	}
	
	public InvocationResults merge(String docType, String targetCsid, String sourceCsid) throws URISyntaxException, DocumentException {
		String serviceName = getAuthorityServiceNameForDocType(docType);

		if (serviceName == null) {
			throw new IllegalArgumentException("unrecognized authority docType " + docType);
		}
		
		PoxPayloadOut targetItemPayload = findAuthorityItemByCsid(serviceName, targetCsid);
		PoxPayloadOut sourceItemPayload = findAuthorityItemByCsid(serviceName, sourceCsid);

		return merge(docType, targetItemPayload, sourceItemPayload);
	}
	
	private InvocationResults merge(String docType, PoxPayloadOut targetItemPayload, List<PoxPayloadOut> sourceItemPayloads) throws URISyntaxException {
		int numAffected = 0;
		List<String> userNotes = new ArrayList<String>();

		for (PoxPayloadOut sourceItemPayload : sourceItemPayloads) {
			InvocationResults results = merge(docType, targetItemPayload, sourceItemPayload);
			numAffected += results.getNumAffected();
			userNotes.add(results.getUserNote());
		}

		InvocationResults results = new InvocationResults();
		results.setNumAffected(numAffected);
		results.setUserNote(StringUtils.join(userNotes, "\n"));

		return results;
	}
	
	private InvocationResults merge(String docType, PoxPayloadOut targetItemPayload, PoxPayloadOut sourceItemPayload) throws URISyntaxException {
		List<Element> mergedTermGroups = new ArrayList<Element>();
		
		Map<String, Element> targetTermGroups = getTermGroups(targetItemPayload);
		Map<String, Element> sourceTermGroups = getTermGroups(sourceItemPayload);
		
		// Copy target term groups, merging in any source term groups that have
		// a matching display name.

		for (String displayName : targetTermGroups.keySet()) {	
			Element targetTermGroupElement = targetTermGroups.get(displayName);
			Element mergedTermGroupElement = null;
			
			if (sourceTermGroups.containsKey(displayName)) {
				Element sourceTermGroupElement = sourceTermGroups.get(displayName);
				
				try {
					mergedTermGroupElement = mergeTermGroups(targetTermGroupElement, sourceTermGroupElement);
				}
				catch(Exception e) {
					logger.error(e.getMessage(), e);
				}
				
				sourceTermGroups.remove(displayName);
			}
			else {
				mergedTermGroupElement = targetTermGroupElement.createCopy();
			}
			
			if (mergedTermGroupElement != null) {
				mergedTermGroups.add(mergedTermGroupElement);
			}
		}
		
		// Copy any remaining source term groups.
		
		for (Element sourceTermGroupElement : sourceTermGroups.values()) {
			mergedTermGroups.add(sourceTermGroupElement.createCopy());
		}
		
		replaceTermGroups(targetItemPayload, mergedTermGroups);
		
		String serviceName = getAuthorityServiceNameForDocType(docType);
		AuthorityResource<?, ?> resource = (AuthorityResource<?, ?>) getResourceMap().get(serviceName);
		
		resource.updateAuthorityItem(getResourceMap(), createUriInfo(), getFieldValue(targetItemPayload, "inAuthority"), getCsid(targetItemPayload), targetItemPayload.getXmlPayload());
		
		InvocationResults results = new InvocationResults();
		
		return results;
	}
	
	private void replaceTermGroups(PoxPayloadOut itemPayload, List<Element> newTermGroupElements) {
		Element termGroupListElement = getTermGroupListElement(itemPayload);
		
		for (Element termGroupElement : (List<Element>) termGroupListElement.elements()) {
			termGroupListElement.remove(termGroupElement);
		}
		
		for (Element termGroupElement : newTermGroupElements) {
			termGroupListElement.add(termGroupElement);
		}
	}
	
	/**
	 * @param Returns a map of the term groups in an authority item, keyed by display name.
	 *        If multiple groups have the same display name, they are merged into one
	 *        by calling mergeTermGroups with the term group appearing first as the target.
	 * @return The term groups.
	 */
	private Map<String, Element> getTermGroups(PoxPayloadOut itemPayload) {
		Map<String, Element> termGroups = new LinkedHashMap<String, Element>();
		Element termGroupListElement = getTermGroupListElement(itemPayload);
		Iterator<Element> childIterator = termGroupListElement.elementIterator();
		
		while (childIterator.hasNext()) {
			Element termGroupElement = childIterator.next();
			String displayName = getDisplayName(termGroupElement);
			
			if (termGroups.containsKey(displayName)) {
				// Two term groups in the same item have identical display names.
				
				throw new RuntimeException("item with csid " + getCsid(itemPayload) + " has multiple terms with display name " + displayName);
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
	
	private Element mergeTermGroups(Element targetTermGroupElement, Element sourceTermGroupElement) throws Exception {
		Element mergedTermGroupElement = targetTermGroupElement.createCopy();
		
		// Assuming there are no nested repeating groups.
	
		for (Element sourceChildElement : (List<Element>) sourceTermGroupElement.elements()) {
			String sourceValue = sourceChildElement.getText();
			
			if (sourceValue == null) {
				sourceValue = "";
			}
			
			if (sourceValue.length() > 0) {
				String name = sourceChildElement.getName();
				Element targetChildElement = mergedTermGroupElement.element(name);
				
				if (targetChildElement != null) {
					String targetValue = targetChildElement.getText();
					
					if (targetValue == null) {
						targetValue = "";
					}
					
					if (!targetValue.equals(sourceValue)) {
						if (targetValue.length() > 0) {
							throw new Exception("merge conflict in " + name + ": could not merge value " + sourceValue + " with existing value " + targetValue);
						}
						
						mergedTermGroupElement.remove(targetChildElement);
						mergedTermGroupElement.add(sourceChildElement.createCopy());
					}
				}
			}
		}
		
		return mergedTermGroupElement;
	}
}