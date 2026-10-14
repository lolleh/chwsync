/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync;

/**
 * Constants shared across the CHW module.
 * <p>
 * The module's whole reason to exist is the sync feed, so most of what is configured here describes
 * the shape of that feed: which record types travel over it, how much of it a device may ask for in
 * one round trip, and how long a device's bearer token survives.
 */
public class ChwSyncConstants {
	
	public static final String MODULE_ID = "chwsync";
	
	/**
	 * Root of every REST endpoint exposed by this module, relative to the DispatcherServlet. Core
	 * mounts that servlet at {@code /ws/*}, so this value has to be repeated in the public URL -
	 * {@code /ws}{@code /chwsync/v1} - not this value on its own.
	 */
	public static final String REST_NAMESPACE = "/" + MODULE_ID + "/v1";
	
	/** The namespace as clients see it, including the DispatcherServlet's own {@code /ws} mount. */
	public static final String REST_NAMESPACE_URL = "/ws" + REST_NAMESPACE;
	
	// Privileges
	
	/**
	 * Grants reads and writes through the sync feed. This is the privilege a CHW account carries, and
	 * it is deliberately the only one the mobile clients need.
	 */
	public static final String SYNC_PRIVILEGE = "Sync CHW Data";
	
	/** Grants device registration, revocation and the dashboard's cross-CHW aggregates. */
	public static final String MANAGE_DEVICES_PRIVILEGE = "Manage CHW Devices";
	
	// Global properties
	
	/** How many changes a single sync pull returns when the client does not ask for a size. */
	public static final String GP_SYNC_PAGE_SIZE = MODULE_ID + ".syncPageSize";
	
	/** Hard ceiling on a single sync pull, so one greedy device cannot exhaust memory. */
	public static final String GP_SYNC_MAX_PAGE_SIZE = MODULE_ID + ".syncMaxPageSize";
	
	/** Hard ceiling on the number of changes accepted in a single push batch. */
	public static final String GP_SYNC_MAX_PUSH_BATCH = MODULE_ID + ".syncMaxPushBatch";
	
	/**
	 * How long a device bearer token stays valid, in hours. Long by default because a CHW may be
	 * offline in the field for weeks; revocation is the real control, not expiry.
	 */
	public static final String GP_TOKEN_TTL_HOURS = MODULE_ID + ".tokenTtlHours";
	
	/**
	 * Identifier type used for the CHW-assigned patient number when the client does not supply an
	 * identifier. Empty by default, which means "let core assign whatever it does today".
	 */
	public static final String GP_CHW_IDENTIFIER_TYPE = MODULE_ID + ".identifierType";
	
	// Defaults
	
	public static final int DEFAULT_SYNC_PAGE_SIZE = 200;
	
	public static final int DEFAULT_SYNC_MAX_PAGE_SIZE = 1000;
	
	public static final int DEFAULT_SYNC_MAX_PUSH_BATCH = 200;
	
	public static final int DEFAULT_TOKEN_TTL_HOURS = 24 * 30;
	
	/** Name of the application data directory sub-folder the module keeps its configuration in. */
	public static final String MODULE_CONFIG_DIR = MODULE_ID;
	
	/**
	 * Bearer token scheme. Clients send {@code Authorization: Bearer <token>}; the value is opaque and
	 * only its SHA-256 hash is ever stored.
	 */
	public static final String AUTH_HEADER = "Authorization";
	
	public static final String AUTH_HEADER_PREFIX = "Bearer ";
	
	private ChwSyncConstants() {
		// static utility class
	}
}
