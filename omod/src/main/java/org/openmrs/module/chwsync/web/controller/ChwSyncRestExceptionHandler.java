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

import java.util.LinkedHashMap;
import java.util.Map;

import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.APIException;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.module.chwsync.web.controller.BaseSyncController.ForbiddenException;
import org.openmrs.module.chwsync.web.controller.BaseSyncController.NotFoundException;
import org.openmrs.module.chwsync.web.controller.BaseSyncController.UnauthenticatedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the failures the sync endpoints can raise into stable HTTP status codes and one consistent
 * JSON error body.
 * <p>
 * The distinction a client acts on is between "you are not logged in" (get a token and retry), "you
 * are logged in but not allowed" (do not retry), "this request is malformed" (fix and retry) and
 * "the server broke" (report it). Collapsing those into a single 500 is what makes a sync client
 * unable to tell whether to back off or keep trying.
 */
@RestControllerAdvice(basePackages = "org.openmrs.module.chwsync.web.controller")
public class ChwSyncRestExceptionHandler {
	
	private static final Logger log = LoggerFactory.getLogger(ChwSyncRestExceptionHandler.class);
	
	@ExceptionHandler(UnauthenticatedException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public Map<String, Object> handleUnauthenticated(UnauthenticatedException e) {
		return error("unauthenticated", e.getMessage());
	}
	
	@ExceptionHandler(ContextAuthenticationException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public Map<String, Object> handleContextAuthentication(ContextAuthenticationException e) {
		return error("unauthenticated", "The username or password is incorrect.");
	}
	
	@ExceptionHandler(ForbiddenException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public Map<String, Object> handleForbidden(ForbiddenException e) {
		return error("forbidden", e.getMessage());
	}
	
	@ExceptionHandler(APIAuthenticationException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public Map<String, Object> handleApiAuthentication(APIAuthenticationException e) {
		return error("forbidden", e.getMessage());
	}
	
	@ExceptionHandler(NotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, Object> handleNotFound(NotFoundException e) {
		return error("not_found", e.getMessage());
	}
	
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> handleBadRequest(IllegalArgumentException e) {
		return error("invalid_request", e.getMessage());
	}
	
	/**
	 * A core API refusal. These are usually the caller's fault - a missing field, an identifier that is
	 * already taken - so they are answered as a bad request rather than blamed on the server.
	 */
	@ExceptionHandler(APIException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> handleApiException(APIException e) {
		return error("api_error", e.getMessage());
	}
	
	/**
	 * Catch-all. The message is deliberately generic: an unexpected exception message can carry
	 * identifiers or SQL that should not reach a handset. The detail is in the server log.
	 */
	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public Map<String, Object> handleUnexpected(Exception e) {
		log.error("Unhandled exception in a CHW sync request", e);
		return error("server_error", "The request could not be completed. See the server log for details.");
	}
	
	private static Map<String, Object> error(String code, String message) {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("code", code);
		body.put("message", message == null ? code : message);
		return body;
	}
}
