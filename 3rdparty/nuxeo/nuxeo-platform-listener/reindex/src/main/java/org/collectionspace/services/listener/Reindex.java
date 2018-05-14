package org.collectionspace.services.listener;

import java.util.Iterator;

import org.collectionspace.services.nuxeo.listener.AbstractCSEventListenerImpl;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.elasticsearch.ElasticSearchComponent;
import org.nuxeo.elasticsearch.api.ElasticSearchService;
import org.nuxeo.elasticsearch.commands.IndexingCommand;
import org.nuxeo.runtime.api.Framework;

public class Reindex extends AbstractCSEventListenerImpl {

    @Override
    public void handleEvent(Event event) {
        // FIXME: Why does this seem to be blocking the save thread? It's supposed to be
        // async and post-commit.
 
        DocumentModel doc = ((DocumentEventContext) event.getContext()).getSourceDocument();
        String docType = doc.getType();
                
        if (docType.equals("PlaceitemTenant5000")) {
            String refName = (String) doc.getProperty("places_common", "refName");
            String escapedRefName = refName.replace("'", "\\'");
            String query = String.format("SELECT * FROM CollectionObjectTenant5000 WHERE collectionobjects_common:computedCurrentLocation = '%s'", escapedRefName);

            CoreSession session = doc.getCoreSession();
            DocumentModelList collectionObjectDocs  = session.query(query);
            
            ElasticSearchComponent es = (ElasticSearchComponent) Framework.getService(ElasticSearchService.class);

            if (collectionObjectDocs.size() > 0) {
                // FIXME: This won't be acceptable for large search results.
                // This event handler has to run async, which for some reason it's not.
                // Also try using runReindexingWorker, commented out below.
                
                Iterator<DocumentModel> iterator =  collectionObjectDocs.iterator();
                
                while (iterator.hasNext()) {
                    DocumentModel collectionObjectDoc = iterator.next();
                    IndexingCommand command = new IndexingCommand(collectionObjectDoc, IndexingCommand.Type.UPDATE, false, false);
                    
                    es.indexNonRecursive(command);
                }
            }

//            ElasticSearchComponent es = (ElasticSearchComponent) Framework.getService(ElasticSearchService.class);
//            es.runReindexingWorker(doc.getRepositoryName(), query);
        }
    }
}
