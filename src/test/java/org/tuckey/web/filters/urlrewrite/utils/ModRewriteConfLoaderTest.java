package org.tuckey.web.filters.urlrewrite.utils;

import junit.framework.TestCase;

import org.tuckey.web.filters.urlrewrite.Condition;
import org.tuckey.web.filters.urlrewrite.ConditionMatch;
import org.tuckey.web.filters.urlrewrite.Conf;
import org.tuckey.web.filters.urlrewrite.NormalRule;
import org.tuckey.web.filters.urlrewrite.RewrittenUrl;
import org.tuckey.web.testhelper.MockRequest;
import org.tuckey.web.testhelper.MockResponse;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

import javax.servlet.ServletException;

public class ModRewriteConfLoaderTest extends TestCase {

    ModRewriteConfLoader loader = new ModRewriteConfLoader();
    Conf conf;

    public static final String BASE_PATH = "/org/tuckey/web/filters/urlrewrite/utils/";

    public void setUp() {
        Log.setLevel("DEBUG");
        conf = new Conf();
    }

    public void testEngine() {
        loader.process("RewriteEngine on", conf);
        assertTrue(conf.isEngineEnabled());
    }

    public void testEngine2() {
        loader.process("RewriteEngine off", conf);
        assertFalse(conf.isEngineEnabled());
    }

    public void testLoadFromFile() throws IOException {
        InputStream is = ModRewriteConfLoaderTest.class.getResourceAsStream(BASE_PATH + "htaccess-test1.txt");
        loader.process(is, conf);
        assertTrue(conf.isEngineEnabled());
        assertEquals(1, conf.getRules().size());
    }

    public void testLoadFromFile2() throws IOException {
        InputStream is = ModRewriteConfLoaderTest.class.getResourceAsStream(BASE_PATH + "htaccess-test1.txt");
        Conf conf = new Conf(null, is, "htaccess-test1.txt", null, true);
        assertTrue(conf.isEngineEnabled());
        assertTrue(conf.isOk());
        assertEquals(1, conf.getRules().size());
    }

    public void testSimple2() {
        loader.process("\n" +
                "    # redirect mozilla to another area                         \n" +
                "    RewriteCond  %{HTTP_USER_AGENT}  ^Mozilla.*                \n" +
                "    RewriteRule  ^/$                 /homepage.max.html  [L]   ", conf);
        assertEquals(1, conf.getRules().size());
        assertEquals(1, ((NormalRule) conf.getRules().get(0)).getConditions().size());
        assertEquals("header", ((Condition) ((NormalRule) conf.getRules().get(0)).getConditions().get(0)).getType());
        assertEquals("user-agent", ((Condition) ((NormalRule) conf.getRules().get(0)).getConditions().get(0)).getName());
        assertEquals("^Mozilla.*", ((Condition) ((NormalRule) conf.getRules().get(0)).getConditions().get(0)).getValue());
        assertEquals("redirect mozilla to another area", ((NormalRule) conf.getRules().get(0)).getNote());
        assertEquals("^/$", ((NormalRule) conf.getRules().get(0)).getFrom());
        assertEquals("/homepage.max.html", ((NormalRule) conf.getRules().get(0)).getTo());
        assertEquals(true, ((NormalRule) conf.getRules().get(0)).isLast());
    }

    public void testSimpleRedirect() {
        loader.process("\n" +
                "   RewriteRule ^/$     http://www.foo.com [R]              \n" +
                "", conf);

        assertNotNull(conf.getRules());
        assertEquals(1, conf.getRules().size());

        NormalRule rule = (NormalRule) conf.getRules().get(0);
        assertNotNull(rule);
        assertEquals("redirect", rule.getToType());
        assertEquals("^/$", rule.getFrom());
        assertEquals("http://www.foo.com", rule.getTo());
    }

    public void testPermanentRedirect() {
        loader.process("\n" +
                "   RewriteRule ^/$     http://www.foo.com [R=301]          \n" +
                "", conf);

        assertNotNull(conf.getRules());
        assertEquals(1, conf.getRules().size());

        NormalRule rule = (NormalRule) conf.getRules().get(0);
        assertNotNull(rule);
        assertEquals("permanent-redirect", rule.getToType());
        assertEquals("^/$", rule.getFrom());
        assertEquals("http://www.foo.com", rule.getTo());
    }
    
    public void testNoCaseConditionFlag() throws IOException, ServletException, InvocationTargetException {
    	loader.process("\n" +
                "RewriteCond %{HTTP_HOST} ^(.*)from\\.com$ [NC]\n" +
                "RewriteRule ^(.*)$ http://to.com [L,R=301,QSA]\n\n" +
                 "RewriteCond %{HTTP_HOST} ^(.*)from1\\.com$\n" +
                 "RewriteRule ^(.*)$ http://to1.com [L,R=301,QSA]\n" +
                "", conf);
    	 
    	assertNotNull(conf.getRules());
         assertEquals(2, conf.getRules().size());

         NormalRule rule = (NormalRule) conf.getRules().get(0);
         assertNotNull(rule);
         rule.initialise(null);
         
         assertEquals("permanent-redirect", rule.getToType());
         assertEquals("^(.*)$", rule.getFrom());
         assertTrue(rule.isLast());
         assertEquals("http://to.com", rule.getTo());
         
         
         MockRequest request = new MockRequest("/");
         request.setQueryString("testParam=false");
         request.setServerName("From.com"); // capitalized
         RewrittenUrl newUrl = rule.matches("/?testParam=false", request, new MockResponse());
         assertEquals("http://to.com?testParam=false", newUrl.getTarget());
         
     	 // now lets test that the rule without the [NC] flag works.
         rule = (NormalRule) conf.getRules().get(1);
         assertNotNull(rule);
         rule.initialise(null);
         
         assertEquals("permanent-redirect", rule.getToType());
         assertEquals("^(.*)$", rule.getFrom());
         assertTrue(rule.isLast());
         assertEquals("http://to1.com", rule.getTo());
         
         
         request = new MockRequest("/");
         request.setQueryString("testParam=false");
         request.setServerName("From1.com"); // capitalized, should not work...
         newUrl = rule.matches("/?testParam=false", request, new MockResponse());
         assertNull("The url should not have been rewritten, so this should be null!", newUrl);

         request = new MockRequest("/");
         request.setQueryString("testParam=false");
         request.setServerName("from1.com"); // lowercased, should work...
         newUrl = rule.matches("/?testParam=false", request, new MockResponse());
         assertEquals("http://to1.com?testParam=false", newUrl.getTarget());
    }

    
    public void testHttpHostCondition() throws IOException, ServletException, InvocationTargetException {
    	loader.process("\n" +
                "  # http host condition redirect\n" +
                "RewriteCond %{HTTP_HOST} ^(.*)from\\.com$ [NC]\n" +
                "RewriteRule ^(.*)$ http://to.com [L,R=301,QSA]" +
                "", conf);
    	 
    	assertNotNull(conf.getRules());
         assertEquals(1, conf.getRules().size());

         NormalRule rule = (NormalRule) conf.getRules().get(0);
         assertNotNull(rule);
         
         rule.initialise(null);
         
         assertEquals("permanent-redirect", rule.getToType());
         assertEquals("^(.*)$", rule.getFrom());
         assertTrue(rule.isLast());
         assertEquals("http://to.com", rule.getTo());
         
         
         MockRequest request = new MockRequest("/");
         request.setQueryString("testParam=false");
         request.setServerName("from.com");
         RewrittenUrl newUrl = rule.matches("/?testParam=false", request, new MockResponse());
         assertEquals("http://to.com?testParam=false", newUrl.getTarget());
        	
    }

    public void testMobile() {
        loader.process("\n" +
                "  # mobile redirect\n" +
                "RewriteCond %{HTTP_HOST} from.com\n" +
                "RewriteCond %{HTTP:accept} (hdml|wml|xhtml-mp|vnd\\.wap\\.) [OR]\n" +
                "RewriteCond %{HTTP:x-wap-profile} .+ [OR]\n" +
                "RewriteCond %{HTTP:user-agent} (Windows\\ CE) [OR]\n" +
                "RewriteCond %{HTTP:user-agent} (iPhone) [OR]\n" +
                "RewriteCond %{HTTP:user-agent} (iPod) [OR]\n" +
                "RewriteRule ^(.*)$ http://to.com [L,R]" +
                "", conf);

        assertNotNull(conf.getRules());
        assertEquals(1, conf.getRules().size());

        NormalRule rule = (NormalRule) conf.getRules().get(0);
        assertNotNull(rule);
        assertEquals("redirect", rule.getToType());
        assertEquals("^(.*)$", rule.getFrom());
        assertTrue(rule.isLast());
        assertEquals("http://to.com", rule.getTo());
    }

    public void testTemporaryRedirect() {
        loader.process("\n" +
                "   RewriteRule ^/$     http://www.foo.com [R=302]          \n" +
                "", conf);

        assertNotNull(conf.getRules());
        assertEquals(1, conf.getRules().size());

        NormalRule rule = (NormalRule) conf.getRules().get(0);
        assertNotNull(rule);
        assertEquals("temporary-redirect", rule.getToType());
        assertEquals("^/$", rule.getFrom());
        assertEquals("http://www.foo.com", rule.getTo());
    }

    public void testReqUri() {
        loader.process("\n" +
                "    RewriteCond  %{REQUEST_URI}  ^somepage.*                   \n" +
                "    RewriteRule  ^/$                 /homepage.max.html  [L]   ", conf);
        assertEquals(1, conf.getRules().size());
        assertEquals(1, ((NormalRule) conf.getRules().get(0)).getConditions().size());
        assertEquals("request-uri", ((Condition) ((NormalRule) conf.getRules().get(0)).getConditions().get(0)).getType());
        assertEquals("^somepage.*", ((Condition) ((NormalRule) conf.getRules().get(0)).getConditions().get(0)).getValue());
        assertEquals("^/$", ((NormalRule) conf.getRules().get(0)).getFrom());
        assertEquals("/homepage.max.html", ((NormalRule) conf.getRules().get(0)).getTo());
        assertEquals(true, ((NormalRule) conf.getRules().get(0)).isLast());
    }

    public void testReqFilename() {
        loader.process("     RewriteCond  %{REQUEST_FILENAME}  -f                        \n" +
                "            RewriteRule  ^/conf-test1.xml$    /homepage.max.html  [L]   ", conf);
        assertEquals("request-filename", ((Condition) ((NormalRule) conf.getRules().get(0)).getConditions().get(0)).getType());
        assertEquals("isfile", ((Condition) ((NormalRule) conf.getRules().get(0)).getConditions().get(0)).getOperator());
    }

    public void testReqFilename2() {
        loader.process("     RewriteCond  %{REQUEST_FILENAME}  -F                        \n" +
                "            RewriteRule  ^/conf-test1.xml$    /homepage.max.html  [L]   ", conf);
        assertEquals("request-filename", ((Condition) ((NormalRule) conf.getRules().get(0)).getConditions().get(0)).getType());
        assertEquals("isfile", ((Condition) ((NormalRule) conf.getRules().get(0)).getConditions().get(0)).getOperator());
    }

    public void testReqIsDir() {
        loader.process("     RewriteCond  %{REQUEST_FILENAME}  -d                        \n" +
                "            RewriteRule  ^/conf-test1.xml$    /homepage.max.html  [L]   ", conf);
        assertEquals("request-filename", ((Condition) ((NormalRule) conf.getRules().get(0)).getConditions().get(0)).getType());
        assertEquals("isdir", ((Condition) ((NormalRule) conf.getRules().get(0)).getConditions().get(0)).getOperator());
    }

    public void testReqNotDir() {
        loader.process("     RewriteCond  %{REQUEST_FILENAME}  !-d                        \n" +
                "            RewriteRule  ^/conf-test1.xml$    /homepage.max.html  [L]   ", conf);
        assertEquals("request-filename", ((Condition) ((NormalRule) conf.getRules().get(0)).getConditions().get(0)).getType());
        assertEquals("notdir", ((Condition) ((NormalRule) conf.getRules().get(0)).getConditions().get(0)).getOperator());
    }


    public void testQSAppendRedirect() throws IOException, ServletException, InvocationTargetException {
        loader.process("\n" +
                "   RewriteRule ^/$     http://www.foo.com [QSA,R]              \n" +
                "", conf);

        assertNotNull(conf.getRules());
        assertEquals(1, conf.getRules().size());

        NormalRule rule = (NormalRule) conf.getRules().get(0);
        assertNotNull(rule);
        assertEquals("redirect", rule.getToType());
        assertEquals("^/$", rule.getFrom());
        assertEquals("http://www.foo.com", rule.getTo());
        assertTrue(rule.getQueryStringAppend());
        rule.initialise(null);
        

        MockRequest request = new MockRequest("/");
        request.setServerName("to.com");
        request.setQueryString("testParam=false");
        RewrittenUrl newUrl = rule.matches("/?testParam=false", request, new MockResponse());
        assertEquals("http://www.foo.com?testParam=false", newUrl.getTarget()); // this will make sure we don't append the query string twice
    }


    public void testNegatedConditionRegex() {
        loader.process("\n" +
                "   RewriteRule ^/$     http://www.foo.com [QSA,R]              \n" +
                "", conf);

        assertNotNull(conf.getRules());
        assertEquals(1, conf.getRules().size());

        NormalRule rule = (NormalRule) conf.getRules().get(0);
        assertNotNull(rule);
        assertEquals("redirect", rule.getToType());
        assertEquals("^/$", rule.getFrom());
        assertEquals("http://www.foo.com", rule.getTo());
        assertTrue(rule.getQueryStringAppend());
    }

    public void testNotCondition() throws IOException, ServletException, InvocationTargetException {
    	loader.process("\n" +
	    	"RewriteCond %{REQUEST_URI} !^/probe$ [NC]\n" +
			"RewriteCond %{HTTP_HOST} !^www\\.from\\.com$ [NC]\n" +
			"RewriteCond %{HTTP_HOST} !^www\\.from-too\\.com$ [NC]\n" +
			"RewriteRule ^(.*)$ http://www.to.com$1 [L,R=301]\n", conf);
    	NormalRule rule = (NormalRule) conf.getRules().get(0);
        assertNotNull(rule);
        rule.initialise(null);
        
        assertEquals("permanent-redirect", rule.getToType());
        assertEquals("^(.*)$", rule.getFrom());
        assertTrue(rule.isLast());
        assertEquals("http://www.to.com$1", rule.getTo());
        

        MockRequest request = new MockRequest("/");
        request.setServerName("from.com");
        request.setQueryString("testParam=false");
        RewrittenUrl newUrl = rule.matches("/?testParam=false", request, new MockResponse());
        assertEquals("http://www.to.com/?testParam=false", newUrl.getTarget());
        

        // www.from.com should not pass the conditions, as it says !www.from.com
        request.setServerName("www.from.com");
        newUrl = rule.matches("/?testParam=false", request, new MockResponse());
        assertNull(newUrl);
    }

    public void testHttpsNotOn() throws IOException, ServletException, InvocationTargetException {
    	loader.process("\n" +
	    	"RewriteCond %{HTTPS} !=on\n" +
	    	"RewriteRule ^/oa/(.*) https://www.to.com/oa/$1 [R=301,L,QSA]", conf);
    	NormalRule rule = (NormalRule) conf.getRules().get(0);
        assertNotNull(rule);
        rule.initialise(null);
        
        assertEquals("permanent-redirect", rule.getToType());
        assertEquals("^/oa/(.*)", rule.getFrom());
        assertTrue(rule.isLast());
        assertEquals("https://www.to.com/oa/$1", rule.getTo());
        
        Condition cond = (Condition) rule.getConditions().get(0);
        assertEquals(cond.getValue(), "^http$");
        assertEquals(cond.getType(), "scheme");
        

        MockRequest request = new MockRequest("/");
        request.setServerName("to.com");
        request.setScheme("http");
        request.setPathInfo("/oa/bob");
        request.setQueryString("testParam=false");
        RewrittenUrl newUrl = rule.matches("/oa/bob?testParam=false", request, new MockResponse());
        assertEquals("https://www.to.com/oa/bob?testParam=false", newUrl.getTarget());
        

        // if the request is already https, do nothing
        request.setScheme("https");
        newUrl = rule.matches("/oa/bob?testParam=false", request, new MockResponse());
        assertNull("Expected null, but was " + (newUrl == null ? "null" : newUrl.getTarget()), newUrl);
    }
}
