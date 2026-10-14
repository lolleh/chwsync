/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.model;

import java.util.Date;

/**
 * A patient's place in a {@link Household}.
 * <p>
 * Membership is a link row rather than a column on the patient, because a person moving between
 * households is a fact about the relationship, not about the person, and because a household visit
 * needs to list its people without a second round trip.
 */
public class HouseholdMember {
	
	private Integer id;
	
	private Integer householdId;
	
	private Integer patientId;
	
	private String relationship;
	
	private boolean headOfHousehold;
	
	private Date dateCreated;
	
	private Date dateChanged;
	
	private boolean voided;
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer id) {
		this.id = id;
	}
	
	public Integer getHouseholdId() {
		return householdId;
	}
	
	public void setHouseholdId(Integer householdId) {
		this.householdId = householdId;
	}
	
	public Integer getPatientId() {
		return patientId;
	}
	
	public void setPatientId(Integer patientId) {
		this.patientId = patientId;
	}
	
	public String getRelationship() {
		return relationship;
	}
	
	public void setRelationship(String relationship) {
		this.relationship = relationship;
	}
	
	public boolean isHeadOfHousehold() {
		return headOfHousehold;
	}
	
	public void setHeadOfHousehold(boolean headOfHousehold) {
		this.headOfHousehold = headOfHousehold;
	}
	
	public Date getDateCreated() {
		return dateCreated;
	}
	
	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}
	
	public Date getDateChanged() {
		return dateChanged;
	}
	
	public void setDateChanged(Date dateChanged) {
		this.dateChanged = dateChanged;
	}
	
	public boolean isVoided() {
		return voided;
	}
	
	public void setVoided(boolean voided) {
		this.voided = voided;
	}
}
