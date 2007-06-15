/**
 * Copyright (c) 2005-2007, Paul Tuckey
 * All rights reserved.
 * ====================================================================
 * Licensed under the BSD License. Text as follows.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   - Neither the name tuckey.org nor the names of its contributors
 *     may be used to endorse or promote products derived from this
 *     software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 */
package org.tuckey.web.filters.urlrewrite;

import org.tuckey.web.filters.urlrewrite.utils.Log;
import org.tuckey.web.filters.urlrewrite.utils.NumberUtils;
import org.tuckey.web.filters.urlrewrite.utils.ServerNameMatcher;
import org.tuckey.web.filters.urlrewrite.utils.StringUtils;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;

/**
 * Based on the popular and very useful mod_rewrite for apache, UrlRewriteFilter is a Java Web Filter for any J2EE
 * compliant web application server (such as Resin or Tomcat), which allows you to rewrite URLs before they get to your
 * code. It is a very powerful tool just like Apache's mod_rewrite.
 * <p/>
 * The main things it is used for are:
 * <p/>
 * <ul>
 * <li>URL Tidyness - keep URLs tidy irrespective of the underlying technology (JSPs, servlets, struts etc).</li>
 * <li>Browser Detection - Allows you to rewrite URLs based on request HTTP headers (such as "user-agent").</li>
 * <li>Date based rewriting - Allows you to forward or redirect to other URL's based on the date/time.</li>
 * </ul>
 * UrlRewriteFilter uses an xml file, called urlrewrite.xml (lives in the WEB-INF directory), for configuration. Most
 * parameters can be Perl5 style Regular Expressions or Wildcards (i.e. *). This makes it very powerful indeed.
 * <p/>
 * Special thanks to all those who gave patches/feedback especially Vineet Kumar.
 * <p/>
 * Thanks also to Ralf S. Engelschall (www.engelschall.com) the inventor of mod_rewrite.
 * <p/>
 * <p/>
 * <p/>
 * <p/>
 * <p/>
 * todo: user in role throw 403 if no match?
 * <p/>
 * todo: ability to test new conf without having to reload, via status page
 * <p/>
 * todo: allow condition matching on get-param or post-param as well as just parameter
 * <p/>
 * todo: store list of robots and hide jsessionid when a robot also have condition
 * <p/>
 * todo: allow mounting of packages from /xxxyyy/aaa.gif to org.tuckey.xxx.static."aaa.gif" http://wiki.opensymphony.com/pages/viewpage.action?pageId=4476
 * <p/>
 * todo: random condition type
 * 2. A randomized condition, i.e. a condition which is
 * true with a certain probability.
 * 3. A round robin condition, i.e. a condition which is
 * true every n-th time.
 * <p/>
 * todo: backrefs in sets
 * <p/>
 * todo: better debugging of server name matcher
 * <p/>
 * todo: debugging tool especially googlebot client debugger, possibly googlebot tag
 * <p/>
 * todo: ability to set request parameters
 * <p/>
 * todo: ability to specify a $1 as $encode($1) (or something like that)
 * <p/>
 * todo: <to-servlet>struts</to-servlet>  will call context.getNamedDispatcher() to similar
 * <p/>
 * todo: grouping of rule for default settings
 * <p/>
 * todo: capture original (pre match) url into request attr so that people can use it
 * <p/>
 * <p/>
 * todo: wieselt to UrlRewrite is there a way to compare ALL parameters with a pattern? As far as i
 * understand the manual i allways have to provide a name when using
 * type="parameter" in a condition.
 * <p/>
 * todo: javaFreak 12/13/06
 * In Apache: RewriteEngine on
 * RewriteMap upper2lower int:tolower
 * RewriteRule ^/(.*)$ /${upper2lower:$1}
 * <p/>
 * todo: Rostislav Hristov  12/29/06
 * Is there an analogue to this mod_rewrite feature and can we expect it
 * in the upcoming versions?
 * RewriteCond      %{REQUEST_FILENAME}   !-f
 * RewriteCond      %{REQUEST_FILENAME}   !-d
 * <p/>
 * todo: debug screen, ie, this request matches the following rules
 * <p/>
 * todo: centralised browser detection example (set request attr?)
 *
 * @author Paul Tuckey
 * @version $Revision: 51 $ $Date: 2006-12-08 11:37:07 +1300 (Fri, 08 Dec 2006) $
 */
public class UrlRewriteFilter implements Filter {

    private static Log log = Log.getLog(UrlRewriteFilter.class);

    // next line is replaced by ant on compile
    public static final String VERSION = "3.1.0 build 6032";

    public static final String DEFAULT_WEB_CONF_PATH = "/WEB-INF/urlrewrite.xml";

    /**
     * The conf for this filter.
     */
    private UrlRewriter urlRewriter = null;

    /**
     * A user defined setting that can enable conf reloading.
     */
    private boolean confReloadCheckEnabled = false;

    /**
     * A user defined setting that says how often to check the conf has changed.
     */
    private int confReloadCheckInterval = 0;

    /**
     * The last time that the conf file was loaded.
     */
    private long confLastLoad = 0;
    private Conf confLastLoaded = null;
    private long confReloadLastCheck = 30;

    /**
     * path to conf file.
     */
    private String confPath;

    /**
     * Flag to make sure we don't bog the filter down during heavy load.
     */
    private boolean confReloadInProgress = false;

    private boolean statusEnabled = true;
    private String statusPath = "/rewrite-status";

    private ServerNameMatcher statusServerNameMatcher;
    private static final String DEFAULT_STATUS_ENABLED_ON_HOSTS = "localhost, local, 127.0.0.1";


    /**
     *
     */
    private ServletContext context = null;

    /**
     * Init is called automatically by the application server when it creates this filter.
     *
     * @param filterConfig The config of the filter
     */
    public void init(final FilterConfig filterConfig) throws ServletException {

        log.debug("filter init called");
        if (filterConfig == null) {
            log.error("unable to init filter as filter config is null");
            return;
        }

        log.debug("init: calling destroy just in case we are being re-inited uncleanly");
        destroyActual();

        context = filterConfig.getServletContext();
        if (context == null) {
            log.error("unable to init as servlet context is null");
            return;
        }

        // set the conf of the logger to make sure we get the messages in context log
        Log.setConfiguration(filterConfig);

        // get init paramerers from context web.xml file
        String confReloadCheckIntervalStr = filterConfig.getInitParameter("confReloadCheckInterval");
        String confPathStr = filterConfig.getInitParameter("confPath");
        String statusPathConf = filterConfig.getInitParameter("statusPath");
        String statusEnabledConf = filterConfig.getInitParameter("statusEnabled");
        String statusEnabledOnHosts = filterConfig.getInitParameter("statusEnabledOnHosts");

        // confReloadCheckInterval (default to null)
        if (!StringUtils.isBlank(confReloadCheckIntervalStr)) {
            // convert to millis
            confReloadCheckInterval = 1000 * NumberUtils.stringToInt(confReloadCheckIntervalStr);

            if (confReloadCheckInterval < 0) {
                confReloadCheckEnabled = false;
                log.info("conf reload check disabled");

            } else if (confReloadCheckInterval == 0) {
                confReloadCheckEnabled = true;
                log.info("conf reload check performed each request");

            } else {
                confReloadCheckEnabled = true;
                log.info("conf reload check set to " + confReloadCheckInterval / 1000 + "s");
            }

        } else {
            confReloadCheckEnabled = false;
        }

        if (!StringUtils.isBlank(confPathStr)) {
            confPath = StringUtils.trim(confPathStr);
        } else {
            confPath = DEFAULT_WEB_CONF_PATH;
        }
        log.debug("confPath set to " + confPath);

        // status enabled (default true)
        if (statusEnabledConf != null && !"".equals(statusEnabledConf)) {
            log.debug("statusEnabledConf set to " + statusEnabledConf);
            statusEnabled = "true".equals(statusEnabledConf.toLowerCase());
        }
        if (statusEnabled) {
            // status path (default /rewrite-status)
            if (statusPathConf != null && !"".equals(statusPathConf)) {
                statusPath = statusPathConf.trim();
                log.info("status display enabled, path set to " + statusPath);
            }
        } else {
            log.info("status display disabled");
        }

        if (StringUtils.isBlank(statusEnabledOnHosts)) {
            statusEnabledOnHosts = DEFAULT_STATUS_ENABLED_ON_HOSTS;
        } else {
            log.debug("statusEnabledOnHosts set to " + statusEnabledOnHosts);
        }
        statusServerNameMatcher = new ServerNameMatcher(statusEnabledOnHosts);

        loadUrlRewriter(filterConfig);
    }

    /**
     * Separate from init so that it can be overidden.
     */
    protected void loadUrlRewriter(FilterConfig filterConfig) throws ServletException {
        loadUrlRewriter();
    }


    private void loadUrlRewriter() {
        InputStream inputStream = context.getResourceAsStream(confPath);
        URL confUrl = null;
        try {
            confUrl = context.getResource(confPath);
        } catch (MalformedURLException e) {
            log.debug(e);
        }
        String confUrlStr = null;
        if (confUrl != null) {
            confUrlStr = confUrl.toString();
        }
        if (inputStream == null) {
            log.error("unable to find urlrewrite conf file at " + confPath);
            // set the writer back to null
            if (urlRewriter != null) {
                log.error("unloading existing conf");
                urlRewriter = null;
            }

        } else {
            Conf conf = new Conf(context, inputStream, confPath, confUrlStr);
            if (log.isDebugEnabled()) {
                if (conf.getRules() != null) {
                    log.debug("inited with " + conf.getRules().size() + " rules");
                }
                log.debug("conf is " + (conf.isOk() ? "ok" : "NOT ok"));
            }
            confLastLoaded = conf;
            if (conf.isOk()) {
                urlRewriter = new UrlRewriter(conf);
                log.info("loaded (conf ok)");

            } else {
                log.error("Conf failed to load");
                log.error("unloading existing conf");
                urlRewriter = null;
            }
        }
    }

    /**
     * Destroy is called by the application server when it unloads this filter.
     */
    public void destroy() {
        log.info("destroy called");
        destroyActual();
    }

    public void destroyActual() {
        destroyUrlRewriter();
        context = null;
        confLastLoad = 0;
        confPath = DEFAULT_WEB_CONF_PATH;
        confReloadCheckEnabled = false;
        confReloadCheckInterval = 0;
        confReloadInProgress = false;
    }

    protected void destroyUrlRewriter() {
        if (urlRewriter != null) {
            urlRewriter.destroy();
            urlRewriter = null;
        }
    }

    /**
     * The main method called for each request that this filter is mapped for.
     *
     * @param request  the request to filter
     * @param response the response to filter
     * @param chain    the chain for the filtering
     * @throws IOException
     * @throws ServletException
     */
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {

        UrlRewriter urlRewriter = getUrlRewriter(request, response, chain);

        final HttpServletRequest hsRequest = (HttpServletRequest) request;
        final HttpServletResponse hsResponse = (HttpServletResponse) response;
        UrlRewriteWrappedResponse urlRewriteWrappedResponse = new UrlRewriteWrappedResponse(hsResponse, hsRequest,
                urlRewriter);
        //UrlRewriteWrappedRequest urlRewriteWrappedRequest = new UrlRewriteWrappedRequest(hsRequest);

        // check for status request
        if (statusEnabled && statusServerNameMatcher.isMatch(request.getServerName())) {
            String uri = hsRequest.getRequestURI();
            if (log.isDebugEnabled()) {
                log.debug("checking for status path on " + uri);
            }
            String contextPath = hsRequest.getContextPath();
            if (uri != null && uri.startsWith(contextPath + statusPath)) {
                showStatus(hsRequest, urlRewriteWrappedResponse);
                return;
            }
        }

        boolean requestRewritten = false;
        if (urlRewriter != null) {

            // process the request
            requestRewritten = urlRewriter.processRequest(hsRequest, urlRewriteWrappedResponse, chain);

        } else {
            if (log.isDebugEnabled()) {
                log.debug("urlRewriter engine not loaded ignoring request (could be a conf file problem)");
            }
        }

        // if no rewrite has taken place continue as normal
        if (!requestRewritten) {
            chain.doFilter(hsRequest, urlRewriteWrappedResponse);
        }
    }


    /**
     * Called for every request.
     * <p/>
     * Split from doFilter so that it can be overriden.
     */
    protected UrlRewriter getUrlRewriter(ServletRequest request, ServletResponse response, FilterChain chain) {
        // check to see if the conf needs reloading
        if (isTimeToReloadConf()) {
            reloadConf();
        }
        return urlRewriter;
    }

    /**
     * Is it time to reload the configuration now.  Depends on is conf reloading is enabled.
     */
    public boolean isTimeToReloadConf() {
        long now = System.currentTimeMillis();
        return confReloadCheckEnabled && !confReloadInProgress && (now - confReloadCheckInterval) > confReloadLastCheck;
    }

    /**
     * Forcibly reload the configuration now.
     */
    public void reloadConf() {
        long now = System.currentTimeMillis();
        confReloadInProgress = true;
        confReloadLastCheck = now;

        log.debug("starting conf reload check");
        long confFileCurrentTime = getConfFileLastModified();
        if (confLastLoad < confFileCurrentTime) {
            // reload conf
            confLastLoad = System.currentTimeMillis();
            log.info("conf file modified since last load, reloading");
            loadUrlRewriter();
        } else {
            log.debug("conf is not modified");
        }
        confReloadInProgress = false;
    }

    /**
     * Gets the last modified date of the conf file.
     *
     * @return time as a long
     */
    private long getConfFileLastModified() {
        File confFile = new File(context.getRealPath(confPath));
        return confFile.lastModified();
    }


    /**
     * Show the status of the conf and the filter to the user.
     *
     * @param request  to get status info from
     * @param response response to show the status on.
     * @throws java.io.IOException if the output cannot be written
     */
    private void showStatus(final HttpServletRequest request, final ServletResponse response)
            throws IOException {

        log.debug("showing status");

        Status status = new Status(confLastLoaded, this);
        status.displayStatusInContainer(request);

        response.setContentLength(status.getBuffer().length());

        final PrintWriter out = response.getWriter();
        out.write(status.getBuffer().toString());
        out.close();

    }

    public boolean isConfReloadCheckEnabled() {
        return confReloadCheckEnabled;
    }

    /**
     * The amount of seconds between reload checks.
     *
     * @return int number of millis
     */
    public int getConfReloadCheckInterval() {
        return confReloadCheckInterval / 1000;
    }

    public Date getConfReloadLastCheck() {
        return new Date(confReloadLastCheck);
    }

    public boolean isStatusEnabled() {
        return statusEnabled;
    }

    public String getStatusPath() {
        return statusPath;
    }

    public boolean isLoaded() {
        return urlRewriter != null;
    }
}
