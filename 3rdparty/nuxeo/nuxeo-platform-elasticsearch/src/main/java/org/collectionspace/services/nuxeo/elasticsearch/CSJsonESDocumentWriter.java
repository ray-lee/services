package org.collectionspace.services.nuxeo.elasticsearch;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import javax.ws.rs.core.HttpHeaders;

import org.codehaus.jackson.JsonGenerator;
import org.nuxeo.ecm.automation.jaxrs.io.documents.JsonESDocumentWriter;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.plexus.util.StringUtils;

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
            
            String uri = (String) doc.getProperty("collectionobjects_core", "uri");
            String csid = uri.substring(uri.lastIndexOf('/') + 1);
            String mediaQuery = String.format("SELECT media_common:blobCsid, media_common:title FROM Relation WHERE relations_common:subjectCsid = '%s' AND relations_common:objectDocumentType = 'Media'", csid);

            DocumentModelList mediaDocs = session.query(mediaQuery, 1);
            
            if (mediaDocs.size() > 0) {
                
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
}