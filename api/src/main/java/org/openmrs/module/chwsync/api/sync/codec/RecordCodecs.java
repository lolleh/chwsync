/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.sync.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.context.Context;
import org.openmrs.module.chwsync.api.model.Appointment;
import org.openmrs.module.chwsync.api.model.Household;
import org.openmrs.module.chwsync.api.model.HouseholdMember;
import org.openmrs.module.chwsync.api.model.Referral;
import org.openmrs.module.chwsync.api.sync.Json;

/**
 * Serialises this module's own records - households, appointments and referrals - and applies
 * payloads back.
 * <p>
 * These are the records a CHW creates that OpenMRS core has no home for. They carry patient and
 * location <em>uuids</em> on the wire even though the tables store integer ids, because ids are a
 * property of one server while uuids are what let a client created offline be understood once it
 * reaches the server.
 */
public final class RecordCodecs {
	
	private RecordCodecs() {
	}
	
	// ---------------------------------------------------------------- household
	
	public static Map<String, Object> toMap(Household household) {
		Map<String, Object> map = Json.map();
		map.put("uuid", household.getUuid());
		map.put("version", Json.version(household.version()));
		map.put("voided", Boolean.valueOf(household.isVoided()));
		map.put("name", household.getName());
		map.put("village", household.getVillage());
		map.put("location", uuidOfLocation(household.getLocationId()));
		map.put("headPatient", uuidOfPatient(household.getHeadPatientId()));
		List<Map<String, Object>> members = new ArrayList<Map<String, Object>>();
		for (HouseholdMember member : household.getMembers()) {
			Map<String, Object> row = Json.map();
			row.put("patient", uuidOfPatient(member.getPatientId()));
			row.put("relationship", member.getRelationship());
			row.put("headOfHousehold", Boolean.valueOf(member.isHeadOfHousehold()));
			members.add(row);
		}
		map.put("members", members);
		return map;
	}
	
	public static void apply(Household household, Map<String, Object> payload) {
		String uuid = Json.string(payload, "uuid");
		if (household.getUuid() == null && uuid != null) {
			household.setUuid(uuid);
		}
		household.setName(Json.string(payload, "name"));
		household.setVillage(Json.string(payload, "village"));
		household.setLocationId(idOfLocation(Json.string(payload, "location")));
		household.setHeadPatientId(idOfPatient(Json.string(payload, "headPatient")));
		List<HouseholdMember> members = new ArrayList<HouseholdMember>();
		for (Map<String, Object> row : Json.asMapList(payload.get("members"))) {
			Integer patientId = idOfPatient(Json.string(row, "patient"));
			if (patientId == null) {
				continue;
			}
			HouseholdMember member = new HouseholdMember();
			member.setPatientId(patientId);
			member.setRelationship(Json.string(row, "relationship"));
			member.setHeadOfHousehold(Boolean.TRUE.equals(Json.bool(row, "headOfHousehold")));
			members.add(member);
		}
		household.setMembers(members);
	}
	
	// ---------------------------------------------------------------- appointment
	
	public static Map<String, Object> toMap(Appointment appointment) {
		Map<String, Object> map = Json.map();
		map.put("uuid", appointment.getUuid());
		map.put("version", Json.version(appointment.version()));
		map.put("voided", Boolean.valueOf(appointment.isVoided()));
		map.put("patient", uuidOfPatient(appointment.getPatientId()));
		map.put("provider", uuidOfProvider(appointment.getProviderId()));
		map.put("location", uuidOfLocation(appointment.getLocationId()));
		map.put("appointmentDate", Json.formatDateTime(appointment.getAppointmentDate()));
		map.put("reason", appointment.getReason());
		map.put("status", appointment.getStatus());
		map.put("notes", appointment.getNotes());
		return map;
	}
	
	public static void apply(Appointment appointment, Map<String, Object> payload) {
		String uuid = Json.string(payload, "uuid");
		if (appointment.getUuid() == null && uuid != null) {
			appointment.setUuid(uuid);
		}
		appointment.setPatientId(idOfPatient(Json.string(payload, "patient")));
		appointment.setProviderId(idOfProvider(Json.string(payload, "provider")));
		appointment.setLocationId(idOfLocation(Json.string(payload, "location")));
		appointment.setAppointmentDate(Json.parseDate(Json.string(payload, "appointmentDate")));
		appointment.setReason(Json.string(payload, "reason"));
		String status = Json.string(payload, "status");
		if (status != null) {
			appointment.setStatus(status);
		}
		appointment.setNotes(Json.string(payload, "notes"));
	}
	
	// ---------------------------------------------------------------- referral
	
	public static Map<String, Object> toMap(Referral referral) {
		Map<String, Object> map = Json.map();
		map.put("uuid", referral.getUuid());
		map.put("version", Json.version(referral.version()));
		map.put("voided", Boolean.valueOf(referral.isVoided()));
		map.put("patient", uuidOfPatient(referral.getPatientId()));
		map.put("referralDate", Json.formatDateTime(referral.getReferralDate()));
		map.put("facility", referral.getFacility());
		map.put("reason", referral.getReason());
		map.put("urgency", referral.getUrgency());
		map.put("status", referral.getStatus());
		map.put("notes", referral.getNotes());
		return map;
	}
	
	public static void apply(Referral referral, Map<String, Object> payload) {
		String uuid = Json.string(payload, "uuid");
		if (referral.getUuid() == null && uuid != null) {
			referral.setUuid(uuid);
		}
		referral.setPatientId(idOfPatient(Json.string(payload, "patient")));
		referral.setReferralDate(Json.parseDate(Json.string(payload, "referralDate")));
		referral.setFacility(Json.string(payload, "facility"));
		referral.setReason(Json.string(payload, "reason"));
		String urgency = Json.string(payload, "urgency");
		if (urgency != null) {
			referral.setUrgency(urgency);
		}
		String status = Json.string(payload, "status");
		if (status != null) {
			referral.setStatus(status);
		}
		referral.setNotes(Json.string(payload, "notes"));
	}
	
	// ---------------------------------------------------------------- uuid <-> id
	
	private static String uuidOfPatient(Integer patientId) {
		if (patientId == null) {
			return null;
		}
		Patient patient = Context.getPatientService().getPatient(patientId);
		return patient == null ? null : patient.getUuid();
	}
	
	private static Integer idOfPatient(String uuid) {
		if (uuid == null) {
			return null;
		}
		Patient patient = Context.getPatientService().getPatientByUuid(uuid);
		return patient == null ? null : patient.getPatientId();
	}
	
	private static String uuidOfLocation(Integer locationId) {
		if (locationId == null) {
			return null;
		}
		Location location = Context.getLocationService().getLocation(locationId);
		return location == null ? null : location.getUuid();
	}
	
	private static Integer idOfLocation(String uuid) {
		if (uuid == null) {
			return null;
		}
		Location location = Context.getLocationService().getLocationByUuid(uuid);
		return location == null ? null : location.getLocationId();
	}
	
	private static String uuidOfProvider(Integer providerId) {
		if (providerId == null) {
			return null;
		}
		Provider provider = Context.getProviderService().getProvider(providerId);
		return provider == null ? null : provider.getUuid();
	}
	
	private static Integer idOfProvider(String uuid) {
		if (uuid == null) {
			return null;
		}
		Provider provider = Context.getProviderService().getProviderByUuid(uuid);
		return provider == null ? null : provider.getProviderId();
	}
}
