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
import org.tuckey.web.filters.urlrewrite.utils.StringUtils;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;


/**
 * A wrapper around NormalRule to handle some difference between the values passed from the xml vs. the modrewrite.
 */
public class ModRewriteRule extends NormalRule {

    private static Log log = Log.getLog(ModRewriteRule.class);


    /**
     * Constructor.
     */
    public ModRewriteRule() {
        // empty
    }
    
    /**
     * returns the URL in the format Apache ModRewrite is accustom... this means it should not be looking at the
     * parameters if they are passed...
     * @param url
     * @return
     */
    private String cleanUrl(final String url) {
    	if(url == null) {
    		return url;
    	}
    	int pos = url.indexOf("?");
    	if(pos > 0) {
    		return url.substring(0, pos);
    	}
    	return url;
    }

    /**
     * Will run the rule against the uri and perform action required will return false is not matched
     * otherwise true.
     *
     * @param url
     * @param hsRequest
     * @return String of the rewritten url or the same as the url passed in if no match was made
     */
    public RewrittenUrl matches(final String url, final HttpServletRequest hsRequest,
                                final HttpServletResponse hsResponse, RuleChain chain)
            throws IOException, ServletException, InvocationTargetException {
    	return super.matches(cleanUrl(url), hsRequest, hsResponse, chain);
    }

    public RewrittenUrl matches(final String url, final HttpServletRequest hsRequest,
                                final HttpServletResponse hsResponse)
            throws IOException, ServletException, InvocationTargetException {
        return matches(url, hsRequest, hsResponse, null);
    }
}
