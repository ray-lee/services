package org.collectionspace.services.listener;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.LifeCycleConstants;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.elasticsearch.ElasticSearchComponent;
import org.nuxeo.elasticsearch.api.ElasticSearchService;
import org.nuxeo.runtime.api.Framework;

public class Reindex implements PostCommitEventListener {
    public static final String PREV_COVERAGE_KEY = "Reindex.PREV_COVERAGE";
    public static final String PREV_PUBLISH_TO_KEY = "Reindex.PREV_PUBLISH_TO";

    @Override
    public void handleEvent(EventBundle events) {
        if (events.size() > 0) {
            Iterator<Event> iter = events.iterator();

            while (iter.hasNext()) {
                Event event = iter.next();
                DocumentEventContext eventContext = (DocumentEventContext) event.getContext();
                DocumentModel doc = eventContext.getSourceDocument();
                String docType = doc.getType();
                String eventName = event.getName();

                if (docType.startsWith("Media")) {
                    if (
                        eventName.equals(DocumentEventTypes.DOCUMENT_CREATED) ||
                        eventName.equals(DocumentEventTypes.DOCUMENT_UPDATED)
                    ) {
                        String prevCoverage = (String) eventContext.getProperty(PREV_COVERAGE_KEY);
                        String coverage = (String) doc.getProperty("media_common", "coverage");

                        List<String> prevPublishTo = (List<String>) eventContext.getProperty(PREV_PUBLISH_TO_KEY);
                        List<String> publishTo = (List<String>) doc.getProperty("media_materials", "publishToList");

                        if (doc.getCurrentLifeCycleState().equals(LifeCycleConstants.DELETED_STATE)) {
                            reindex(doc.getRepositoryName(), coverage);
                        }
                        else if (
                            !ListUtils.isEqualList(prevPublishTo, publishTo) ||
                            !StringUtils.equals(prevCoverage, coverage)
                        ) {
                            if (!StringUtils.equals(prevCoverage, coverage)) {
                                reindex(doc.getRepositoryName(), prevCoverage);
                            }

                            reindex(doc.getRepositoryName(), coverage);
                        }
                    }
                    else if (eventName.equals(DocumentEventTypes.DOCUMENT_REMOVED)) {
                        String prevCoverage = (String) eventContext.getProperty(PREV_COVERAGE_KEY);

                        reindex(doc.getRepositoryName(), prevCoverage);
                    }
                }
            }
        }
    }

    // @Override
    // public void handleEvent(Event event) {
    //     DocumentEventContext eventContext = (DocumentEventContext) event.getContext();
    //     DocumentModel doc = eventContext.getSourceDocument();
    //     String docType = doc.getType();
    //     String eventName = event.getName();

    //     if (docType.equals("Media")) {
    //         if (eventName.equals(DocumentEventTypes.BEFORE_DOC_UPDATE)) {
    //             DocumentModel previousDoc = (DocumentModel) eventContext.getProperty(CoreEventConstants.PREVIOUS_DOCUMENT_MODEL);
    //             String refName = (String) previousDoc.getProperty("media_common", "coverage");

    //             eventContext.setProperty(PREV_COVERAGE_KEY, refName);
    //         }
            // else if (eventName.equals(DocumentEventTypes.ABOUT_TO_REMOVE)) {
            //     String refName = (String) doc.getProperty("media_common", "coverage");

            //     eventContext.setProperty(PREV_COVERAGE_KEY, refName);
            // }
            // else if (
            //     eventName.equals(DocumentEventTypes.DOCUMENT_CREATED) ||
            //     eventName.equals(DocumentEventTypes.DOCUMENT_UPDATED)
            // ) {
            //     String prevRefName = (String) eventContext.getProperty(PREV_COVERAGE_KEY);
            //     String refName = (String) doc.getProperty("media_common", "coverage");

            //     if (doc.getCurrentLifeCycleState().equals(LifeCycleConstants.DELETED_STATE)) {
            //         reindex(doc.getCoreSession(), refName);
            //     }
            //     else if (!StringUtils.equals(prevRefName, refName)) {
            //         reindex(doc.getCoreSession(), prevRefName);
            //         reindex(doc.getCoreSession(), refName);
            //     }
            // }

            // else if (eventName.equals(DocumentEventTypes.DOCUMENT_REMOVED)) {
            //     String prevRefName = (String) eventContext.getProperty(PREV_COVERAGE_KEY);

            //     reindex(doc.getCoreSession(), prevRefName);
            // }
    //     }
    // }

    private void reindex(String repositoryName, String coverage) {
        if (StringUtils.isEmpty(coverage)) {
            return;
        }

        String escapedRefName = coverage.replace("'", "\\'");
        String query = String.format("SELECT ecm:uuid FROM Materialitem WHERE collectionspace_core:refName = '%s'", escapedRefName);

        ElasticSearchComponent es = (ElasticSearchComponent) Framework.getService(ElasticSearchService.class);
        es.runReindexingWorker(repositoryName, query);

        // DocumentModelList resultDocs  = session.query(query);

        // if (resultDocs.size() > 0) {
        //     // FIXME: This won't be acceptable for large search results.
        //     // This event handler has to run async, which for some reason it's not.
        //     // Also try using runReindexingWorker, commented out below.

        //     // Commit the current transaction so the ES document writer will see the changes.

        //     boolean isTransactionActive = TransactionHelper.isTransactionActive();

        //     if (isTransactionActive) {
        //         TransactionHelper.commitOrRollbackTransaction();
        //         TransactionHelper.startTransaction();
        //     }

        //     ElasticSearchComponent es = (ElasticSearchComponent) Framework.getService(ElasticSearchService.class);
        //     Iterator<DocumentModel> iterator =  resultDocs.iterator();

        //     while (iterator.hasNext()) {
        //         DocumentModel resultDoc = iterator.next();
        //         IndexingCommand command = new IndexingCommand(resultDoc, IndexingCommand.Type.UPDATE, false, false);

        //         es.indexNonRecursive(command);
        //     }
        // }

        // FIXME: Below is the proper way to do it -- async. But this event handler is running
        // synchronously and pre-commit, apparently, so there's a race condition where the worker
        // thread that does the reindexing won't see the updated media record. I think this can be
        // fixed by making this event handler run post-commit (and preferably async), which I think
        // it's not because it extends EventListener instead of PostCommitEventListener.

        // es.runReindexingWorker(doc.getRepositoryName(), query);
    }
}
