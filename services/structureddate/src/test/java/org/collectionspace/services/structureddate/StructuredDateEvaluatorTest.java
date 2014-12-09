package org.collectionspace.services.structureddate;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.Yaml;

public class StructuredDateEvaluatorTest {
	public static final String TEST_CASE_FILE = "/test-dates.yaml";
	public static final List<String> YAML_DATE_SPEC = Arrays.asList("year", "month", "day", "era", "certainty", "qualifierType", "qualifierValue", "qualifierUnit");

	final Logger logger = LoggerFactory.getLogger(StructuredDateEvaluatorTest.class);

	@BeforeClass
	public void setUp() {

	};

	@Test
	public void test() {
		Yaml yaml = new Yaml();
		Map<String, Object> testCases = (Map<String, Object>) yaml.load(getClass().getResourceAsStream(TEST_CASE_FILE));

		for (String displayDate : testCases.keySet()) {
			logger.debug("Testing input: " + displayDate);

			Map<String, Object> expectedStructuredDateFields = (Map<String, Object>) testCases.get(displayDate);

			StructuredDate expectedStructuredDate = createStructuredDateFromYamlSpec(displayDate, expectedStructuredDateFields);
			StructuredDate actualStructuredDate = null;
			
			try {
				actualStructuredDate = StructuredDate.parse(displayDate);
			}
			catch(StructuredDateFormatException e) {
				logger.debug(e.getMessage());
			}

			Assert.assertEquals(actualStructuredDate, expectedStructuredDate);
		}
	}

	private StructuredDate createStructuredDateFromYamlSpec(String displayDate, Map<String, Object> structuredDateFields) {
		StructuredDate structuredDate = null;

		if (structuredDateFields != null) {
			structuredDate = new StructuredDate();

			for (String propertyName : structuredDateFields.keySet()) {
				Object value = structuredDateFields.get(propertyName);

				try {
					Class propertyType = PropertyUtils.getPropertyType(structuredDate, propertyName);

					if (propertyType.equals(Date.class)) {
						value = createDateFromYamlSpec((List<Object>) value);
					}

					PropertyUtils.setProperty(structuredDate, propertyName, value);
				}
				catch(NoSuchMethodException e) {
					logger.warn(propertyName + " is not a property");
				}
				catch(InvocationTargetException e) {
					logger.error(propertyName + " accessor threw an exception");
				}
				catch(IllegalAccessException e) {
					logger.error("could not access property " + propertyName);
				}
			}
			
			if (structuredDate.getDisplayDate() == null) {
				structuredDate.setDisplayDate(displayDate);
			}
		}

		return structuredDate;
	}

	private Date createDateFromYamlSpec(List<Object> dateFields) {
		Date date = new Date();
		Iterator<Object> fieldIterator = dateFields.iterator();

		for (String propertyName : YAML_DATE_SPEC) {
			Object value = fieldIterator.hasNext() ? fieldIterator.next() : null;

			try {
				Class propertyType = PropertyUtils.getPropertyType(date, propertyName);

				if (value != null && Enum.class.isAssignableFrom(propertyType)) {
					value = Enum.valueOf(propertyType, (String) value);
				}

				PropertyUtils.setProperty(date, propertyName, value);
			}
			catch(NoSuchMethodException e) {
				logger.warn(propertyName + " is not a property");
			}
			catch(InvocationTargetException e) {
				logger.error(propertyName + " accessor threw an exception");
			}
			catch(IllegalAccessException e) {
				logger.error("could not access property " + propertyName);
			}   		
		}

		return date;
	}
}
