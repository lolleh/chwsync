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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.chwsync.api.sync.Json;

/**
 * Turns a patient into the folded payload the sync feed carries, and back.
 * <p>
 * A {@link Patient} is not one row: it is a person, a set of names, addresses, attributes and
 * identifiers, each with its own history. Sending those as six entity types would make every
 * offline create six round trips and every conflict six decisions, so the codec folds them into one
 * payload keyed by the patient's uuid. The client sees a patient; the server still writes six
 * tables.
 * <p>
 * Reconciliation is by uuid where the client supplies one and by natural key (a name, an
 * identifier) otherwise. Items present before but absent from the payload are voided rather than
 * deleted, which is how OpenMRS keeps clinical history intact.
 */
public final class PatientCodec {
	
	private PatientCodec() {
	}
	
	// ---------------------------------------------------------------- serialize
	
	public static Map<String, Object> toMap(Patient patient) {
		Map<String, Object> map = Json.map();
		map.put("uuid", patient.getUuid());
		map.put("version", Json.version(lastChanged(patient)));
		map.put("voided", Boolean.valueOf(patient.getVoided()));
		
		map.put("gender", patient.getGender());
		map.put("birthdate", Json.formatDate(patient.getBirthdate()));
		map.put("birthdateEstimated", patient.getBirthdateEstimated());
		map.put("dead", patient.getDead());
		map.put("deathDate", Json.formatDate(patient.getDeathDate()));
		map.put("causeOfDeath", patient.getCauseOfDeath() == null ? null : patient.getCauseOfDeath().getUuid());
		map.put("causeOfDeathNonCoded", patient.getCauseOfDeathNonCoded());
		
		List<Map<String, Object>> names = new ArrayList<Map<String, Object>>();
		for (PersonName name : patient.getNames()) {
			if (Boolean.TRUE.equals(name.getVoided())) {
				continue;
			}
			Map<String, Object> row = Json.map();
			row.put("uuid", name.getUuid());
			row.put("givenName", name.getGivenName());
			row.put("middleName", name.getMiddleName());
			row.put("familyName", name.getFamilyName());
			row.put("familyName2", name.getFamilyName2());
			row.put("prefix", name.getPrefix());
			row.put("familyNamePrefix", name.getFamilyNamePrefix());
			row.put("familyNameSuffix", name.getFamilyNameSuffix());
			row.put("preferred", name.getPreferred());
			names.add(row);
		}
		map.put("names", names);
		
		List<Map<String, Object>> addresses = new ArrayList<Map<String, Object>>();
		for (PersonAddress address : patient.getAddresses()) {
			if (Boolean.TRUE.equals(address.getVoided()) || address.isBlank()) {
				continue;
			}
			Map<String, Object> row = Json.map();
			row.put("uuid", address.getUuid());
			row.put("address1", address.getAddress1());
			row.put("address2", address.getAddress2());
			row.put("cityVillage", address.getCityVillage());
			row.put("countyDistrict", address.getCountyDistrict());
			row.put("stateProvince", address.getStateProvince());
			row.put("country", address.getCountry());
			row.put("postalCode", address.getPostalCode());
			row.put("latitude", address.getLatitude());
			row.put("longitude", address.getLongitude());
			row.put("preferred", address.getPreferred());
			addresses.add(row);
		}
		map.put("addresses", addresses);
		
		List<Map<String, Object>> identifiers = new ArrayList<Map<String, Object>>();
		for (PatientIdentifier identifier : patient.getIdentifiers()) {
			if (Boolean.TRUE.equals(identifier.getVoided())) {
				continue;
			}
			Map<String, Object> row = Json.map();
			row.put("uuid", identifier.getUuid());
			row.put("identifier", identifier.getIdentifier());
			row.put("identifierType",
			    identifier.getIdentifierType() == null ? null : identifier.getIdentifierType().getUuid());
			row.put("location", identifier.getLocation() == null ? null : identifier.getLocation().getUuid());
			row.put("preferred", identifier.getPreferred());
			identifiers.add(row);
		}
		map.put("identifiers", identifiers);
		
		List<Map<String, Object>> attributes = new ArrayList<Map<String, Object>>();
		for (PersonAttribute attribute : patient.getActiveAttributes()) {
			Map<String, Object> row = Json.map();
			row.put("uuid", attribute.getUuid());
			row.put("attributeType", attribute.getAttributeType() == null ? null : attribute.getAttributeType().getUuid());
			row.put("value", attribute.getValue());
			attributes.add(row);
		}
		map.put("attributes", attributes);
		
		return map;
	}
	
	/**
	 * @return the patient's sync version, the value a client compares to detect a stale copy
	 */
	public static Long version(Patient patient) {
		return Json.version(lastChanged(patient));
	}
	
	private static Date lastChanged(Patient patient) {
		Date last = later(patient.getDateChanged(), patient.getDateCreated());
		last = later(last, patient.getPersonDateChanged());
		last = later(last, patient.getPersonDateCreated());
		for (PersonName name : patient.getNames()) {
			last = later(last, name.getDateChanged());
			last = later(last, name.getDateCreated());
		}
		for (PersonAddress address : patient.getAddresses()) {
			last = later(last, address.getDateChanged());
			last = later(last, address.getDateCreated());
		}
		for (PatientIdentifier identifier : patient.getIdentifiers()) {
			last = later(last, identifier.getDateChanged());
			last = later(last, identifier.getDateCreated());
		}
		for (PersonAttribute attribute : patient.getActiveAttributes()) {
			last = later(last, attribute.getDateChanged());
			last = later(last, attribute.getDateCreated());
		}
		return last;
	}
	
	private static Date later(Date a, Date b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return a.after(b) ? a : b;
	}
	
	// ---------------------------------------------------------------- apply
	
	/**
	 * Applies a payload onto a patient. The caller passes a fresh {@link Patient} for a create and a
	 * loaded one for an update; either way the payload is the whole desired state, so anything present
	 * in the patient but absent from the payload is voided.
	 *
	 * @param patient the patient to write onto, never null
	 * @param payload the client's payload
	 * @param user the user performing the write, used for audit columns
	 */
	public static void apply(Patient patient, Map<String, Object> payload, User user) {
		Date now = new Date();
		
		String uuid = Json.string(payload, "uuid");
		if (patient.getUuid() == null && uuid != null) {
			patient.setUuid(uuid);
		}
		patient.setGender(Json.string(payload, "gender"));
		Date birthdate = Json.parseDate(Json.string(payload, "birthdate"));
		if (birthdate != null) {
			patient.setBirthdate(birthdate);
		}
		Boolean birthdateEstimated = Json.bool(payload, "birthdateEstimated");
		if (birthdateEstimated != null) {
			patient.setBirthdateEstimated(birthdateEstimated);
		}
		Boolean dead = Json.bool(payload, "dead");
		if (dead != null) {
			patient.setDead(dead);
		}
		patient.setDeathDate(Json.parseDate(Json.string(payload, "deathDate")));
		patient.setCauseOfDeath(concept(Json.string(payload, "causeOfDeath")));
		patient.setCauseOfDeathNonCoded(Json.string(payload, "causeOfDeathNonCoded"));
		
		applyNames(patient, Json.asMapList(payload.get("names")), user, now);
		applyAddresses(patient, Json.asMapList(payload.get("addresses")), user, now);
		applyIdentifiers(patient, Json.asMapList(payload.get("identifiers")), user, now);
		applyAttributes(patient, Json.asMapList(payload.get("attributes")), user, now);
	}
	
	private static void applyNames(Patient patient, List<Map<String, Object>> rows, User user, Date now) {
		if (rows.isEmpty()) {
			return;
		}
		Set<PersonName> existing = patient.getNames();
		List<PersonName> keep = new ArrayList<PersonName>();
		for (Map<String, Object> row : rows) {
			PersonName name = findName(existing, Json.string(row, "uuid"));
			if (name == null) {
				name = new PersonName();
				patient.addName(name);
			}
			name.setGivenName(Json.string(row, "givenName"));
			name.setMiddleName(Json.string(row, "middleName"));
			name.setFamilyName(Json.string(row, "familyName"));
			name.setFamilyName2(Json.string(row, "familyName2"));
			name.setPrefix(Json.string(row, "prefix"));
			name.setFamilyNamePrefix(Json.string(row, "familyNamePrefix"));
			name.setFamilyNameSuffix(Json.string(row, "familyNameSuffix"));
			name.setPreferred(Json.bool(row, "preferred"));
			keep.add(name);
		}
		voidMissing(existing, keep, user, now);
	}
	
	private static void applyAddresses(Patient patient, List<Map<String, Object>> rows, User user, Date now) {
		if (rows.isEmpty()) {
			return;
		}
		Set<PersonAddress> existing = patient.getAddresses();
		List<PersonAddress> keep = new ArrayList<PersonAddress>();
		for (Map<String, Object> row : rows) {
			PersonAddress address = findAddress(existing, Json.string(row, "uuid"));
			if (address == null) {
				address = new PersonAddress();
				patient.addAddress(address);
			}
			address.setAddress1(Json.string(row, "address1"));
			address.setAddress2(Json.string(row, "address2"));
			address.setCityVillage(Json.string(row, "cityVillage"));
			address.setCountyDistrict(Json.string(row, "countyDistrict"));
			address.setStateProvince(Json.string(row, "stateProvince"));
			address.setCountry(Json.string(row, "country"));
			address.setPostalCode(Json.string(row, "postalCode"));
			address.setLatitude(Json.string(row, "latitude"));
			address.setLongitude(Json.string(row, "longitude"));
			address.setPreferred(Json.bool(row, "preferred"));
			keep.add(address);
		}
		voidMissing(existing, keep, user, now);
	}
	
	private static void applyIdentifiers(Patient patient, List<Map<String, Object>> rows, User user, Date now) {
		if (rows.isEmpty()) {
			return;
		}
		Set<PatientIdentifier> existing = patient.getIdentifiers();
		List<PatientIdentifier> keep = new ArrayList<PatientIdentifier>();
		for (Map<String, Object> row : rows) {
			PatientIdentifier identifier = findIdentifier(existing, Json.string(row, "uuid"));
			if (identifier == null) {
				identifier = new PatientIdentifier();
				identifier.setPatient(patient);
				patient.addIdentifier(identifier);
			}
			identifier.setIdentifier(Json.string(row, "identifier"));
			PatientIdentifierType type = typedByUuid(Json.string(row, "identifierType"));
			if (type != null) {
				identifier.setIdentifierType(type);
			}
			identifier.setLocation(location(Json.string(row, "location")));
			identifier.setPreferred(Json.bool(row, "preferred"));
			keep.add(identifier);
		}
		voidMissing(existing, keep, user, now);
	}
	
	private static void applyAttributes(Patient patient, List<Map<String, Object>> rows, User user, Date now) {
		if (rows.isEmpty()) {
			return;
		}
		Set<PersonAttribute> existing = patient.getAttributes();
		List<PersonAttribute> keep = new ArrayList<PersonAttribute>();
		for (Map<String, Object> row : rows) {
			PersonAttribute attribute = findAttribute(existing, Json.string(row, "uuid"));
			PersonAttributeType type = attributeType(Json.string(row, "attributeType"));
			if (type == null) {
				continue;
			}
			if (attribute == null) {
				attribute = new PersonAttribute(type, Json.string(row, "value"));
				attribute.setPerson(patient);
				patient.addAttribute(attribute);
			} else {
				attribute.setAttributeType(type);
				attribute.setValue(Json.string(row, "value"));
			}
			keep.add(attribute);
		}
		voidMissing(existing, keep, user, now);
	}
	
	private static <T extends org.openmrs.Voidable> void voidMissing(Set<T> existing, List<T> keep, User user, Date now) {
		for (T item : existing) {
			if (Boolean.TRUE.equals(item.getVoided()) || keep.contains(item)) {
				continue;
			}
			item.setVoided(true);
			item.setVoidedBy(user);
			item.setDateVoided(now);
			item.setVoidReason("Replaced by CHW sync");
		}
	}
	
	private static PersonName findName(Set<PersonName> names, String uuid) {
		if (uuid != null) {
			for (PersonName name : names) {
				if (uuid.equals(name.getUuid())) {
					return name;
				}
			}
		}
		return null;
	}
	
	private static PersonAddress findAddress(Set<PersonAddress> addresses, String uuid) {
		if (uuid != null) {
			for (PersonAddress address : addresses) {
				if (uuid.equals(address.getUuid())) {
					return address;
				}
			}
		}
		return null;
	}
	
	private static PatientIdentifier findIdentifier(Set<PatientIdentifier> identifiers, String uuid) {
		if (uuid != null) {
			for (PatientIdentifier identifier : identifiers) {
				if (uuid.equals(identifier.getUuid())) {
					return identifier;
				}
			}
		}
		return null;
	}
	
	private static PersonAttribute findAttribute(Set<PersonAttribute> attributes, String uuid) {
		if (uuid != null) {
			for (PersonAttribute attribute : attributes) {
				if (uuid.equals(attribute.getUuid())) {
					return attribute;
				}
			}
		}
		return null;
	}
	
	private static Concept concept(String uuid) {
		return uuid == null ? null : Context.getConceptService().getConceptByUuid(uuid);
	}
	
	private static Location location(String uuid) {
		return uuid == null ? null : Context.getLocationService().getLocationByUuid(uuid);
	}
	
	private static PatientIdentifierType typedByUuid(String uuid) {
		return uuid == null ? null : Context.getPatientService().getPatientIdentifierTypeByUuid(uuid);
	}
	
	private static PersonAttributeType attributeType(String uuid) {
		return uuid == null ? null : Context.getPersonService().getPersonAttributeTypeByUuid(uuid);
	}
	
	/** Convenience factory used by the sync service for a create. */
	public static Patient newPatient() {
		Patient patient = new Patient();
		patient.setNames(new java.util.HashSet<PersonName>());
		patient.setAddresses(new java.util.HashSet<PersonAddress>());
		patient.setIdentifiers(new java.util.HashSet<PatientIdentifier>());
		patient.setAttributes(new java.util.HashSet<PersonAttribute>());
		return patient;
	}
	
	/** Exposed so callers can blank a payload's transient maps before logging. */
	public static Map<String, Object> emptyPayload() {
		return new LinkedHashMap<String, Object>();
	}
}
