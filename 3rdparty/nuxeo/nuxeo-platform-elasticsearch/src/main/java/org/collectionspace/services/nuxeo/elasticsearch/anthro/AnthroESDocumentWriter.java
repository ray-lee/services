package org.collectionspace.services.nuxeo.elasticsearch.anthro;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.IntNode;
import org.codehaus.jackson.node.ObjectNode;

import org.collectionspace.services.nuxeo.elasticsearch.DefaultESDocumentWriter;
import org.nuxeo.ecm.core.api.DocumentModel;

public class AnthroESDocumentWriter extends DefaultESDocumentWriter {

	@Override
	public ObjectNode getDenormValues(DocumentModel doc) {
		ObjectNode denormValues = super.getDenormValues(doc);

		String docType = doc.getType();

		if (docType.startsWith("CollectionObject")) {
			// Create a list of collection years from the field collection date structured date.

			Set<Integer> years = new HashSet<Integer>();
			Map<String, Object> fieldCollectionDateGroup = (Map<String, Object>) doc.getProperty("collectionobjects_common", "fieldCollectionDateGroup");

			if (fieldCollectionDateGroup != null) {
				GregorianCalendar earliestCalendar = (GregorianCalendar) fieldCollectionDateGroup.get("dateEarliestScalarValue");
				GregorianCalendar latestCalendar = (GregorianCalendar) fieldCollectionDateGroup.get("dateLatestScalarValue");

				if (earliestCalendar != null && latestCalendar != null) {
					// Grr @ latest scalar value historically being exclusive.
					// Subtract one day to make it inclusive.
					latestCalendar.add(Calendar.DATE, -1);

					Integer earliestYear = earliestCalendar.get(Calendar.YEAR);
					Integer latestYear = latestCalendar.get(Calendar.YEAR);;

					for (int year = earliestYear; year <= latestYear; year++) {
						years.add(year);
					}
				}
			}

			List<Integer> yearList = new ArrayList<Integer>(years);
			Collections.sort(yearList);

			List<JsonNode> yearNodes = new ArrayList<JsonNode>();

			for (Integer year : yearList) {
				yearNodes.add(new IntNode(year));
			}

			denormValues.putArray("collectionYears").addAll(yearNodes);
		}

		return denormValues;
	}
}
