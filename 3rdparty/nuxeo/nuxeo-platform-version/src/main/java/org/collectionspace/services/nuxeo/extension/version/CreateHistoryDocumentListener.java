package org.collectionspace.services.nuxeo.extension.version;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.collectionspace.services.client.CollectionSpaceClientUtils;
import org.collectionspace.services.client.MovementClient;
import org.collectionspace.services.client.PayloadOutputPart;
import org.collectionspace.services.client.PoxPayloadOut;
import org.collectionspace.services.client.RelationClient;
import org.collectionspace.services.common.ResourceBase;
import org.collectionspace.services.common.ResourceMap;
import org.collectionspace.services.common.invocable.Invocable.InvocationError;
import org.collectionspace.services.jaxb.AbstractCommonList;
import org.collectionspace.services.relation.RelationResource;
import org.collectionspace.services.relation.RelationsCommonList;
import org.dom4j.DocumentException;
import org.dom4j.tree.DefaultElement;
import org.dom4j.tree.FlyweightText;
import org.jboss.resteasy.specimpl.UriInfoImpl;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

public class CreateHistoryDocumentListener implements EventListener {
	protected final int CREATED_STATUS = Response.Status.CREATED.getStatusCode();
	protected final int BAD_REQUEST_STATUS = Response.Status.BAD_REQUEST.getStatusCode();
	protected final int INT_ERROR_STATUS = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();

    public void handleEvent(Event event) throws ClientException {
        EventContext ec = event.getContext();
        if (ec instanceof DocumentEventContext) {
            DocumentEventContext context = (DocumentEventContext) ec;
            DocumentModel doc = context.getSourceDocument();
            System.out.println("CreateHistoryDocumentListener: " + doc.getType());
            if (doc.getType().startsWith("Movement")) {
            	// if not deleted - a deleted doc is its own history
            	String state = context.getCoreSession().getCurrentLifeCycleState(doc.getRef());
            	System.out.println("Movement state: " + state);
            	
            	if (!state.equals("deleted")) {
            	
            	//doc.getProperty(arg0, arg1);
            	
            	//if (true) {
            		System.out.println("Creating history: " + doc.getType() + " " + doc.getName());

            		String csid = doc.getName();
            		createHistoryDocument(csid);
            	}
            }
        }
    }
    
    private void createHistoryDocument(String movementCsid) {
    	ResourceMap resourceMap = ResteasyProviderFactory.getContextData(ResourceMap.class);
		ResourceBase resource = resourceMap.get(MovementClient.SERVICE_NAME);
		byte[] response = resource.get(createUriInfo(), movementCsid);

		System.out.println(new String(response));
		
		PoxPayloadOut payload = null;		

		try {
			payload = new PoxPayloadOut(response);
		} catch (DocumentException e) {
			System.out.println(e.getMessage());
		}
		
		System.out.println("payload name: " + payload.getName());

		for (PayloadOutputPart part : payload.getParts()) {
			System.out.println("part: " + part.getLabel());
		}
		
		PoxPayloadOut copyPayload = new PoxPayloadOut("movements");
		copyPayload.addPart("movements_common", payload.getPart("movements_common").asElement());
		copyPayload.addPart("movements_naturalhistory", payload.getPart("movements_naturalhistory").asElement());
		
		org.dom4j.Element isHistoryElement = new DefaultElement("isHistory");
		isHistoryElement.add(new FlyweightText("true"));
		copyPayload.getPart("movements_naturalhistory").asElement().add(isHistoryElement);
		
		System.out.println("===copy===");
		System.out.println(copyPayload.toXML());
		System.out.println("==========");

//		Response createResponse = resource.create(resourceMap, null, copyPayload.toXML());
//
//		if(createResponse.getStatus() != CREATED_STATUS) {
//			System.out.println("Create failed");
//		} else {
//			String newId = CollectionSpaceClientUtils.extractId(createResponse);
//			System.out.println("Created copy: " + newId);
//		}
		
		RelationResource relationResource = (RelationResource) resourceMap.get(RelationClient.SERVICE_NAME);
		RelationsCommonList relationList = relationResource.getList(createRelationSearchUriInfo(movementCsid));
		
		System.out.println("relations: " + relationList.getTotalItems());
		System.out.println("items: " + relationList.getRelationListItem().size());
		
		for (RelationsCommonList.RelationListItem item : relationList.getRelationListItem()) {
			System.out.println("type: " + item.getRelationshipType() + " object: " + item.getObjectCsid());
		}
		
		
    }
  
	/**
	 * Create a stub UriInfo
	 */
	private UriInfo createUriInfo() {
		return createUriInfo("");
	}

	private UriInfo createUriInfo(String queryString) {
		URI absolutePath = null;
		URI baseUri = null;

		try {
			absolutePath = new URI("");
			baseUri = new URI("");
		} catch (URISyntaxException e) {
			System.out.println(e.getMessage());
		}

		return new UriInfoImpl(absolutePath, baseUri, "", queryString, Collections.EMPTY_LIST);
	}

	private UriInfo createRelationSearchUriInfo(String subjectCsid) {
		String queryString = "sbj=" + subjectCsid + "&objType=CollectionObject";

		URI uri = null;

		try {
			uri = new URI(null, null, null, queryString, null);
		} catch (URISyntaxException e) {
			System.out.println(e.getMessage());
		}

		System.out.println("relation search query: " + uri.getRawQuery());

		return createUriInfo(uri.getRawQuery());		
	}
}
