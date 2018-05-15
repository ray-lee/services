package org.collectionspace.services.nuxeo.elasticsearch;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.HttpHeaders;

import org.apache.chemistry.opencmis.commons.enums.CmisVersion;
import org.apache.chemistry.opencmis.commons.server.CallContext;
import org.apache.chemistry.opencmis.server.impl.CallContextImpl;
import org.apache.chemistry.opencmis.server.shared.ThresholdOutputStreamFactory;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonGenerator;
import org.nuxeo.ecm.automation.jaxrs.io.documents.JsonESDocumentWriter;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.ecm.core.opencmis.bindings.NuxeoCmisServiceFactory;
import org.nuxeo.ecm.core.opencmis.impl.server.NuxeoCmisService;
import org.nuxeo.runtime.api.Framework;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;

public class CSJsonESDocumentWriter extends JsonESDocumentWriter {
    private static ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void writeDoc(JsonGenerator jg, DocumentModel doc, String[] schemas,
            Map<String, String> contextParameters, HttpHeaders headers)
            throws IOException {

        ObjectNode denormValues = objectMapper.createObjectNode();
        
        String docType = doc.getType();

        if (docType.equals("CollectionObjectTenant5000")) {
            CoreSession session = doc.getCoreSession();
            
            String refName = (String) doc.getProperty("collectionobjects_common", "computedCurrentLocation");

            if (StringUtils.isNotEmpty(refName)) {
                String escapedRefName = refName.replace("'", "\\'");
                String placeQuery = String.format("SELECT * FROM PlaceitemTenant5000 WHERE places_common:refName = '%s'", escapedRefName);
    
                DocumentModelList placeDocs = session.query(placeQuery, 1);
    
                if (placeDocs.size() > 0) {
                    DocumentModel placeDoc = placeDocs.get(0);
                    
                    String placementType = (String) placeDoc.getProperty("places_publicart:placementType").getValue();
                    
                    if (placementType != null) {
                        denormValues.put("placementType", placementType);
                    }
                    
                    Property geoRefGroup;
                    
                    try {
                        geoRefGroup = placeDoc.getProperty("places_common:placeGeoRefGroupList/0");
                    } catch (PropertyNotFoundException e) {
                        geoRefGroup = null;
                    }
                    
                    if (geoRefGroup != null) {
                        Double decimalLatitude = (Double) geoRefGroup.getValue("decimalLatitude");
                        Double decimalLongitude = (Double) geoRefGroup.getValue("decimalLongitude");
        
                        if (decimalLatitude != null && decimalLongitude != null) {
                            ObjectNode geoPointNode = objectMapper.createObjectNode();
                            
                            geoPointNode.put("lat", decimalLatitude);
                            geoPointNode.put("lon", decimalLongitude);
                            
                            denormValues.put("geoPoint", geoPointNode);
                        }
                    }
                }
            }
            
            String uri = (String) doc.getProperty("collectionspace_core", "uri");
            
            if (uri != null) {
                String csid = uri.substring(uri.lastIndexOf('/') + 1);
                
                NuxeoCmisService cmisService = new NuxeoCmisService(session);
                
                cmisService.setCallContext(getCallContext(session));
                
                String mediaQuery = String.format("SELECT Media.media_common:blobCsid, Media.media_common:title FROM Relation JOIN Media ON Media.cmis:name = Relation.relations_common:objectCsid WHERE Relation.relations_common:subjectCsid = '%s' AND Relation.relations_common:objectDocumentType = 'Media' ORDER BY Media.media_common:title", csid);

                try {
                    boolean searchAllVersions = true;
                    
                    IterableQueryResult result = cmisService.queryAndFetch(mediaQuery, searchAllVersions);
                    
                    try {
                        
                        for (Map<String, Serializable> row : result) {
                            String blobCsid = (String) row.get("Media.media_common:blobCsid");
                            
                            denormValues.put("blobCsid", blobCsid);

                            break;
                        }
                    } finally {
                        result.close();
                    }
                } catch (Exception e) {
                    System.out.println(e);
                } finally {
                    cmisService.close();
                }
            }
        }
        
        jg.writeStartObject();

        writeSystemProperties(jg, doc);
        writeSchemas(jg, doc, schemas);
        writeContextParameters(jg, doc, contextParameters);
        writeDenormValues(jg, doc, denormValues);
        
        jg.writeEndObject();
        jg.flush();
    }
    
    public void writeDenormValues(JsonGenerator jg, DocumentModel doc, ObjectNode denormValues) throws IOException {
        if (denormValues != null && denormValues.size() > 0) {
            if (jg.getCodec() == null) {
                jg.setCodec(objectMapper);
            }

            Iterator<Map.Entry<String, JsonNode>> entries = denormValues.getFields();
            
            while (entries.hasNext()) {
                Map.Entry<String, JsonNode> entry = entries.next();
                
                jg.writeFieldName("collectionspace_denorm:" + entry.getKey());
                jg.writeTree(entry.getValue());
            }
        }
    }

    private class MediaTitleComparator implements Comparator<DocumentModel> {
        @Override
        public int compare(DocumentModel doc1, DocumentModel doc2) {
           String title1 = (String) doc1.getProperty("media_common", "title");
           String title2 = (String) doc2.getProperty("media_common", "title");
           
           if (title1 == null && title2 == null) {
               return 0;
           }
           
           if (title1 == null) {
               return 1;
           }
           
           if (title2 == null) {
               return -1;
           }
           
           return title1.compareTo(title2);
        }
    }
    
    private CallContext getCallContext(CoreSession session) {
        ThresholdOutputStreamFactory streamFactory = ThresholdOutputStreamFactory.newInstance(
                null, 1024 * 1024, -1, false);
        
        CallContextImpl callContext = new CallContextImpl(
                CallContext.BINDING_LOCAL, CmisVersion.CMIS_1_1,
                session.getRepositoryName(), null, null, null,
                new NuxeoCmisServiceFactory(), streamFactory);
        
        callContext.put(CallContext.USERNAME, session.getPrincipal().getName());
        
        return callContext;
    }
}