package org.collectionspace.services.nuxeo.extension.version;

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

            	DocumentRef versionRef = doc.checkIn(VersioningOption.NONE, null);            	
            	DocumentModel versionDoc = context.getCoreSession().getDocument(versionRef);
            	System.out.println("Version: " + versionDoc.getId());
            	
            	context.getCoreSession().followTransition(versionRef, "delete");
            	doc.checkOut();
            }
        }
    }
}
