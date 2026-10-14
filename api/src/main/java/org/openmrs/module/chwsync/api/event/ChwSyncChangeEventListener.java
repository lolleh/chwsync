/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.event;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.jms.MapMessage;
import javax.jms.Message;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.OpenmrsObject;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.Person;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonName;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.event.Event;
import org.openmrs.event.SubscribableEventListener;
import org.openmrs.module.chwsync.api.service.ChwSyncService;
import org.openmrs.module.chwsync.api.sync.ChangeOperation;
import org.openmrs.module.chwsync.api.sync.SyncEntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns core change events into entries in the sync feed.
 * <p>
 * OpenMRS core already fires an event every time it writes a patient, encounter, visit or obs, and
 * on a facility workstation that is exactly where CHW-relevant edits originate. Rather than poll
 * core tables on every pull - which cannot be paged correctly off a timestamp, because a large edit
 * burst would overflow one page and the next pull would step past it - the feed is appended to
 * here, at the moment of the change, and pull simply reads the log in order.
 * <p>
 * Most events name the record directly. A few name a <em>part</em> of one: a change to a person's
 * name is an event about a {@link PersonName}, not about the patient. Those are resolved back to
 * the top level record they belong to, so a facility correcting a surname still shows up as a
 * patient change on the next pull. An obs is resolved to its encounter, because obs are never
 * synced on their own.
 */
public class ChwSyncChangeEventListener implements SubscribableEventListener {
	
	private static final Logger log = LoggerFactory.getLogger(ChwSyncChangeEventListener.class);
	
	private ChwSyncService syncService;
	
	public void setSyncService(ChwSyncService syncService) {
		this.syncService = syncService;
	}
	
	/**
	 * The core types whose changes matter to a CHW. The person-level types are here because the patient
	 * payload folds names, addresses, identifiers and attributes; without them a corrected surname
	 * would never reach the device.
	 */
	@Override
	public List<Class<? extends OpenmrsObject>> subscribeToObjects() {
		return Arrays.<Class<? extends OpenmrsObject>> asList(Patient.class, Person.class, PersonName.class,
		    PersonAddress.class, PatientIdentifier.class, PersonAttribute.class, Encounter.class, Obs.class, Visit.class);
	}
	
	/**
	 * Only actions the feed can express. Retirement is a metadata concept and never applies to a
	 * clinical row, so it is left out rather than mapped onto something it is not.
	 */
	@Override
	public List<String> subscribeToActions() {
		return Arrays.asList(Event.Action.CREATED.name(), Event.Action.UPDATED.name(), Event.Action.VOIDED.name(),
		    Event.Action.UNVOIDED.name(), Event.Action.PURGED.name());
	}
	
	@Override
	public void onMessage(Message message) {
		try {
			if (!(message instanceof MapMessage)) {
				return;
			}
			MapMessage body = (MapMessage) message;
			String uuid = body.getString("uuid");
			String classname = body.getString("classname");
			ChangeOperation operation = ChangeOperation.fromEventAction(body.getString("action"));
			if (StringUtils.isBlank(uuid) || classname == null || operation == null) {
				return;
			}
			resolve(classname, uuid, operation);
		}
		catch (Exception e) {
			// A single malformed event must not take the listener down and stop every later change from
			// being recorded; the next write to the same record will re-offer the same uuid.
			log.warn("CHW sync: could not record a core change event", e);
		}
	}
	
	private void resolve(String classname, String uuid, ChangeOperation operation) {
		SyncEntityType type;
		String topUuid;
		
		if (matches(classname, Encounter.class)) {
			type = SyncEntityType.ENCOUNTER;
			topUuid = uuid;
		} else if (matches(classname, Visit.class)) {
			type = SyncEntityType.VISIT;
			topUuid = uuid;
		} else if (matches(classname, Obs.class)) {
			Encounter encounter = encounterOfObs(uuid);
			if (encounter == null) {
				return;
			}
			type = SyncEntityType.ENCOUNTER;
			topUuid = encounter.getUuid();
		} else if (matches(classname, Patient.class) || matches(classname, Person.class)) {
			type = SyncEntityType.PATIENT;
			topUuid = uuid;
		} else if (matches(classname, PersonName.class)) {
			topUuid = personUuid(Context.getPersonService().getPersonNameByUuid(uuid));
			type = SyncEntityType.PATIENT;
		} else if (matches(classname, PersonAddress.class)) {
			topUuid = personUuid(Context.getPersonService().getPersonAddressByUuid(uuid));
			type = SyncEntityType.PATIENT;
		} else if (matches(classname, PersonAttribute.class)) {
			topUuid = personUuid(Context.getPersonService().getPersonAttributeByUuid(uuid));
			type = SyncEntityType.PATIENT;
		} else if (matches(classname, PatientIdentifier.class)) {
			PatientIdentifier identifier = Context.getPatientService().getPatientIdentifierByUuid(uuid);
			topUuid = identifier == null || identifier.getPatient() == null ? null : identifier.getPatient().getUuid();
			type = SyncEntityType.PATIENT;
		} else {
			return;
		}
		
		if (StringUtils.isBlank(topUuid)) {
			return;
		}
		record(type, topUuid, operation);
	}
	
	/**
	 * Records the change against the sync service, making sure this thread has a session. The event
	 * module delivers on its own thread, which has none, and a service call without one cannot reach
	 * the database. If a session is somehow already open - a synchronous delivery inside the writing
	 * thread - it is reused rather than a second one being opened on top of it.
	 */
	private void record(SyncEntityType type, String uuid, ChangeOperation operation) {
		boolean opened = false;
		try {
			if (!Context.isSessionOpen()) {
				Context.openSession();
				opened = true;
			}
			syncService.recordCoreChange(type, uuid, operation, new Date());
		}
		finally {
			if (opened) {
				Context.closeSession();
			}
		}
	}
	
	private static Encounter encounterOfObs(String obsUuid) {
		Obs obs = Context.getObsService().getObsByUuid(obsUuid);
		return obs == null ? null : obs.getEncounter();
	}
	
	private static String personUuid(PersonName name) {
		return name == null || name.getPerson() == null ? null : name.getPerson().getUuid();
	}
	
	private static String personUuid(PersonAddress address) {
		return address == null || address.getPerson() == null ? null : address.getPerson().getUuid();
	}
	
	private static String personUuid(PersonAttribute attribute) {
		return attribute == null || attribute.getPerson() == null ? null : attribute.getPerson().getUuid();
	}
	
	private static boolean matches(String classname, Class<?> type) {
		return type.getName().equals(classname);
	}
}
