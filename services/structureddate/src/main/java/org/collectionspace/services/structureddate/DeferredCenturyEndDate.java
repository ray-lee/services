package org.collectionspace.services.structureddate;


/**
 * A deferred date that represents the end of a century. The end year
 * can not be determined until the era of the century is known. Once the 
 * era is known, resolveDate() may be called to calculate the year.
 */
public class DeferredCenturyEndDate extends DeferredCenturyDate {

	public DeferredCenturyEndDate(int century) {
		super(century);
	}
	
	@Override
	public void resolveDate() {
		Era era = getEra();
		
		if (era == null) {
			era = Date.DEFAULT_ERA;
		}
		
		Date endDate = DateUtils.getCenturyEndDate(century, era);
		
		setYear(endDate.getYear());
		setMonth(endDate.getMonth());
		setDay(endDate.getDay());
		setEra(endDate.getEra());
	}
}
