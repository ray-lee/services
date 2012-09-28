package org.collectionspace.services.nuxeo.extension.version;

import java.io.Serializable;
import java.util.Map;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

public class MovementUpdateListener implements EventListener {
 
    public void handleEvent(Event event) throws ClientException {
        EventContext ec = event.getContext();
        if (ec instanceof DocumentEventContext) {
            DocumentEventContext context = (DocumentEventContext) ec;
            DocumentModel doc = context.getSourceDocument();
            //System.out.println("MovementUpdateListener: " + doc.getType() + " " + doc.isDirty());
            if (doc.getType().startsWith("Movement")) {
            	//Map<String, Serializable> properties = context.getProperties();
            	
            	//System.out.println("context keys:");
            	//for (String key : properties.keySet()) {
            	//	System.out.println("  " + key);
            	//}
            	
            	String newLocation = (String) doc.getProperty("movements_common", "currentLocation");
            	System.out.println("new location: " + newLocation);
            	
            	DocumentModel oldDoc = (DocumentModel) context.getProperty("previousDocumentModel");	            	
        		String oldLocation = (String) oldDoc.getProperty("movements_common", "currentLocation");
        		System.out.println("old location:" + oldLocation);
            	
            	//properties.put("addVersion", true);
            	//context.setProperties(properties);
        		
        		//Change the csid - this works
        		//context.getCoreSession().move(doc.getRef(), doc.getParentRef(), "foobar");
            }
        }
    }
}