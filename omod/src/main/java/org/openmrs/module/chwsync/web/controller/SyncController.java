/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.chwsync.web.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.chwsync.ChwSyncConstants;
import org.openmrs.module.chwsync.api.dto.SyncDtos;
import org.openmrs.module.chwsync.api.service.ChwSyncService;
import org.openmrs.module.chwsync.api.sync.Json;
import org.openmrs.module.chwsync.api.sync.SyncEntityType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The sync feed itself.
 * <p>
 * Both directions are safe to retry: a pull that is cut off mid-response costs nothing but a
 * repeated page, and a push that is retried after the connection dropped is recognised as a replay
 * rather than applied twice. That is deliberate - a CHW works from places where the network comes
 * and goes during a single tap, so a protocol that only behaves on a stable connection is not one
 * they can use.
 */
@RestController
@RequestMapping(ChwSyncConstants.REST_NAMESPACE + "/sync")
public class SyncController extends BaseSyncController {
	
	/**
	 * Returns everything that changed after the client's cursor, up to a page.
	 *
	 * @param since the cursor from the previous pull, or omitted for a first sync
	 * @param types a comma-separated subset of record types, or omitted for all of them
	 * @param limit the page size, clamped to the configured maximum
	 */
	@RequestMapping(value = "/pull", method = RequestMethod.GET)
	public SyncDtos.PullResponse pull(
	        @RequestHeader(value = ChwSyncConstants.AUTH_HEADER, required = false) final String authorization,
	        @RequestParam(value = "since", required = false) final String since,
	        @RequestParam(value = "types", required = false) final String types,
	        @RequestParam(value = "limit", required = false) final Integer limit) {
		return asDevice(authorization, new DeviceWork<SyncDtos.PullResponse>() {
			
			@Override
			public SyncDtos.PullResponse run() {
				return sync().pull(parseSince(since), parseTypes(types), limit);
			}
		});
	}
	
	/**
	 * Applies a batch of the client's changes, answering each one individually so that a single
	 * conflict does not fail the whole batch.
	 */
	@RequestMapping(value = "/push", method = RequestMethod.POST)
	public SyncDtos.PushResponse push(
	        @RequestHeader(value = ChwSyncConstants.AUTH_HEADER, required = false) final String authorization,
	        @RequestBody final SyncDtos.PushRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("A push body is required.");
		}
		return asDevice(authorization, new DeviceWork<SyncDtos.PushResponse>() {
			
			@Override
			public SyncDtos.PushResponse run() {
				try {
					return sync().push(request, StringUtils.trimToNull(request.getDeviceUuid()));
				}
				catch (APIAuthenticationException e) {
					throw forbidden(e);
				}
			}
		});
	}
	
	/** Reads one record in wire form, which is what a client asks for to settle a conflict. */
	@RequestMapping(value = "/record/{type}/{uuid}", method = RequestMethod.GET)
	public Map<String, Object> fetch(
	        @RequestHeader(value = ChwSyncConstants.AUTH_HEADER, required = false) String authorization,
	        @PathVariable("type") String type, @PathVariable("uuid") final String uuid) {
		final SyncEntityType entityType = SyncEntityType.fromWireName(type);
		if (entityType == null) {
			throw new IllegalArgumentException("Unknown record type: " + type);
		}
		return asDevice(authorization, new DeviceWork<Map<String, Object>>() {
			
			@Override
			public Map<String, Object> run() {
				Map<String, Object> record = sync().fetch(entityType, uuid);
				if (record == null) {
					throw new NotFoundException("No " + entityType.getWireName() + " with uuid " + uuid);
				}
				return record;
			}
		});
	}
	
	/** What the server will accept in one call, so a client can size its batches before trying. */
	@RequestMapping(value = "/limits", method = RequestMethod.GET)
	public Map<String, Object> limits(
	        @RequestHeader(value = ChwSyncConstants.AUTH_HEADER, required = false) String authorization) {
		return asDevice(authorization, new DeviceWork<Map<String, Object>>() {
			
			@Override
			public Map<String, Object> run() {
				ChwSyncService service = sync();
				Map<String, Object> limits = Json.map();
				limits.put("pageSize", service.defaultPageSize());
				limits.put("maxPushBatch", service.maxPushBatch());
				limits.put("serverTime", Json.formatDateTime(new Date()));
				return limits;
			}
		});
	}
	
	private static ChwSyncService sync() {
		ChwSyncService service = Context.getService(ChwSyncService.class);
		if (service == null) {
			throw new IllegalStateException("The CHW sync service is not available.");
		}
		return service;
	}
	
	private static Date parseSince(String since) {
		if (StringUtils.isBlank(since)) {
			return null;
		}
		return Json.parseDate(since);
	}
	
	private static List<SyncEntityType> parseTypes(String types) {
		if (StringUtils.isBlank(types)) {
			return null;
		}
		List<SyncEntityType> parsed = new ArrayList<SyncEntityType>();
		for (String token : types.split(",")) {
			String name = token.trim();
			if (name.isEmpty()) {
				continue;
			}
			SyncEntityType type = SyncEntityType.fromWireName(name);
			if (type == null) {
				throw new IllegalArgumentException("Unknown record type: " + name);
			}
			parsed.add(type);
		}
		return parsed;
	}
}
