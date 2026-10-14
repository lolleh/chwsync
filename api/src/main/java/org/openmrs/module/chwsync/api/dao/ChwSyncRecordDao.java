/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.api.dao;

import java.util.Date;
import java.util.List;

import org.openmrs.module.chwsync.api.model.Appointment;
import org.openmrs.module.chwsync.api.model.Household;
import org.openmrs.module.chwsync.api.model.HouseholdMember;
import org.openmrs.module.chwsync.api.model.Referral;

/**
 * Persistence for this module's own records: households, appointments and referrals.
 */
public interface ChwSyncRecordDao {
	
	Household findHouseholdByUuid(String uuid);
	
	List<Household> listHouseholds(boolean includeVoided);
	
	List<Household> findHouseholdsForPatient(Integer patientId);
	
	void insertHousehold(Household household);
	
	void updateHousehold(Household household);
	
	List<HouseholdMember> findMembers(Integer householdId);
	
	void clearMembers(Integer householdId);
	
	void insertMember(HouseholdMember member);
	
	Appointment findAppointmentByUuid(String uuid);
	
	List<Appointment> findAppointments(Integer patientId, String status, Date from, Date to, boolean includeVoided);
	
	void insertAppointment(Appointment appointment);
	
	void updateAppointment(Appointment appointment);
	
	Referral findReferralByUuid(String uuid);
	
	List<Referral> findReferrals(Integer patientId, String status, boolean includeVoided);
	
	void insertReferral(Referral referral);
	
	void updateReferral(Referral referral);
}
