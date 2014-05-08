package org.collectionspace.services.batch.nuxeo;

import java.util.Arrays;

import org.collectionspace.services.common.invocable.InvocationResults;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.reload.ReloadService;

public class FlushCacheBatchJob extends AbstractBatchJob {

	public FlushCacheBatchJob() {
		setSupportedInvocationModes(Arrays.asList(INVOCATION_MODE_NO_CONTEXT));
	}
	
	@Override
	public void run() {
		InvocationResults results = new InvocationResults();
		setCompletionStatus(STATUS_MIN_PROGRESS);
		
		ReloadService reloadService = Framework.getLocalService(ReloadService.class);
		
		try {
			reloadService.flush();
		
			results.setUserNote("Caches flushed");
			setCompletionStatus(STATUS_COMPLETE);
		}
		catch(Exception e) {
			setErrorResult(e.getMessage());
		}
	}
}
