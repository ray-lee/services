package org.collectionspace.services.nuxeo.extension.botgarden;

import java.net.URISyntaxException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.collectionspace.services.batch.nuxeo.AbstractBatchJob;
import org.collectionspace.services.client.LoanoutClient;
import org.collectionspace.services.client.OrgAuthorityClient;
import org.collectionspace.services.client.PersonAuthorityClient;
import org.collectionspace.services.client.PoxPayloadOut;
import org.collectionspace.services.client.workflow.WorkflowClient;
import org.collectionspace.services.common.ResourceBase;
import org.collectionspace.services.common.ResourceMap;
import org.collectionspace.services.loanout.nuxeo.LoanoutConstants;
import org.collectionspace.services.organization.nuxeo.OrganizationConstants;
import org.collectionspace.services.person.nuxeo.PersonConstants;
import org.dom4j.DocumentException;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

/**
 * An example event handler that updates fields on a record by pulling values from referenced authority items.
 * In this case, the note field on loanout records is set to a value based on its borrower (an organization),
 * and the borrower's contact (a person).
 */
public class UpdateDenormalizedFieldsListener implements EventListener {
	final Log logger = LogFactory.getLog(UpdateDenormalizedFieldsListener.class);
	
	public static final String PREVIOUS_BORROWER_PROPERTY_NAME = "UpdateDenormalizedFieldsListener.previousBorrower";

	@Override
	public void handleEvent(Event event) throws ClientException {
		EventContext ec = event.getContext();
		
		if (ec instanceof DocumentEventContext) {
			DocumentEventContext context = (DocumentEventContext) ec;
			DocumentModel doc = context.getSourceDocument();

			logger.debug("docType=" + doc.getType());

			// Check if this is a loanout document.
			if (doc.getType().startsWith(LoanoutConstants.NUXEO_DOCTYPE) &&
					!doc.isVersion() && 
					!doc.isProxy() && 
					!doc.getCurrentLifeCycleState().equals(WorkflowClient.WORKFLOWSTATE_DELETED)) {

				if (event.getName().equals(DocumentEventTypes.BEFORE_DOC_UPDATE)) {
					// In the beforeDocumentModification event, stash the previous value of the borrower field,
					// so it can be retrieved in the documentModified event.
					
					DocumentModel previousDoc = (DocumentModel) context.getProperty(CoreEventConstants.PREVIOUS_DOCUMENT_MODEL);

					String previousBorrower = (String) previousDoc.getProperty(LoanoutConstants.BORROWER_SCHEMA_NAME, LoanoutConstants.BORROWER_FIELD_NAME);
					context.setProperty(PREVIOUS_BORROWER_PROPERTY_NAME, previousBorrower);				
				}
				else {
					boolean updateRequired = false;
					
					if (event.getName().equals(DocumentEventTypes.DOCUMENT_UPDATED)) {
						// In the documentModified event, check if the borrower changed. If it did, the denormalized fields need to be updated.
						// Otherwise, the fields do not need to be updated. This is both an optimization to avoid doing unnecessary work,
						// and a stop condition against infinite looping (when the fields are updated by this event handler, documentModified
						// fires again). 
						
						String previousBorrower = (String) context.getProperty(PREVIOUS_BORROWER_PROPERTY_NAME); 
						String currentBorrower = (String) doc.getProperty(LoanoutConstants.BORROWER_SCHEMA_NAME, LoanoutConstants.BORROWER_FIELD_NAME);
						
						if (currentBorrower.equals(previousBorrower)) {
							logger.debug("borrower unchanged, skipping update");
						}
						else {
							logger.debug("borrower changed: previousBorrower=" + previousBorrower + " currentBorrower=" + currentBorrower);
							updateRequired = true;
						}
					}
					else if (event.getName().equals(DocumentEventTypes.DOCUMENT_CREATED)) {
						// When a document is created, the denormalized fields should be updated.
						updateRequired = true;
					}
					
					if (updateRequired) {
						try {
							createUpdater().updateFields(doc.getName());				
						}
						catch (Exception e) {
							logger.debug(e.getMessage(), e);
						}
					}
				}
			}
		}
	}
	
	private UpdateDenormalizedFieldsBatchJob createUpdater() {
		ResourceMap resourceMap = ResteasyProviderFactory.getContextData(ResourceMap.class);

		UpdateDenormalizedFieldsBatchJob updater = new UpdateDenormalizedFieldsBatchJob();
		updater.setResourceMap(resourceMap);

		return updater;
	}
	
	private class UpdateDenormalizedFieldsBatchJob extends AbstractBatchJob {
		public void run() {
			// Not actually running this as a batch job.
		}
		
		/**
		 * Calculates and updates the note field of a loanout record with the display name of its borrower (org),
		 * concatenated with the display name of the borrower's contact (person).
		 * 
		 * @param loanoutCsid			The csid of the loanout record to update.
		 * @throws URISyntaxException
		 * @throws DocumentException
		 */
		public void updateFields(String loanoutCsid) throws URISyntaxException, DocumentException {
			PoxPayloadOut loanoutPayload = findByCsid(LoanoutClient.SERVICE_NAME, loanoutCsid);
			String orgRefName = getFieldValue(loanoutPayload, LoanoutConstants.BORROWER_SCHEMA_NAME, LoanoutConstants.BORROWER_FIELD_NAME);
			
			String borrowerDisplayName = "";
			String borrowerContactDisplayName = "";
			
			if (StringUtils.isNotBlank(orgRefName)) {
				PoxPayloadOut orgPayload = findAuthorityItemByRefName(OrgAuthorityClient.SERVICE_NAME, orgRefName);
				
				if (orgPayload != null) {
					borrowerDisplayName = getFieldValue(orgPayload, OrganizationConstants.DISPLAY_NAME_SCHEMA_NAME, OrganizationConstants.DISPLAY_NAME_FIELD_NAME);			
					List<String> contactRefNames = getFieldValues(orgPayload, OrganizationConstants.CONTACT_SCHEMA_NAME, OrganizationConstants.CONTACT_FIELD_NAME);
					
					logger.debug("contactRefNames=" + StringUtils.join(contactRefNames, ','));
					
					if (contactRefNames.size() > 0) {
						PoxPayloadOut personPayload = findAuthorityItemByRefName(PersonAuthorityClient.SERVICE_NAME, contactRefNames.get(0));
		
						if (personPayload != null) {
							borrowerContactDisplayName = getFieldValue(personPayload, PersonConstants.DISPLAY_NAME_SCHEMA_NAME, PersonConstants.DISPLAY_NAME_FIELD_NAME);
						}
					}
				}
			}
			
			String note = borrowerDisplayName + " / " + borrowerContactDisplayName;
			
			setNote(loanoutCsid, note);
		}
		
		/**
		 * Sets the note field of a loanout record to the specified value.
		 * @param loanOutCsid			The csid of the loanout.
		 * @param value					The new value of the note field.
		 * @throws URISyntaxException
		 */
		private void setNote(String loanOutCsid, String value) throws URISyntaxException {
			String updatePayload = 
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
					"<document name=\"loansout\">" +
						"<ns2:loansout_common xmlns:ns2=\"http://collectionspace.org/services/loanout\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
							this.getFieldXml(LoanoutConstants.NOTE_FIELD_NAME, value) +
						"</ns2:loansout_common>" +
					"</document>";

			ResourceBase resource = getResourceMap().get(LoanoutClient.SERVICE_NAME);
			resource.update(getResourceMap(), createUriInfo(), loanOutCsid, updatePayload);
		}
	}
}
