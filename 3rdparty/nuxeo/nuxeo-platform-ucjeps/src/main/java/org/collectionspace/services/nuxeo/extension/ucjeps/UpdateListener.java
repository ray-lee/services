package org.collectionspace.services.nuxeo.extension.ucjeps;

import org.collectionspace.services.batch.nuxeo.CreateCatalogFromMedia;
import org.collectionspace.services.common.ResourceMap;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

public class UpdateListener implements EventListener {
 
    public void handleEvent(Event event) throws ClientException {
        EventContext ec = event.getContext();

        if (ec instanceof DocumentEventContext) {
            DocumentEventContext context = (DocumentEventContext) ec;
            DocumentModel doc = context.getSourceDocument();
            
            if (DocumentEventTypes.DOCUMENT_CREATED.equals(event.getName())) {
            	String type = doc.getType();
            	String csid = doc.getName();
            	
            	System.out.println("handleEvent: " + type + " " + csid);
            	
            	if (type.startsWith("Media")) {
            		System.out.println("creating cataloging record from media record");
            		
            		ResourceMap resourceMap = ResteasyProviderFactory.getContextData(ResourceMap.class);
            		
            		CreateCatalogFromMedia creator = new CreateCatalogFromMedia();
            		creator.setResourceMap(resourceMap);
            		creator.createCatalogFromMedia(csid);
            	}
            }
        }
    }
}
