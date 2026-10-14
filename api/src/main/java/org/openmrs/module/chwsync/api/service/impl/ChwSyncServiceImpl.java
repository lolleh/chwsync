/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.chwsync.ChwSyncConstants;
import org.openmrs.module.chwsync.api.dao.ChwSyncRecordDao;
import org.openmrs.module.chwsync.api.dao.SyncDao;
import org.openmrs.module.chwsync.api.dto.SyncDtos;
import org.openmrs.module.chwsync.api.model.Appointment;
import org.openmrs.module.chwsync.api.model.Household;
import org.openmrs.module.chwsync.api.model.HouseholdMember;
import org.openmrs.module.chwsync.api.model.Referral;
import org.openmrs.module.chwsync.api.model.SyncChange;
import org.openmrs.module.chwsync.api.service.ChwSyncService;
import org.openmrs.module.chwsync.api.sync.ChangeOperation;
import org.openmrs.module.chwsync.api.sync.Json;
import org.openmrs.module.chwsync.api.sync.SyncEntityType;
import org.openmrs.module.chwsync.api.sync.codec.EncounterCodec;
import org.openmrs.module.chwsync.api.sync.codec.PatientCodec;
import org.openmrs.module.chwsync.api.sync.codec.RecordCodecs;
import org.openmrs.module.chwsync.api.sync.codec.VisitCodec;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link ChwSyncService}.
 * <p>
 * The engine is deliberately built around two properties, because a CHW's connection is not
 * reliable:
 * <ul>
 * <li><b>A pull never loses a change.</b> The cursor is a server timestamp and each pull re-reads a
 * short window before it, so a record whose writing transaction committed just after the last pull
 * is still delivered. Re-delivery is harmless because clients upsert on uuid.</li>
 * <li><b>A push is never applied twice.</b> Every client change carries its own id; the outcome is
 * recorded under that id and replayed on retry instead of being applied again.</li>
 * </ul>
 * Conflicts are reported, not silently resolved. When the client's {@code baseVersion} is not the
 * record's current version the server refuses the write and returns its own copy, so the decision
 * to overwrite a facility-side edit stays with a person rather than with whichever device synced
 * last.
 */
@Transactional
public class ChwSyncServiceImpl extends BaseOpenmrsService implements ChwSyncService {
	
	/**
	 * How far before the client's cursor each pull re-reads. Long enough to cover a transaction that
	 * was mid-commit during the previous pull, short enough that the overlap stays small.
	 */
	private static final long PULL_WINDOW_MS = 5_000L;
	
	private static final String DELETE_REASON = "Deleted from a CHW device";
	
	private SyncDao syncDao;
	
	private ChwSyncRecordDao recordDao;
	
	public void setSyncDao(SyncDao syncDao) {
		this.syncDao = syncDao;
	}
	
	public void setRecordDao(ChwSyncRecordDao recordDao) {
		this.recordDao = recordDao;
	}
	
	// ---------------------------------------------------------------- pull
	
	@Override
	public SyncDtos.PullResponse pull(Date since, List<SyncEntityType> types, Integer limit) {
		int pageSize = effectiveLimit(limit);
		Date now = new Date();
		Date from = since == null ? new Date(0L) : new Date(since.getTime() - PULL_WINDOW_MS);
		
		List<SyncChange> found = syncDao.findChanges(from, now, types, pageSize + 1);
		boolean hasMore = found.size() > pageSize;
		if (hasMore) {
			found = new ArrayList<SyncChange>(found.subList(0, pageSize));
		}
		
		SyncDtos.PullResponse response = new SyncDtos.PullResponse();
		response.setCursor(Json.formatDateTime(now));
		response.setHasMore(hasMore);
		response.setChanges(toChanges(found));
		return response;
	}
	
	/**
	 * Collapses repeats of the same record to its latest change, then serializes the record once.
	 */
	private List<SyncDtos.Change> toChanges(List<SyncChange> changes) {
		Map<String, SyncChange> latestByRecord = new LinkedHashMap<String, SyncChange>();
		for (SyncChange change : changes) {
			if (change.getEntityType() == null || change.getEntityUuid() == null) {
				continue;
			}
			latestByRecord.put(change.getEntityType().getWireName() + "|" + change.getEntityUuid(), change);
		}
		
		List<SyncDtos.Change> result = new ArrayList<SyncDtos.Change>();
		for (SyncChange change : latestByRecord.values()) {
			SyncDtos.Change dto = new SyncDtos.Change();
			dto.setRevision(change.getRevision());
			dto.setEntityType(change.getEntityType().getWireName());
			dto.setUuid(change.getEntityUuid());
			dto.setOperation(
			    change.getOperation() == null ? ChangeOperation.UPDATE.getWireName() : change.getOperation().getWireName());
			dto.setChangedAt(Json.formatDateTime(change.getChangedAt()));
			
			if (change.getOperation() == ChangeOperation.DELETE) {
				dto.setDeleted(true);
			} else {
				Map<String, Object> data = fetch(change.getEntityType(), change.getEntityUuid());
				if (data == null || Boolean.TRUE.equals(data.get("voided"))) {
					dto.setDeleted(true);
				} else {
					dto.setData(data);
					dto.setVersion(versionOf(data));
				}
			}
			result.add(dto);
		}
		return result;
	}
	
	@Override
	public Map<String, Object> fetch(SyncEntityType type, String uuid) {
		if (type == null || StringUtils.isBlank(uuid)) {
			return null;
		}
		switch (type) {
			case PATIENT:
				Patient patient = Context.getPatientService().getPatientByUuid(uuid);
				return patient == null ? null : PatientCodec.toMap(patient);
			case VISIT:
				Visit visit = Context.getVisitService().getVisitByUuid(uuid);
				return visit == null ? null : VisitCodec.toMap(visit);
			case ENCOUNTER:
				Encounter encounter = Context.getEncounterService().getEncounterByUuid(uuid);
				return encounter == null ? null : EncounterCodec.toMap(encounter);
			case HOUSEHOLD:
				Household household = recordDao.findHouseholdByUuid(uuid);
				return household == null ? null : RecordCodecs.toMap(household);
			case APPOINTMENT:
				Appointment appointment = recordDao.findAppointmentByUuid(uuid);
				return appointment == null ? null : RecordCodecs.toMap(appointment);
			case REFERRAL:
				Referral referral = recordDao.findReferralByUuid(uuid);
				return referral == null ? null : RecordCodecs.toMap(referral);
			default:
				return null;
		}
	}
	
	// ---------------------------------------------------------------- push
	
	@Override
	public SyncDtos.PushResponse push(SyncDtos.PushRequest request, String deviceUuid) {
		if (request == null || request.getChanges() == null || request.getChanges().isEmpty()) {
			throw new APIException("Push contains no changes");
		}
		int maxBatch = maxPushBatch();
		if (request.getChanges().size() > maxBatch) {
			throw new APIException("Push contains " + request.getChanges().size() + " changes, the maximum is " + maxBatch);
		}
		
		SyncDtos.PushResponse response = new SyncDtos.PushResponse();
		Date now = new Date();
		for (SyncDtos.PushItem item : request.getChanges()) {
			SyncDtos.PushResult result = processItem(item, deviceUuid, now);
			response.getResults().add(result);
			switch (result.getStatus()) {
				case SyncDtos.PushResult.STATUS_APPLIED:
					response.setApplied(response.getApplied() + 1);
					break;
				case SyncDtos.PushResult.STATUS_CONFLICT:
					response.setConflicts(response.getConflicts() + 1);
					break;
				default:
					response.setRejected(response.getRejected() + 1);
					break;
			}
		}
		response.setCursor(Json.formatDateTime(new Date()));
		return response;
	}
	
	private SyncDtos.PushResult processItem(SyncDtos.PushItem item, String deviceUuid, Date now) {
		SyncDtos.PushResult result = baseResult(item);
		
		// Replay guard: an item the server has already handled produces its original outcome, never a
		// second write. This is what makes a retried push after a dropped response safe.
		if (StringUtils.isNotBlank(item.getClientChangeId())) {
			String prior = syncDao.findClientChangeResult(item.getClientChangeId());
			if (prior != null) {
				result.setStatus(SyncDtos.PushResult.STATUS_REPLAYED);
				result.setMessage("Change already processed");
				return result;
			}
		}
		
		SyncEntityType type = SyncEntityType.fromWireName(item.getEntityType());
		ChangeOperation operation = ChangeOperation.fromWireName(item.getOperation());
		if (type == null) {
			return reject(result, "Unknown entityType: " + item.getEntityType());
		}
		if (operation == null) {
			return reject(result, "Unknown operation: " + item.getOperation());
		}
		if (StringUtils.isBlank(item.getUuid())) {
			return reject(result, "Every change must carry the record uuid");
		}
		
		try {
			applyItem(type, operation, item, deviceUuid, now, result);
		}
		catch (APIException e) {
			return reject(result, e.getMessage());
		}
		catch (RuntimeException e) {
			return reject(result, "Could not apply change: " + e.getMessage());
		}
		
		if (SyncDtos.PushResult.STATUS_APPLIED.equals(result.getStatus())
		        && StringUtils.isNotBlank(item.getClientChangeId())) {
			syncDao.recordClientChange(item.getClientChangeId(), deviceUuid, result.getStatus(), new Date());
		}
		return result;
	}
	
	private void applyItem(SyncEntityType type, ChangeOperation operation, SyncDtos.PushItem item, String deviceUuid,
	        Date now, SyncDtos.PushResult result) {
		switch (type) {
			case PATIENT:
				applyPatient(operation, item, now, result);
				break;
			case VISIT:
				applyVisit(operation, item, now, result);
				break;
			case ENCOUNTER:
				applyEncounter(operation, item, now, result);
				break;
			case HOUSEHOLD:
				applyHousehold(operation, item, deviceUuid, now, result);
				break;
			case APPOINTMENT:
				applyAppointment(operation, item, deviceUuid, now, result);
				break;
			case REFERRAL:
				applyReferral(operation, item, deviceUuid, now, result);
				break;
			default:
				reject(result, "Unsupported entityType");
		}
	}
	
	// ---------------------------------------------------------------- apply: core records
	
	private void applyPatient(ChangeOperation operation, SyncDtos.PushItem item, Date now, SyncDtos.PushResult result) {
		Patient existing = Context.getPatientService().getPatientByUuid(item.getUuid());
		if (operation == ChangeOperation.DELETE) {
			if (existing == null) {
				applied(result, null);
				return;
			}
			if (conflicts(item, PatientCodec.version(existing), result, PatientCodec.toMap(existing))) {
				return;
			}
			Patient voided = Context.getPatientService().voidPatient(existing, DELETE_REASON);
			applied(result, PatientCodec.version(voided));
			return;
		}
		
		if (existing == null) {
			if (item.getBaseVersion() != null) {
				reject(result, "Patient does not exist on the server");
				return;
			}
			Patient patient = PatientCodec.newPatient();
			PatientCodec.apply(patient, item.getPayload(), currentUser());
			Patient saved = Context.getPatientService().savePatient(patient);
			applied(result, PatientCodec.version(saved));
			return;
		}
		
		if (item.getBaseVersion() == null) {
			conflict(result, PatientCodec.version(existing), PatientCodec.toMap(existing));
			return;
		}
		if (conflicts(item, PatientCodec.version(existing), result, PatientCodec.toMap(existing))) {
			return;
		}
		PatientCodec.apply(existing, item.getPayload(), currentUser());
		Patient saved = Context.getPatientService().savePatient(existing);
		applied(result, PatientCodec.version(saved));
	}
	
	private void applyVisit(ChangeOperation operation, SyncDtos.PushItem item, Date now, SyncDtos.PushResult result) {
		Visit existing = Context.getVisitService().getVisitByUuid(item.getUuid());
		if (operation == ChangeOperation.DELETE) {
			if (existing == null) {
				applied(result, null);
				return;
			}
			if (conflicts(item, VisitCodec.version(existing), result, VisitCodec.toMap(existing))) {
				return;
			}
			Visit voided = Context.getVisitService().voidVisit(existing, DELETE_REASON);
			applied(result, VisitCodec.version(voided));
			return;
		}
		
		if (existing == null) {
			if (item.getBaseVersion() != null) {
				reject(result, "Visit does not exist on the server");
				return;
			}
			Visit visit = new Visit();
			VisitCodec.apply(visit, item.getPayload(), currentUser());
			Visit saved = Context.getVisitService().saveVisit(visit);
			applied(result, VisitCodec.version(saved));
			return;
		}
		
		if (item.getBaseVersion() == null) {
			conflict(result, VisitCodec.version(existing), VisitCodec.toMap(existing));
			return;
		}
		if (conflicts(item, VisitCodec.version(existing), result, VisitCodec.toMap(existing))) {
			return;
		}
		VisitCodec.apply(existing, item.getPayload(), currentUser());
		Visit saved = Context.getVisitService().saveVisit(existing);
		applied(result, VisitCodec.version(saved));
	}
	
	private void applyEncounter(ChangeOperation operation, SyncDtos.PushItem item, Date now, SyncDtos.PushResult result) {
		Encounter existing = Context.getEncounterService().getEncounterByUuid(item.getUuid());
		if (operation == ChangeOperation.DELETE) {
			if (existing == null) {
				applied(result, null);
				return;
			}
			if (conflicts(item, EncounterCodec.version(existing), result, EncounterCodec.toMap(existing))) {
				return;
			}
			Encounter voided = Context.getEncounterService().voidEncounter(existing, DELETE_REASON);
			applied(result, EncounterCodec.version(voided));
			return;
		}
		
		if (existing == null) {
			if (item.getBaseVersion() != null) {
				reject(result, "Encounter does not exist on the server");
				return;
			}
			Encounter encounter = new Encounter();
			EncounterCodec.apply(encounter, item.getPayload(), currentUser());
			Encounter saved = Context.getEncounterService().saveEncounter(encounter);
			applied(result, EncounterCodec.version(saved));
			return;
		}
		
		if (item.getBaseVersion() == null) {
			conflict(result, EncounterCodec.version(existing), EncounterCodec.toMap(existing));
			return;
		}
		if (conflicts(item, EncounterCodec.version(existing), result, EncounterCodec.toMap(existing))) {
			return;
		}
		EncounterCodec.apply(existing, item.getPayload(), currentUser());
		Encounter saved = Context.getEncounterService().saveEncounter(existing);
		applied(result, EncounterCodec.version(saved));
	}
	
	// ---------------------------------------------------------------- apply: module records
	
	private void applyHousehold(ChangeOperation operation, SyncDtos.PushItem item, String deviceUuid, Date now,
	        SyncDtos.PushResult result) {
		Household existing = recordDao.findHouseholdByUuid(item.getUuid());
		if (operation == ChangeOperation.DELETE) {
			if (existing == null) {
				applied(result, null);
				return;
			}
			if (conflicts(item, Json.version(existing.version()), result, RecordCodecs.toMap(existing))) {
				return;
			}
			voidHousehold(existing, now);
			appendChange(SyncEntityType.HOUSEHOLD, item.getUuid(), ChangeOperation.DELETE, deviceUuid, now);
			applied(result, Json.version(existing.version()));
			return;
		}
		
		if (existing == null && item.getBaseVersion() != null) {
			reject(result, "Household does not exist on the server");
			return;
		}
		if (existing != null && item.getBaseVersion() == null) {
			conflict(result, Json.version(existing.version()), RecordCodecs.toMap(existing));
			return;
		}
		if (existing != null && conflicts(item, Json.version(existing.version()), result, RecordCodecs.toMap(existing))) {
			return;
		}
		
		Household household = existing != null ? existing : new Household();
		RecordCodecs.apply(household, item.getPayload());
		User user = currentUser();
		if (existing == null) {
			household.setCreator(user == null ? null : user.getUserId());
			household.setDateCreated(now);
			household.setChangedBy(user == null ? null : user.getUserId());
			household.setDateChanged(now);
			recordDao.insertHousehold(household);
			// Membership rows need the generated household id, so they are written after the insert.
			persistMembers(household, now);
		} else {
			household.setChangedBy(user == null ? null : user.getUserId());
			household.setDateChanged(now);
			recordDao.updateHousehold(household);
			recordDao.clearMembers(household.getHouseholdId());
			persistMembers(household, now);
		}
		appendChange(SyncEntityType.HOUSEHOLD, household.getUuid(), ChangeOperation.UPDATE, deviceUuid, now);
		Household reloaded = recordDao.findHouseholdByUuid(household.getUuid());
		applied(result, Json.version(reloaded.version()));
	}
	
	private void persistMembers(Household household, Date now) {
		Integer householdId = recordDao.findHouseholdByUuid(household.getUuid()).getHouseholdId();
		for (HouseholdMember member : household.getMembers()) {
			member.setHouseholdId(householdId);
			member.setDateCreated(now);
			member.setDateChanged(now);
			recordDao.insertMember(member);
		}
	}
	
	private void voidHousehold(Household household, Date now) {
		User user = currentUser();
		household.setVoided(true);
		household.setVoidedBy(user == null ? null : user.getUserId());
		household.setDateVoided(now);
		household.setDateChanged(now);
		household.setVoidReason(DELETE_REASON);
		recordDao.updateHousehold(household);
	}
	
	private void applyAppointment(ChangeOperation operation, SyncDtos.PushItem item, String deviceUuid, Date now,
	        SyncDtos.PushResult result) {
		Appointment existing = recordDao.findAppointmentByUuid(item.getUuid());
		if (operation == ChangeOperation.DELETE) {
			if (existing == null) {
				applied(result, null);
				return;
			}
			if (conflicts(item, Json.version(existing.version()), result, RecordCodecs.toMap(existing))) {
				return;
			}
			voidAppointment(existing, now);
			appendChange(SyncEntityType.APPOINTMENT, item.getUuid(), ChangeOperation.DELETE, deviceUuid, now);
			applied(result, Json.version(existing.version()));
			return;
		}
		if (existing == null && item.getBaseVersion() != null) {
			reject(result, "Appointment does not exist on the server");
			return;
		}
		if (existing != null && item.getBaseVersion() == null) {
			conflict(result, Json.version(existing.version()), RecordCodecs.toMap(existing));
			return;
		}
		if (existing != null && conflicts(item, Json.version(existing.version()), result, RecordCodecs.toMap(existing))) {
			return;
		}
		
		Appointment appointment = existing != null ? existing : new Appointment();
		RecordCodecs.apply(appointment, item.getPayload());
		User user = currentUser();
		appointment.setChangedBy(user == null ? null : user.getUserId());
		appointment.setDateChanged(now);
		if (existing == null) {
			appointment.setCreator(user == null ? null : user.getUserId());
			appointment.setDateCreated(now);
			recordDao.insertAppointment(appointment);
		} else {
			recordDao.updateAppointment(appointment);
		}
		appendChange(SyncEntityType.APPOINTMENT, appointment.getUuid(), ChangeOperation.UPDATE, deviceUuid, now);
		Appointment reloaded = recordDao.findAppointmentByUuid(appointment.getUuid());
		applied(result, Json.version(reloaded.version()));
	}
	
	private void voidAppointment(Appointment appointment, Date now) {
		User user = currentUser();
		appointment.setVoided(true);
		appointment.setVoidedBy(user == null ? null : user.getUserId());
		appointment.setDateVoided(now);
		appointment.setDateChanged(now);
		appointment.setVoidReason(DELETE_REASON);
		recordDao.updateAppointment(appointment);
	}
	
	private void applyReferral(ChangeOperation operation, SyncDtos.PushItem item, String deviceUuid, Date now,
	        SyncDtos.PushResult result) {
		Referral existing = recordDao.findReferralByUuid(item.getUuid());
		if (operation == ChangeOperation.DELETE) {
			if (existing == null) {
				applied(result, null);
				return;
			}
			if (conflicts(item, Json.version(existing.version()), result, RecordCodecs.toMap(existing))) {
				return;
			}
			voidReferral(existing, now);
			appendChange(SyncEntityType.REFERRAL, item.getUuid(), ChangeOperation.DELETE, deviceUuid, now);
			applied(result, Json.version(existing.version()));
			return;
		}
		if (existing == null && item.getBaseVersion() != null) {
			reject(result, "Referral does not exist on the server");
			return;
		}
		if (existing != null && item.getBaseVersion() == null) {
			conflict(result, Json.version(existing.version()), RecordCodecs.toMap(existing));
			return;
		}
		if (existing != null && conflicts(item, Json.version(existing.version()), result, RecordCodecs.toMap(existing))) {
			return;
		}
		
		Referral referral = existing != null ? existing : new Referral();
		RecordCodecs.apply(referral, item.getPayload());
		User user = currentUser();
		referral.setChangedBy(user == null ? null : user.getUserId());
		referral.setDateChanged(now);
		if (existing == null) {
			referral.setCreator(user == null ? null : user.getUserId());
			referral.setDateCreated(now);
			recordDao.insertReferral(referral);
		} else {
			recordDao.updateReferral(referral);
		}
		appendChange(SyncEntityType.REFERRAL, referral.getUuid(), ChangeOperation.UPDATE, deviceUuid, now);
		Referral reloaded = recordDao.findReferralByUuid(referral.getUuid());
		applied(result, Json.version(reloaded.version()));
	}
	
	private void voidReferral(Referral referral, Date now) {
		User user = currentUser();
		referral.setVoided(true);
		referral.setVoidedBy(user == null ? null : user.getUserId());
		referral.setDateVoided(now);
		referral.setDateChanged(now);
		referral.setVoidReason(DELETE_REASON);
		recordDao.updateReferral(referral);
	}
	
	private void appendChange(SyncEntityType type, String uuid, ChangeOperation operation, String deviceUuid, Date now) {
		User user = currentUser();
		syncDao.appendChange(type, uuid, operation, now, user == null ? null : user.getUserId(), deviceUuid, null);
	}
	
	// ---------------------------------------------------------------- result helpers
	
	private SyncDtos.PushResult baseResult(SyncDtos.PushItem item) {
		SyncDtos.PushResult result = new SyncDtos.PushResult();
		result.setClientChangeId(item.getClientChangeId());
		result.setEntityType(item.getEntityType());
		result.setUuid(item.getUuid());
		return result;
	}
	
	private SyncDtos.PushResult reject(SyncDtos.PushResult result, String message) {
		result.setStatus(SyncDtos.PushResult.STATUS_REJECTED);
		result.setMessage(message);
		return result;
	}
	
	private void applied(SyncDtos.PushResult result, Long serverVersion) {
		result.setStatus(SyncDtos.PushResult.STATUS_APPLIED);
		result.setServerVersion(serverVersion);
	}
	
	private void conflict(SyncDtos.PushResult result, Long serverVersion, Map<String, Object> data) {
		result.setStatus(SyncDtos.PushResult.STATUS_CONFLICT);
		result.setServerVersion(serverVersion);
		result.setData(data);
		result.setMessage("The record changed on the server since this device last saw it");
	}
	
	/**
	 * @return true when a conflict was reported; also fills the result in that case
	 */
	private boolean conflicts(SyncDtos.PushItem item, Long serverVersion, SyncDtos.PushResult result,
	        Map<String, Object> serverData) {
		if (item.getBaseVersion() == null) {
			return false;
		}
		if (serverVersion == null || !item.getBaseVersion().equals(serverVersion)) {
			conflict(result, serverVersion, serverData);
			return true;
		}
		return false;
	}
	
	private static Long versionOf(Map<String, Object> data) {
		Object value = data == null ? null : data.get("version");
		if (value instanceof Number) {
			return Long.valueOf(((Number) value).longValue());
		}
		return null;
	}
	
	private static User currentUser() {
		try {
			return Context.getAuthenticatedUser();
		}
		catch (RuntimeException e) {
			return null;
		}
	}
	
	// ---------------------------------------------------------------- core events
	
	@Override
	public void recordCoreChange(SyncEntityType type, String uuid, ChangeOperation operation, Date when) {
		if (type == null || StringUtils.isBlank(uuid) || operation == null) {
			return;
		}
		User user = currentUser();
		syncDao.appendChange(type, uuid, operation, when == null ? new Date() : when, user == null ? null : user.getUserId(),
		    null, null);
	}
	
	// ---------------------------------------------------------------- configuration
	
	@Override
	public int defaultPageSize() {
		return clamp(readInt(ChwSyncConstants.GP_SYNC_PAGE_SIZE, ChwSyncConstants.DEFAULT_SYNC_PAGE_SIZE),
		    ChwSyncConstants.DEFAULT_SYNC_MAX_PAGE_SIZE);
	}
	
	@Override
	public int maxPushBatch() {
		return readInt(ChwSyncConstants.GP_SYNC_MAX_PUSH_BATCH, ChwSyncConstants.DEFAULT_SYNC_MAX_PUSH_BATCH);
	}
	
	private int effectiveLimit(Integer requested) {
		int max = clamp(readInt(ChwSyncConstants.GP_SYNC_MAX_PAGE_SIZE, ChwSyncConstants.DEFAULT_SYNC_MAX_PAGE_SIZE),
		    ChwSyncConstants.DEFAULT_SYNC_MAX_PAGE_SIZE);
		if (requested == null || requested.intValue() <= 0) {
			return defaultPageSize();
		}
		return Math.min(requested.intValue(), max);
	}
	
	private int readInt(String property, int fallback) {
		String value = Context.getAdministrationService().getGlobalProperty(property);
		if (StringUtils.isNotBlank(value)) {
			try {
				return Integer.parseInt(value.trim());
			}
			catch (NumberFormatException e) {
				// a bad setting degrades to the default rather than failing the whole sync
			}
		}
		return fallback;
	}
	
	private static int clamp(int value, int max) {
		if (value <= 0) {
			return max;
		}
		return Math.min(value, max);
	}
}
