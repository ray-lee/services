package org.collectionspace.services.test;

import java.util.HashMap;
import org.collectionspace.services.batch.nuxeo.CreateCatalogFromMedia;

public class CreateCatalogFromMediaTest
{
   public static void main( String[] args )
   {
      System.out.println( "CreateCatalogFromMediaTest" );
      CreateCatalogFromMedia batch = new CreateCatalogFromMedia();
      //batch.run();
      System.out.println( batch.getRelationPayload(
        "SourceCSID", "SourceType", "ObjectCSID", "ObjectType", "Relation" ));
      HashMap<String, String> fields = new HashMap<String, String>();
      fields.put( "objectNumber", "COBJ10020" );
      fields.put( "title", "CollObject 10020" );
      fields.put( "date", "my x date" );
      //System.out.println( batch.getCatalogRecordPayload( fields ));
   }
}
