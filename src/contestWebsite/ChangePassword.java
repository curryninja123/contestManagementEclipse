/*
 * Component of GAE Project for TMSCA Contest Automation
 * Copyright (C) 2013 Sushain Cherivirala
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see [http://www.gnu.org/licenses/].
 */

package contestWebsite;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;

import util.BaseHttpServlet;
import util.Pair;
import util.Password;
import util.UserCookie;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;

@SuppressWarnings({"serial", "unused"})
public class ChangePassword extends BaseHttpServlet {
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		VelocityEngine ve = new VelocityEngine();
		ve.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, "html/pages, html/snippets");
		ve.init();
		VelocityContext context = new VelocityContext();
		Pair<Entity, UserCookie> infoAndCookie = init(context, req);

		UserCookie userCookie = infoAndCookie.y;
		boolean loggedIn = (boolean) context.get("loggedIn");

		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

		if (loggedIn && !userCookie.isAdmin()) {
			context.put("updated", req.getParameter("updated"));
			context.put("passError", req.getParameter("passError"));
			context.put("confPassError", req.getParameter("confPassError"));

			close(context, ve.getTemplate("changePassword.html"), resp);
		}
		else {
			resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User account required for that operation");
		}
	}

	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		UserCookie userCookie = UserCookie.getCookie(req);
		Entity user = userCookie != null ? userCookie.authenticateUser() : null;
		boolean loggedIn = userCookie != null && user != null;

		if (loggedIn) {
			Map<String, String[]> params = req.getParameterMap();
			String currentPass = params.get("currentPass")[0];
			String newPassword = params.get("newPass")[0];
			String confPassword = params.get("confNewPass")[0];
			if (!newPassword.equals(confPassword)) {
				resp.sendRedirect("/changePass?confPassError=1");
			}
			else {
				DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
				Query query = new Query("user").setFilter(new FilterPredicate("user-id", FilterOperator.EQUAL, userCookie.getUsername()));
				List<Entity> users = datastore.prepare(query).asList(FetchOptions.Builder.withLimit(1));
				if (users.size() < 1) {
					resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User account required for that operation");
				}
				else {
					Entity userEntity = users.get(0);
					String hash = (String) userEntity.getProperty("hash");
					String salt = (String) userEntity.getProperty("salt");
					try {
						if (Password.check(currentPass, salt + "$" + hash)) {
							String newHash = Password.getSaltedHash(newPassword);
							Cookie cookie = new Cookie("user-id", URLEncoder.encode(userCookie.getUsername() + "$" + newHash.split("\\$")[1], "UTF-8"));
							resp.addCookie(cookie);

							user.setProperty("salt", newHash.split("\\$")[0]);
							user.setProperty("hash", newHash.split("\\$")[1]);
							datastore.put(user);
							resp.sendRedirect("/changePass?updated=1");
						}
						else {
							resp.sendRedirect("/changePass?passError=1");
						}
					}
					catch (Exception e) {
						e.printStackTrace();
						resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.toString());
					}
				}
			}
		}
		else {
			resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User account required for that operation");
		}
	}
}
