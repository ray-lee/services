package org.collectionspace.services.listener;

import java.io.Serializable;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

public class ReindexSupport implements EventListener {

    @Override
    public void handleEvent(Event event) {
        DocumentEventContext eventContext = (DocumentEventContext) event.getContext();
        DocumentModel doc = eventContext.getSourceDocument();
        String docType = doc.getType();
        String eventName = event.getName();

        if (docType.startsWith("Media")) {
            if (eventName.equals(DocumentEventTypes.BEFORE_DOC_UPDATE)) {
                DocumentModel previousDoc = (DocumentModel) eventContext.getProperty(CoreEventConstants.PREVIOUS_DOCUMENT_MODEL);
                String coverage = (String) previousDoc.getProperty("media_common", "coverage");
                List<String> publishTo = (List<String>) previousDoc.getProperty("media_materials", "publishToList");

                eventContext.setProperty(Reindex.PREV_COVERAGE_KEY, coverage);
                eventContext.setProperty(Reindex.PREV_PUBLISH_TO_KEY, (Serializable) publishTo);
            }
            else if (eventName.equals(DocumentEventTypes.ABOUT_TO_REMOVE)) {
                String coverage = (String) doc.getProperty("media_common", "coverage");

                eventContext.setProperty(Reindex.PREV_COVERAGE_KEY, coverage);
            }
        }
    }
}
