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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A household, the unit a community health worker actually visits.
 * <p>
 * OpenMRS core has no household concept, so this is the module's own. It is deliberately thin: a
 * name, the village, the patient who heads it, and the members. The point is to let one visit or
 * one referral be recorded once for the group rather than once per person.
 */
public class Household {
	
	private Integer householdId;
	
	private String uuid;
	
	private String name;
	
	private Integer locationId;
	
	private Integer headPatientId;
	
	private String village;
	
	private Integer creator;
	
	private Date dateCreated;
	
	private Integer changedBy;
	
	private Date dateChanged;
	
	private boolean voided;
	
	private Integer voidedBy;
	
	private Date dateVoided;
	
	private String voidReason;
	
	private List<HouseholdMember> members = new ArrayList<HouseholdMember>();
	
	public Integer getHouseholdId() {
		return householdId;
	}
	
	public void setHouseholdId(Integer householdId) {
		this.householdId = householdId;
	}
	
	public String getUuid() {
		return uuid;
	}
	
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public Integer getLocationId() {
		return locationId;
	}
	
	public void setLocationId(Integer locationId) {
		this.locationId = locationId;
	}
	
	public Integer getHeadPatientId() {
		return headPatientId;
	}
	
	public void setHeadPatientId(Integer headPatientId) {
		this.headPatientId = headPatientId;
	}
	
	public String getVillage() {
		return village;
	}
	
	public void setVillage(String village) {
		this.village = village;
	}
	
	public Integer getCreator() {
		return creator;
	}
	
	public void setCreator(Integer creator) {
		this.creator = creator;
	}
	
	public Date getDateCreated() {
		return dateCreated;
	}
	
	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}
	
	public Integer getChangedBy() {
		return changedBy;
	}
	
	public void setChangedBy(Integer changedBy) {
		this.changedBy = changedBy;
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
	
	public Integer getVoidedBy() {
		return voidedBy;
	}
	
	public void setVoidedBy(Integer voidedBy) {
		this.voidedBy = voidedBy;
	}
	
	public Date getDateVoided() {
		return dateVoided;
	}
	
	public void setDateVoided(Date dateVoided) {
		this.dateVoided = dateVoided;
	}
	
	public String getVoidReason() {
		return voidReason;
	}
	
	public void setVoidReason(String voidReason) {
		this.voidReason = voidReason;
	}
	
	public List<HouseholdMember> getMembers() {
		return members;
	}
	
	public void setMembers(List<HouseholdMember> members) {
		this.members = members == null ? new ArrayList<HouseholdMember>() : members;
	}
	
	/**
	 * @return the last moment this record was touched, used as the sync version
	 */
	public Date version() {
		return dateChanged != null ? dateChanged : dateCreated;
	}
}
