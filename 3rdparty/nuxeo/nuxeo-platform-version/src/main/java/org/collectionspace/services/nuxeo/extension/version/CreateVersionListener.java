package org.collectionspace.services.nuxeo.extension.version;

import java.util.UUID;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

public class CreateVersionListener implements EventListener {
 
    public void handleEvent(Event event) throws ClientException {
        EventContext ec = event.getContext();
        if (ec instanceof DocumentEventContext) {
            DocumentEventContext context = (DocumentEventContext) ec;
            DocumentModel doc = context.getSourceDocument();
            System.out.println("CreateVersionListener: " + doc.getType() + " " + doc.isVersion() + " " + doc.isProxy());
            if (doc.getType().startsWith("Movement") && !doc.isVersion() && !doc.isProxy()) {
            	//System.out.println("addVersion: " + context.getProperties().get("addVersion"));
            	
            	System.out.println("Versioning " + doc.getType() + " " + doc.getName() + " " + doc.getId());

            	String csid = doc.getName();
            	
            	// Temporarily change the csid, so that the version we create will have a unique csid
            	String newCsid = UUID.randomUUID().toString();
            	context.getCoreSession().move(doc.getRef(), doc.getParentRef(), newCsid);
            	
            	// Version the document
            	DocumentRef versionRef = doc.checkIn(VersioningOption.NONE, null);            	
            	DocumentModel versionDoc = context.getCoreSession().getDocument(versionRef);
            	System.out.println("Version: " + versionDoc.getId());
            	           	
            	// Delete the version (if it's not already deleted), so search doesn't find it
            	if (versionDoc.getAllowedStateTransitions().contains("delete")) {
            		context.getCoreSession().followTransition(versionRef, "delete");
            	}
            	
            	// Check out the document, so it can be modified
            	doc.checkOut();
            	
            	// Reset the csid
            	context.getCoreSession().move(doc.getRef(), doc.getParentRef(), csid);	
            }
        }
    }
}
