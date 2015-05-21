package org.collectionspace.services.structureddate;

public enum Era {
	BCE ("urn:cspace:pahma.cspace.berkeley.edu:vocabularies:name(dateera):item:name(bce)'BC/BCE'"),
	CE  ("urn:cspace:pahma.cspace.berkeley.edu:vocabularies:name(dateera):item:name(ce)'AD/CE'");
	
	private final String value;
	
	private Era(String value) {
		this.value = value;
	}
	
	public String toString() {
		return value;
	}
	
	public String toDisplayString() {
		int index = value.indexOf("'");
		
		return value.substring(index + 1, value.length() - 1);
	}
}
