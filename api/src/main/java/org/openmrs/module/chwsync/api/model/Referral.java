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
 * A CHW's referral of a patient to a facility.
 * <p>
 * Kept as a module table so a deployment needs no referrals module. The status is the part the
 * field worker cares about: whether the referral is still outstanding or has been seen to.
 */
public class Referral {
	
	public static final String STATUS_PENDING = "PENDING";
	
	public static final String STATUS_ACCEPTED = "ACCEPTED";
	
	public static final String STATUS_COMPLETED = "COMPLETED";
	
	public static final String STATUS_CANCELLED = "CANCELLED";
	
	public static final String URGENCY_ROUTINE = "ROUTINE";
	
	public static final String URGENCY_URGENT = "URGENT";
	
	public static final String URGENCY_EMERGENCY = "EMERGENCY";
	
	private Integer referralId;
	
	private String uuid;
	
	private Integer patientId;
	
	private Date referralDate;
	
	private String facility;
	
	private String reason;
	
	private String urgency = URGENCY_ROUTINE;
	
	private String status = STATUS_PENDING;
	
	private String notes;
	
	private Integer creator;
	
	private Date dateCreated;
	
	private Integer changedBy;
	
	private Date dateChanged;
	
	private boolean voided;
	
	private Integer voidedBy;
	
	private Date dateVoided;
	
	private String voidReason;
	
	public Integer getReferralId() {
		return referralId;
	}
	
	public void setReferralId(Integer referralId) {
		this.referralId = referralId;
	}
	
	public String getUuid() {
		return uuid;
	}
	
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
	
	public Integer getPatientId() {
		return patientId;
	}
	
	public void setPatientId(Integer patientId) {
		this.patientId = patientId;
	}
	
	public Date getReferralDate() {
		return referralDate;
	}
	
	public void setReferralDate(Date referralDate) {
		this.referralDate = referralDate;
	}
	
	public String getFacility() {
		return facility;
	}
	
	public void setFacility(String facility) {
		this.facility = facility;
	}
	
	public String getReason() {
		return reason;
	}
	
	public void setReason(String reason) {
		this.reason = reason;
	}
	
	public String getUrgency() {
		return urgency;
	}
	
	public void setUrgency(String urgency) {
		this.urgency = urgency;
	}
	
	public String getStatus() {
		return status;
	}
	
	public void setStatus(String status) {
		this.status = status;
	}
	
	public String getNotes() {
		return notes;
	}
	
	public void setNotes(String notes) {
		this.notes = notes;
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
	
	public Date version() {
		return dateChanged != null ? dateChanged : dateCreated;
	}
}
