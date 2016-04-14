package org.collectionspace.services.batch.nuxeo;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.collectionspace.services.client.PoxPayloadOut;
import org.collectionspace.services.common.invocable.InvocationResults;
import org.dom4j.DocumentException;
import org.nuxeo.common.utils.StringUtils;

/**
 * A batch job that merges authority items. The single CSID context is supported.
 *
 * The context CSID (singleCSID element of the batch invocation payload) specifies the target of the merge.
 * This is the record into which one or more source items will be merged.
 * 
 * The following parameters are required:
 * 
 * sourceCSID
 *
 * @author ray
 */
public class MergeAuthorityItemsBatchJob extends AbstractBatchJob {

    public MergeAuthorityItemsBatchJob() {
        setSupportedInvocationModes(Arrays.asList(INVOCATION_MODE_SINGLE));
    }
    
    @Override
    public void run() {
        // TODO Auto-generated method stub
        // targetItem
        // sourceItems

        setCompletionStatus(STATUS_MIN_PROGRESS);
        
        try {
            if (this.requestIsForInvocationModeSingle()) {
                String csid = getInvocationContext().getSingleCSID();
                
                setCompletionStatus(STATUS_COMPLETE);
            }
            else {
                throw new Exception("Unsupported invocation mode: " + this.getInvocationContext().getMode());
            }
        }
        catch(Exception e) {
            setCompletionStatus(STATUS_ERROR);
            setErrorInfo(new InvocationError(INT_ERROR_STATUS, e.getMessage()));
        }
    }
    
    public InvocationResults merge(String docType, String targetCsid, List<String> sourceCsids) throws URISyntaxException, DocumentException {
        int numAffected = 0;
        List<String> userNotes = new ArrayList<String>();
        
        for (String sourceCsid : sourceCsids) {
            InvocationResults results = merge(docType, targetCsid, sourceCsid);
            numAffected += results.getNumAffected();
            userNotes.add(results.getUserNote());
        }
        
        InvocationResults results = new InvocationResults();
        results.setNumAffected(numAffected);
        results.setUserNote(StringUtils.join(userNotes, "\n"));
        
        return results;
    }
    
    public InvocationResults merge(String docType, String targetCsid, String sourceCsid) throws URISyntaxException, DocumentException {
        PoxPayloadOut targetItemPayload = findAuthorityItemByCsid(docType, targetCsid);
        PoxPayloadOut sourceItemPayload = findAuthorityItemByCsid(docType, sourceCsid);
        
        
        InvocationResults results = new InvocationResults();
        
        return results;
    }
}