package io.prometheus.wls.rest;
/*
 * Copyright (c) 2017 Oracle and/or its affiliates
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Enumeration;

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

/**
 * An abstract servlet which performs authentication by forwarding all pertinent headers between the client
 * and the WLS RESTful Management services, thus using that service's security.
 */
abstract public class PassThroughAuthenticationServlet extends HttpServlet {
    private WebClientFactory webClientFactory;

    PassThroughAuthenticationServlet(WebClientFactory webClientFactory) {
        this.webClientFactory = webClientFactory;
    }

    private WebClient createWebClient(HttpServletRequest req) {
        LiveConfiguration.setServer(req.getServerName(), req.getServerPort());
        final WebClient webClient = webClientFactory.createClient(LiveConfiguration.getQueryUrl());
        webClient.addHeader("X-Requested-By", "rest-exporter");

        forwardRequestHeaders(req, webClient);
        return webClient;
    }

    private void forwardRequestHeaders(HttpServletRequest req, WebClient webClient) {
        webClient.establishSession(req.getHeader(ServletConstants.AUTHENTICATION_HEADER), getSessionCookie(req));
     }

    private String getSessionCookie(HttpServletRequest req) {
        for (Enumeration<String> each = req.getHeaders(ServletConstants.COOKIE_HEADER); each.hasMoreElements();) {
            String sessionCookie = ExporterSession.getSessionCookie(each.nextElement());
            if (sessionCookie != null) return sessionCookie;
        }
        return null;
    }

    /**
     * Performs a servlet action, wrappering it with authentication handling.
     *
     * This involves creating an object which can make http calls to the RESTful services, configuring it to
     * include the authentication header, if any, received from client. Any authentication errors from
     * the services will be returned to the client.
     *
     * @param req the servlet request
     * @param resp the servlet response
     * @param authenticatedService an which actually performs calls to the RESTful services using the supplied client
     *
     * @throws IOException if an error occurs in the web client
     * @throws ServletException if some other fatal error occurs
     */
    void doWithAuthentication(HttpServletRequest req, HttpServletResponse resp, AuthenticatedService authenticatedService) throws IOException, ServletException {
        try {
            WebClient webClient = createWebClient(req);
            authenticatedService.execute(webClient, req, resp);
            webClient.forwardResponseHeaders(resp);
        } catch (ForbiddenException e) {
            resp.sendError(SC_FORBIDDEN, "Not authorized");
        } catch (BasicAuthenticationChallengeException e) {
            resp.setHeader("WWW-Authenticate", String.format("Basic realm=\"%s\"", e.getRealm()));
            resp.sendError(SC_UNAUTHORIZED, "Authentication required");
        } finally {
            final HttpSession session = req.getSession(false);
            if (session != null) session.invalidate();
        }
    }

    /**
     * An interface describing the service wrapped by #doWithAuthentication
     */
    interface AuthenticatedService {
        void execute(WebClient webClient, HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException;
    }

}
