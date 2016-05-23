/**
 *  This document is a part of the source code and related artifacts
 *  for CollectionSpace, an open source collections management system
 *  for museums and related institutions:

 *  http://www.collectionspace.org
 *  http://wiki.collectionspace.org

 *  Copyright 2009 University of California at Berkeley

 *  Licensed under the Educational Community License (ECL), Version 2.0.
 *  You may not use this file except in compliance with this License.

 *  You may obtain a copy of the ECL 2.0 License at

 *  https://source.collectionspace.org/collection-space/LICENSE.txt

 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.collectionspace.services.common.vocabulary.nuxeo;

import java.util.Map;

import org.collectionspace.services.client.PoxPayloadIn;
import org.collectionspace.services.client.PoxPayloadOut;
import org.collectionspace.services.common.api.RefName;
import org.collectionspace.services.common.api.RefName.Authority;
import org.collectionspace.services.common.api.Tools;
import org.collectionspace.services.common.context.ServiceContext;
import org.collectionspace.services.common.document.DocumentException;
import org.collectionspace.services.common.document.DocumentNotFoundException;
import org.collectionspace.services.common.document.DocumentWrapper;
import org.collectionspace.services.common.vocabulary.AuthorityJAXBSchema;
import org.collectionspace.services.config.service.ObjectPartType;
import org.collectionspace.services.nuxeo.client.java.NuxeoDocumentModelHandler;
import org.collectionspace.services.nuxeo.client.java.CoreSessionInterface;
import org.collectionspace.services.nuxeo.client.java.RepositoryJavaClientImpl;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AuthorityDocumentModelHandler
 *
 * $LastChangedRevision: $
 * $LastChangedDate: $
 */
public abstract class AuthorityDocumentModelHandler<AuthCommon>
        extends NuxeoDocumentModelHandler<AuthCommon> {

    private final Logger logger = LoggerFactory.getLogger(AuthorityDocumentModelHandler.class);	
    private String authorityCommonSchemaName;

    public AuthorityDocumentModelHandler(String authorityCommonSchemaName) {
        this.authorityCommonSchemaName = authorityCommonSchemaName;
    }

    /*
     * Non standard injection of CSID into common part, since caller may access through
     * shortId, and not know the CSID.
     * @see org.collectionspace.services.nuxeo.client.java.RemoteDocumentModelHandlerImpl#extractPart(org.nuxeo.ecm.core.api.DocumentModel, java.lang.String, org.collectionspace.services.common.service.ObjectPartType)
     */
    @Override
    protected Map<String, Object> extractPart(DocumentModel docModel, String schema, ObjectPartType partMeta)
            throws Exception {
        Map<String, Object> unQObjectProperties = super.extractPart(docModel, schema, partMeta);

        // Add the CSID to the common part
        if (partMeta.getLabel().equalsIgnoreCase(authorityCommonSchemaName)) {
            String csid = getCsid(docModel);//NuxeoUtils.extractId(docModel.getPathAsString());
            unQObjectProperties.put("csid", csid);
        }

        return unQObjectProperties;
    }

    @Override
    public void handleCreate(DocumentWrapper<DocumentModel> wrapDoc) throws Exception {
        super.handleCreate(wrapDoc);
        // CSPACE-3178:
        // Uncomment once debugged and App layer is read to integrate
        // Experimenting with this uncommented now ...
        handleDisplayNameAsShortIdentifier(wrapDoc.getWrappedObject(), authorityCommonSchemaName);
        updateRefnameForAuthority(wrapDoc, authorityCommonSchemaName);//CSPACE-3178
    }
    
    protected String buildWhereForShortId(String name) {
        return authorityCommonSchemaName
                + ":" + AuthorityJAXBSchema.SHORT_IDENTIFIER
                + "='" + name + "'";
    }
    
    private boolean isUnique(DocumentModel docModel, String schemaName) throws DocumentException {
    	return true;
    }
    
    private boolean temp_isUnique(DocumentModel docModel, String schemaName) throws DocumentException {
    	boolean result = true;
    	
    	ServiceContext ctx = this.getServiceContext();
        String shortIdentifier = (String) docModel.getProperty(schemaName, AuthorityJAXBSchema.SHORT_IDENTIFIER);
    	String nxqlWhereClause = buildWhereForShortId(shortIdentifier);
    	try {
			DocumentWrapper<DocumentModel> searchResultWrapper = getRepositoryClient(ctx).findDoc(ctx, nxqlWhereClause);
			if (searchResultWrapper != null) {
				result = false;
				if (logger.isInfoEnabled() == true) {
					DocumentModel searchResult = searchResultWrapper.getWrappedObject();
					String debugMsg = String.format("Could not create a new authority with a short identifier of '%s', because one already exists with the same short identifer: CSID = '%s'",
							shortIdentifier, searchResult.getName());
					logger.trace(debugMsg);
				}
			}
		} catch (DocumentNotFoundException e) {
			// Not a problem, just means we couldn't find another authority with that short ID
		}
    	
    	return result;
    }

    /**
     * If no short identifier was provided in the input payload,
     * generate a short identifier from the display name. Either way though,
     * the short identifier needs to be unique.
     */
    private void handleDisplayNameAsShortIdentifier(DocumentModel docModel, String schemaName) throws Exception {
        String shortIdentifier = (String) docModel.getProperty(schemaName, AuthorityJAXBSchema.SHORT_IDENTIFIER);
        String displayName = (String) docModel.getProperty(schemaName, AuthorityJAXBSchema.DISPLAY_NAME);
        String shortDisplayName = "";
        String generateShortIdentifier = null;
        if (Tools.isEmpty(shortIdentifier)) {
        	generateShortIdentifier = AuthorityIdentifierUtils.generateShortIdentifierFromDisplayName(displayName, shortDisplayName);
            docModel.setProperty(schemaName, AuthorityJAXBSchema.SHORT_IDENTIFIER, shortIdentifier);
        }
        
        if (isUnique(docModel, schemaName) == false) {
        	String shortId = generateShortIdentifier == null ? shortIdentifier : generateShortIdentifier;
        	String errMsgVerb = generateShortIdentifier == null ? "supplied" : "generated";
        	String errMsg = String.format("The %s short identifier '%s' was not unique, so the new authority could not be created.",
        			errMsgVerb, shortId);
        	throw new DocumentException(errMsg);
        }
    }
 
    /**
     * Generate a refName for the authority from the short identifier
     * and display name.
     * 
     * All refNames for authorities are generated.  If a client supplies
     * a refName, it will be overwritten during create (per this method) 
     * or discarded during update (per filterReadOnlyPropertiesForPart).
     * 
     * @see #filterReadOnlyPropertiesForPart(Map<String, Object>, org.collectionspace.services.common.service.ObjectPartType)
     * 
     */
    protected void updateRefnameForAuthority(DocumentWrapper<DocumentModel> wrapDoc, String schemaName) throws Exception {
        DocumentModel docModel = wrapDoc.getWrappedObject();
        RefName.Authority authority = (Authority) getRefName(getServiceContext(), docModel);
        String refName = authority.toString();
        docModel.setProperty(schemaName, AuthorityJAXBSchema.REF_NAME, refName);
    }
    
    @Override
    public RefName.RefNameInterface getRefName(ServiceContext ctx,
    		DocumentModel docModel) {
    	RefName.RefNameInterface refname = null;

    	try {
	        String shortIdentifier = (String) docModel.getProperty(authorityCommonSchemaName, AuthorityJAXBSchema.SHORT_IDENTIFIER);
	        String displayName = (String) docModel.getProperty(authorityCommonSchemaName, AuthorityJAXBSchema.DISPLAY_NAME);
	        RefName.Authority authority = RefName.Authority.buildAuthority(ctx.getTenantName(),
	                ctx.getServiceName(),
	                null,	// Only use shortId form!!!
	                shortIdentifier,
	                displayName);
	        refname = authority;
    	} catch (Exception e) {
    		logger.error(e.getMessage(), e);
    	}
    	
    	return refname;
    }
    
    @Override
    protected String getRefnameDisplayName(DocumentWrapper<DocumentModel> docWrapper) {
    	String result = null;
    	
    	DocumentModel docModel = docWrapper.getWrappedObject();
    	ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx = this.getServiceContext();
    	RefName.Authority refname = (RefName.Authority)getRefName(ctx, docModel);
    	result = refname.getDisplayName();
    	
    	return result;
    }    
    
    public String getShortIdentifier(String authCSID, String schemaName) throws Exception {
        String shortIdentifier = null;
        CoreSessionInterface repoSession = null;

        ServiceContext<PoxPayloadIn, PoxPayloadOut> ctx = this.getServiceContext();
    	RepositoryJavaClientImpl nuxeoRepoClient = (RepositoryJavaClientImpl)this.getRepositoryClient(ctx);
        try {
        	repoSession = nuxeoRepoClient.getRepositorySession(ctx);
            DocumentWrapper<DocumentModel> wrapDoc = nuxeoRepoClient.getDocFromCsid(ctx, repoSession, authCSID);
            DocumentModel docModel = wrapDoc.getWrappedObject();
            shortIdentifier = (String) docModel.getProperty(schemaName, AuthorityJAXBSchema.SHORT_IDENTIFIER);
        } catch (ClientException ce) {
            throw new RuntimeException("AuthorityDocHandler Internal Error: cannot get shortId!", ce);
        } finally {
        	if (repoSession != null) {
        		nuxeoRepoClient.releaseRepositorySession(ctx, repoSession);
        	}
        }
        
        return shortIdentifier;
    }

    /**
     * Filters out selected values supplied in an update request.
     * 
     * @param objectProps the properties filtered out from the update payload
     * @param partMeta metadata for the object to fill
     */
    @Override
    public void filterReadOnlyPropertiesForPart(
            Map<String, Object> objectProps, ObjectPartType partMeta) {
        super.filterReadOnlyPropertiesForPart(objectProps, partMeta);
        String commonPartLabel = getServiceContext().getCommonPartLabel();
        if (partMeta.getLabel().equalsIgnoreCase(commonPartLabel)) {
            objectProps.remove(AuthorityJAXBSchema.CSID);
            objectProps.remove(AuthorityJAXBSchema.SHORT_IDENTIFIER);
            objectProps.remove(AuthorityJAXBSchema.REF_NAME);
        }
    }    
}
