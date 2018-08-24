package org.collectionspace.services.listener;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

public class ReindexSupport implements EventListener {
    private static final String PREV_COVERAGE_KEY = "Reindex.PREV_COVERAGE";

    @Override
    public void handleEvent(Event event) {
        DocumentEventContext eventContext = (DocumentEventContext) event.getContext();
        DocumentModel doc = eventContext.getSourceDocument();
        String docType = doc.getType();
        String eventName = event.getName();

        if (docType.equals("Media")) {
            if (eventName.equals(DocumentEventTypes.BEFORE_DOC_UPDATE)) {
                DocumentModel previousDoc = (DocumentModel) eventContext.getProperty(CoreEventConstants.PREVIOUS_DOCUMENT_MODEL);
                String refName = (String) previousDoc.getProperty("media_common", "coverage");

                eventContext.setProperty(PREV_COVERAGE_KEY, refName);
            }
            else if (eventName.equals(DocumentEventTypes.ABOUT_TO_REMOVE)) {
                String refName = (String) doc.getProperty("media_common", "coverage");

                eventContext.setProperty(PREV_COVERAGE_KEY, refName);
            }
        }
    }
}
