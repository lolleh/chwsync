/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.dao.hibernate;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.module.chwsync.api.dao.AbstractChwSyncDao;
import org.openmrs.module.chwsync.api.dao.ChwSyncRecordDao;
import org.openmrs.module.chwsync.api.model.Appointment;
import org.openmrs.module.chwsync.api.model.Household;
import org.openmrs.module.chwsync.api.model.HouseholdMember;
import org.openmrs.module.chwsync.api.model.Referral;

/**
 * Native-SQL persistence for this module's own records: households, appointments and referrals.
 * <p>
 * Every write sets {@code date_changed} (on update) or {@code date_created} (on insert), because
 * those two columns are what the sync feed uses as a record's version - they are how a client can
 * tell whether the copy it holds is still current.
 */
public class HibernateChwSyncRecordDao extends AbstractChwSyncDao implements ChwSyncRecordDao {
	
	private static final String HOUSEHOLD_COLUMNS = "SELECT household_id, uuid, name, location_id, head_patient_id,"
	        + " village, creator, date_created, changed_by, date_changed, voided, voided_by, date_voided,"
	        + " void_reason FROM chwsync_household";
	
	private static final String MEMBER_COLUMNS = "SELECT id, household_id, patient_id, relationship, head_of_household,"
	        + " date_created, date_changed, voided FROM chwsync_household_member";
	
	private static final String APPOINTMENT_COLUMNS = "SELECT appointment_id, uuid, patient_id, provider_id,"
	        + " location_id, appointment_date, reason, status, notes, creator, date_created, changed_by, date_changed,"
	        + " voided, voided_by, date_voided, void_reason FROM chwsync_appointment";
	
	private static final String REFERRAL_COLUMNS = "SELECT referral_id, uuid, patient_id, referral_date, facility,"
	        + " reason, urgency, status, notes, creator, date_created, changed_by, date_changed, voided, voided_by,"
	        + " date_voided, void_reason FROM chwsync_referral";
	
	// ---------------------------------------------------------------- households
	
	public Household findHouseholdByUuid(String uuid) {
		if (uuid == null) {
			return null;
		}
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("uuid", uuid);
		Household household = unique(HOUSEHOLD_COLUMNS + " WHERE uuid = :uuid", params,
		    HibernateChwSyncRecordDao::mapHousehold);
		if (household != null) {
			household.setMembers(findMembers(household.getHouseholdId()));
		}
		return household;
	}
	
	public List<Household> listHouseholds(boolean includeVoided) {
		String sql = HOUSEHOLD_COLUMNS + (includeVoided ? "" : " WHERE voided = false") + " ORDER BY name ASC";
		List<Household> households = list(sql, null, HibernateChwSyncRecordDao::mapHousehold);
		for (Household household : households) {
			household.setMembers(findMembers(household.getHouseholdId()));
		}
		return households;
	}
	
	public List<Household> findHouseholdsForPatient(Integer patientId) {
		if (patientId == null) {
			return new ArrayList<Household>();
		}
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("patientId", patientId);
		return list(
		    HOUSEHOLD_COLUMNS + " h JOIN chwsync_household_member m ON m.household_id = h.household_id"
		            + " WHERE m.patient_id = :patientId AND m.voided = false AND h.voided = false",
		    params, HibernateChwSyncRecordDao::mapHousehold);
	}
	
	public void insertHousehold(Household household) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("uuid", household.getUuid());
		params.put("name", household.getName());
		params.put("locationId", household.getLocationId());
		params.put("headPatientId", household.getHeadPatientId());
		params.put("village", household.getVillage());
		params.put("creator", household.getCreator());
		params.put("dateCreated", household.getDateCreated());
		params.put("changedBy", household.getChangedBy());
		params.put("dateChanged", household.getDateChanged());
		params.put("voided", Boolean.valueOf(household.isVoided()));
		params.put("voidedBy", household.getVoidedBy());
		params.put("dateVoided", household.getDateVoided());
		params.put("voidReason", household.getVoidReason());
		execute("INSERT INTO chwsync_household (uuid, name, location_id, head_patient_id, village, creator, date_created,"
		        + " changed_by, date_changed, voided, voided_by, date_voided, void_reason) VALUES (:uuid, :name,"
		        + " :locationId, :headPatientId, :village, :creator, :dateCreated, :changedBy, :dateChanged, :voided,"
		        + " :voidedBy, :dateVoided, :voidReason)",
		    params);
	}
	
	public void updateHousehold(Household household) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("uuid", household.getUuid());
		params.put("name", household.getName());
		params.put("locationId", household.getLocationId());
		params.put("headPatientId", household.getHeadPatientId());
		params.put("village", household.getVillage());
		params.put("changedBy", household.getChangedBy());
		params.put("dateChanged", household.getDateChanged());
		params.put("voided", Boolean.valueOf(household.isVoided()));
		params.put("voidedBy", household.getVoidedBy());
		params.put("dateVoided", household.getDateVoided());
		params.put("voidReason", household.getVoidReason());
		execute(
		    "UPDATE chwsync_household SET name = :name, location_id = :locationId, head_patient_id = :headPatientId,"
		            + " village = :village, changed_by = :changedBy, date_changed = :dateChanged, voided = :voided,"
		            + " voided_by = :voidedBy, date_voided = :dateVoided, void_reason = :voidReason WHERE uuid = :uuid",
		    params);
	}
	
	public List<HouseholdMember> findMembers(Integer householdId) {
		if (householdId == null) {
			return new ArrayList<HouseholdMember>();
		}
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("householdId", householdId);
		return list(MEMBER_COLUMNS + " WHERE household_id = :householdId AND voided = false ORDER BY id ASC", params,
		    HibernateChwSyncRecordDao::mapMember);
	}
	
	public void clearMembers(Integer householdId) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("householdId", householdId);
		params.put("voided", Boolean.TRUE);
		execute("UPDATE chwsync_household_member SET voided = :voided WHERE household_id = :householdId AND voided = false",
		    params);
	}
	
	public void insertMember(HouseholdMember member) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("householdId", member.getHouseholdId());
		params.put("patientId", member.getPatientId());
		params.put("relationship", member.getRelationship());
		params.put("headOfHousehold", Boolean.valueOf(member.isHeadOfHousehold()));
		params.put("dateCreated", member.getDateCreated());
		params.put("dateChanged", member.getDateChanged());
		params.put("voided", Boolean.FALSE);
		execute("INSERT INTO chwsync_household_member (household_id, patient_id, relationship, head_of_household,"
		        + " date_created, date_changed, voided) VALUES (:householdId, :patientId, :relationship,"
		        + " :headOfHousehold, :dateCreated, :dateChanged, :voided)",
		    params);
	}
	
	// ---------------------------------------------------------------- appointments
	
	public Appointment findAppointmentByUuid(String uuid) {
		if (uuid == null) {
			return null;
		}
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("uuid", uuid);
		return unique(APPOINTMENT_COLUMNS + " WHERE uuid = :uuid", params, HibernateChwSyncRecordDao::mapAppointment);
	}
	
	public List<Appointment> findAppointments(Integer patientId, String status, Date from, Date to, boolean includeVoided) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		StringBuilder sql = new StringBuilder(APPOINTMENT_COLUMNS).append(" WHERE 1 = 1");
		if (!includeVoided) {
			sql.append(" AND voided = false");
		}
		if (patientId != null) {
			sql.append(" AND patient_id = :patientId");
			params.put("patientId", patientId);
		}
		if (status != null && !status.isEmpty()) {
			sql.append(" AND status = :status");
			params.put("status", status);
		}
		if (from != null) {
			sql.append(" AND appointment_date >= :from");
			params.put("from", from);
		}
		if (to != null) {
			sql.append(" AND appointment_date <= :to");
			params.put("to", to);
		}
		sql.append(" ORDER BY appointment_date ASC");
		return list(sql.toString(), params, HibernateChwSyncRecordDao::mapAppointment);
	}
	
	public void insertAppointment(Appointment appointment) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("uuid", appointment.getUuid());
		params.put("patientId", appointment.getPatientId());
		params.put("providerId", appointment.getProviderId());
		params.put("locationId", appointment.getLocationId());
		params.put("appointmentDate", appointment.getAppointmentDate());
		params.put("reason", appointment.getReason());
		params.put("status", appointment.getStatus());
		params.put("notes", appointment.getNotes());
		params.put("creator", appointment.getCreator());
		params.put("dateCreated", appointment.getDateCreated());
		params.put("changedBy", appointment.getChangedBy());
		params.put("dateChanged", appointment.getDateChanged());
		params.put("voided", Boolean.valueOf(appointment.isVoided()));
		params.put("voidedBy", appointment.getVoidedBy());
		params.put("dateVoided", appointment.getDateVoided());
		params.put("voidReason", appointment.getVoidReason());
		execute("INSERT INTO chwsync_appointment (uuid, patient_id, provider_id, location_id, appointment_date, reason,"
		        + " status, notes, creator, date_created, changed_by, date_changed, voided, voided_by, date_voided,"
		        + " void_reason) VALUES (:uuid, :patientId, :providerId, :locationId, :appointmentDate, :reason,"
		        + " :status, :notes, :creator, :dateCreated, :changedBy, :dateChanged, :voided, :voidedBy, :dateVoided,"
		        + " :voidReason)",
		    params);
	}
	
	public void updateAppointment(Appointment appointment) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("uuid", appointment.getUuid());
		params.put("patientId", appointment.getPatientId());
		params.put("providerId", appointment.getProviderId());
		params.put("locationId", appointment.getLocationId());
		params.put("appointmentDate", appointment.getAppointmentDate());
		params.put("reason", appointment.getReason());
		params.put("status", appointment.getStatus());
		params.put("notes", appointment.getNotes());
		params.put("changedBy", appointment.getChangedBy());
		params.put("dateChanged", appointment.getDateChanged());
		params.put("voided", Boolean.valueOf(appointment.isVoided()));
		params.put("voidedBy", appointment.getVoidedBy());
		params.put("dateVoided", appointment.getDateVoided());
		params.put("voidReason", appointment.getVoidReason());
		execute(
		    "UPDATE chwsync_appointment SET patient_id = :patientId, provider_id = :providerId,"
		            + " location_id = :locationId, appointment_date = :appointmentDate, reason = :reason, status = :status,"
		            + " notes = :notes, changed_by = :changedBy, date_changed = :dateChanged, voided = :voided,"
		            + " voided_by = :voidedBy, date_voided = :dateVoided, void_reason = :voidReason WHERE uuid = :uuid",
		    params);
	}
	
	// ---------------------------------------------------------------- referrals
	
	public Referral findReferralByUuid(String uuid) {
		if (uuid == null) {
			return null;
		}
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("uuid", uuid);
		return unique(REFERRAL_COLUMNS + " WHERE uuid = :uuid", params, HibernateChwSyncRecordDao::mapReferral);
	}
	
	public List<Referral> findReferrals(Integer patientId, String status, boolean includeVoided) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		StringBuilder sql = new StringBuilder(REFERRAL_COLUMNS).append(" WHERE 1 = 1");
		if (!includeVoided) {
			sql.append(" AND voided = false");
		}
		if (patientId != null) {
			sql.append(" AND patient_id = :patientId");
			params.put("patientId", patientId);
		}
		if (status != null && !status.isEmpty()) {
			sql.append(" AND status = :status");
			params.put("status", status);
		}
		sql.append(" ORDER BY referral_date DESC");
		return list(sql.toString(), params, HibernateChwSyncRecordDao::mapReferral);
	}
	
	public void insertReferral(Referral referral) {
		Map<String, Object> params = referralParams(referral);
		execute("INSERT INTO chwsync_referral (uuid, patient_id, referral_date, facility, reason, urgency, status, notes,"
		        + " creator, date_created, changed_by, date_changed, voided, voided_by, date_voided, void_reason)"
		        + " VALUES (:uuid, :patientId, :referralDate, :facility, :reason, :urgency, :status, :notes, :creator,"
		        + " :dateCreated, :changedBy, :dateChanged, :voided, :voidedBy, :dateVoided, :voidReason)",
		    params);
	}
	
	public void updateReferral(Referral referral) {
		Map<String, Object> params = referralParams(referral);
		execute("UPDATE chwsync_referral SET patient_id = :patientId, referral_date = :referralDate, facility = :facility,"
		        + " reason = :reason, urgency = :urgency, status = :status, notes = :notes, changed_by = :changedBy,"
		        + " date_changed = :dateChanged, voided = :voided, voided_by = :voidedBy, date_voided = :dateVoided,"
		        + " void_reason = :voidReason WHERE uuid = :uuid",
		    params);
	}
	
	private static Map<String, Object> referralParams(Referral referral) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("uuid", referral.getUuid());
		params.put("patientId", referral.getPatientId());
		params.put("referralDate", referral.getReferralDate());
		params.put("facility", referral.getFacility());
		params.put("reason", referral.getReason());
		params.put("urgency", referral.getUrgency());
		params.put("status", referral.getStatus());
		params.put("notes", referral.getNotes());
		params.put("creator", referral.getCreator());
		params.put("dateCreated", referral.getDateCreated());
		params.put("changedBy", referral.getChangedBy());
		params.put("dateChanged", referral.getDateChanged());
		params.put("voided", Boolean.valueOf(referral.isVoided()));
		params.put("voidedBy", referral.getVoidedBy());
		params.put("dateVoided", referral.getDateVoided());
		params.put("voidReason", referral.getVoidReason());
		return params;
	}
	
	// ---------------------------------------------------------------- mappers
	
	private static Household mapHousehold(Object[] row) {
		Household household = new Household();
		household.setHouseholdId(asInteger(row[0]));
		household.setUuid(asString(row[1]));
		household.setName(asString(row[2]));
		household.setLocationId(asInteger(row[3]));
		household.setHeadPatientId(asInteger(row[4]));
		household.setVillage(asString(row[5]));
		household.setCreator(asInteger(row[6]));
		household.setDateCreated(asDate(row[7]));
		household.setChangedBy(asInteger(row[8]));
		household.setDateChanged(asDate(row[9]));
		household.setVoided(asPrimitiveBoolean(row[10]));
		household.setVoidedBy(asInteger(row[11]));
		household.setDateVoided(asDate(row[12]));
		household.setVoidReason(asString(row[13]));
		return household;
	}
	
	private static HouseholdMember mapMember(Object[] row) {
		HouseholdMember member = new HouseholdMember();
		member.setId(asInteger(row[0]));
		member.setHouseholdId(asInteger(row[1]));
		member.setPatientId(asInteger(row[2]));
		member.setRelationship(asString(row[3]));
		member.setHeadOfHousehold(asPrimitiveBoolean(row[4]));
		member.setDateCreated(asDate(row[5]));
		member.setDateChanged(asDate(row[6]));
		member.setVoided(asPrimitiveBoolean(row[7]));
		return member;
	}
	
	private static Appointment mapAppointment(Object[] row) {
		Appointment appointment = new Appointment();
		appointment.setAppointmentId(asInteger(row[0]));
		appointment.setUuid(asString(row[1]));
		appointment.setPatientId(asInteger(row[2]));
		appointment.setProviderId(asInteger(row[3]));
		appointment.setLocationId(asInteger(row[4]));
		appointment.setAppointmentDate(asDate(row[5]));
		appointment.setReason(asString(row[6]));
		appointment.setStatus(asString(row[7]));
		appointment.setNotes(asString(row[8]));
		appointment.setCreator(asInteger(row[9]));
		appointment.setDateCreated(asDate(row[10]));
		appointment.setChangedBy(asInteger(row[11]));
		appointment.setDateChanged(asDate(row[12]));
		appointment.setVoided(asPrimitiveBoolean(row[13]));
		appointment.setVoidedBy(asInteger(row[14]));
		appointment.setDateVoided(asDate(row[15]));
		appointment.setVoidReason(asString(row[16]));
		return appointment;
	}
	
	private static Referral mapReferral(Object[] row) {
		Referral referral = new Referral();
		referral.setReferralId(asInteger(row[0]));
		referral.setUuid(asString(row[1]));
		referral.setPatientId(asInteger(row[2]));
		referral.setReferralDate(asDate(row[3]));
		referral.setFacility(asString(row[4]));
		referral.setReason(asString(row[5]));
		referral.setUrgency(asString(row[6]));
		referral.setStatus(asString(row[7]));
		referral.setNotes(asString(row[8]));
		referral.setCreator(asInteger(row[9]));
		referral.setDateCreated(asDate(row[10]));
		referral.setChangedBy(asInteger(row[11]));
		referral.setDateChanged(asDate(row[12]));
		referral.setVoided(asPrimitiveBoolean(row[13]));
		referral.setVoidedBy(asInteger(row[14]));
		referral.setDateVoided(asDate(row[15]));
		referral.setVoidReason(asString(row[16]));
		return referral;
	}
}
