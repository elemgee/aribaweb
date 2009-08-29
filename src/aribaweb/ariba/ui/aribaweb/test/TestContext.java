/*
    Copyright 1996-2009 Ariba, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    $Id: //ariba/platform/ui/aribaweb/ariba/ui/aribaweb/test/TestContext.java#16 $
*/

package ariba.ui.aribaweb.test;

import ariba.ui.aribaweb.core.AWConcreteServerApplication;
import ariba.ui.aribaweb.core.AWRequestContext;
import ariba.ui.aribaweb.core.AWSession;
import ariba.util.core.Assert;
import ariba.util.core.Base64;
import ariba.util.core.ListUtil;
import ariba.util.core.MapUtil;
import ariba.util.core.StringUtil;
import ariba.util.i18n.I18NUtil;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpSession;


public class TestContext
{
    public static final String Name = "uiTestContext";
    public static final String ID = "testContextId";
    public static final String TestAutomationMode = "taMode";
    public static final String SuiteDataParam = "suiteData";
    public static final String ReturnUrlParam = "returnUrl";

    private Map<String, Object> _internalContext = MapUtil.map();
    
    private Map<Object, Object> _context = MapUtil.map();
    private List<String> _unhandledObjectList = ListUtil.list();
    private String _username;
    private TestContextDataProvider _dataProvider;
    private String _id;
    private String[] _returnUrl;
    
    private static Map<String, TestContext> _savedTestContext = MapUtil.map();
    private static Map<String, TestContextObjectFactory> _factoriesById = MapUtil.map();
    private static Map<Class, String> _factoryIdsByClass = MapUtil.map();

    public TestContext ()
    {
        _id = String.valueOf(System.currentTimeMillis());
    }

    public String getId ()
    {
        return _id;
    }
    
    public static TestContext getSavedTestContext (AWRequestContext requestContext)
    {
        TestContext tc = null;
        String tcId = requestContext.formValueForKey(ID);
        if (tcId != null) {
            tc = _savedTestContext.get(tcId);
        }
        return tc;
    }

    public static void removeSavedTestContext (AWRequestContext requestContext)
    {
        String tcId = requestContext.formValueForKey(ID);
        _savedTestContext.remove(tcId);
    }

    public void saveTestContext ()
    {
        _savedTestContext.put(_id, this);
    }

    public void addInternalParam (String key, Object value)
    {
        if (value != null) {
            _internalContext.put(key, value);
        }
    }

    public Object getInternalParam (String key)
    {
        return _internalContext.get(key);
    }

    public void setDataProvider (TestContextDataProvider dataProvider)
    {
        _dataProvider = dataProvider;
    }

    static public boolean isTestAutomationMode (AWRequestContext requestContext)
    {
        return AWConcreteServerApplication.IsDebuggingEnabled &&
               (getTestContext(requestContext) != null ||
                getSavedTestContext(requestContext) != null ||
                !StringUtil.nullOrEmptyOrBlankString(requestContext.formValueForKey(TestAutomationMode)) ||
                requestContext._debugIsInRecordingMode() || requestContext._debugIsInPlaybackMode());
    }
    
    static public TestContext getTestContext (AWRequestContext requestContext)
    {
        // Don't call requestContext.session(false) here; that will attempt to checkout the session
        // which could block if the session is in use by another thread, e.g., if this is a progressCheck request.
        // ProgressCheck requests aren't "supposed" to be assoicated with the session (they take the session ID as
        // query parameter awpcid instead) but in the case of cookie session tracking they have the session
        // cookie anyway since it seems impossible to suppress that...
        HttpSession httpSession = requestContext.existingHttpSession();
        if (httpSession != null) {
            AWSession session = AWSession.session(httpSession);
            if (session != null) {
                return getTestContext(session);
            }
        }
        return null;
    }

    static public TestContext getTestContext (AWSession session)
    {
        return session != null ? (TestContext)session.dict().get(TestContext.Name) : null;
    }

    public Set keys ()
    {
        return _context.keySet();
    }
    
    public Object get (Class type)
    {
        Object obj = _context.get(type);
        if (_dataProvider != null) {
            obj = _dataProvider.resolveForGet(this, obj);
        }
        return obj;
    }

    public void put (Object object)
    {
        put(object.getClass(), object); 
    }
    
    public void put (Object key, Object value)
    {
        Object obj = value;
        if (_dataProvider != null) {
            obj = _dataProvider.resolveForPut(this, obj);
        }
        _context.put(key, obj); 
    }

    /**
        If there is a TestContext associated with the AWRequestContext's AWSession, and
        value is non-null, put the given value into it, keyed by its Class instance.
    */
    public static void put (AWRequestContext rc, Object value)
    {
        TestContext tc = getTestContext(rc);
        if (tc != null && value != null) {
            tc.put(value);
        }
    }

    /**
        If there is a TestContext associated with the AWRequestContext's AWSession, and
        key and value are non-null, put the given key-value pair into it.
    */
    public static void put (AWRequestContext rc, String key, Object value)
    {
        TestContext tc = getTestContext(rc);
        if (tc != null && key != null && value != null) {
            tc.put(key, value);
        }
    }

    /**
        If there is a TestContext associated with the AWSession, and
        value is non-null, put the given value into it, keyed by its Class instance.
    */
    public static void put (AWSession session, Object value)
    {
        TestContext tc = getTestContext(session);
        if (tc != null && value != null) {
            tc.put(value);
        }
    }

    /**
        If there is a TestContext associated with the AWSession, and
        key and value are non-null, put the given key-value pair into it.
    */
    public static void put (AWSession session, String key, Object value)
    {
        TestContext tc = getTestContext(session);
        if (tc != null && key != null && value != null) {
            tc.put(key, value);
        }
    }

    public void setUsername (String username)
    {
        _username = username;
    }

    public String getUsername ()
    {
        return _username;
    }

    public void clear ()
    {
        _context = MapUtil.map();
        _unhandledObjectList = ListUtil.list();
    }

    public static void registerTestContextObjectFactory (
        TestContextObjectFactory factory,
        Class c,
        String factoryId
        )
    {
        Assert.that(factory != null, "factory cannot be null");
        Assert.that(c != null, "class cannot be null");
        Assert.that(!StringUtil.nullOrEmptyOrBlankString(factoryId),
                    "factoryId cannot be null or blank or empty");

        Assert.that(_factoriesById.get(factoryId) == null, 
                    "Cannot reregister factory ID %s", factoryId);
        _factoriesById.put(factoryId, factory);

        Assert.that(_factoryIdsByClass.get(c) == null,
                    "Cannot reregister class %s", c.getName());
        _factoryIdsByClass.put(c, factoryId);
    }

    TestContextObjectFactory factoryForId (String id)
    {
        return _factoriesById.get(id);
    }

    String factoryIdForClass (Class clazz)
    {
        for (Class c = clazz; c != null; c = c.getSuperclass()) {
            String id = _factoryIdsByClass.get(c);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private static final String Pipe = "|";
    public static final String SemiColon = ";";

    public String getSuiteData ()
    {
        StringBuffer buf = new StringBuffer();

        // include factoryId and objId pair from _unhandledObjectList first to
        // ensure the correct order when we reconstitute the objects
        for (String encodedPairString : _unhandledObjectList) {
            if (buf.length() > 0) {
                buf.append(SemiColon);
            }
            buf.append(encodedPairString);
        }

        for (Object key : keys()) {
            Class c = (Class)key;
            String factoryId = factoryIdForClass(c);
            if (factoryId != null) {
                TestContextObjectFactory f = factoryForId(factoryId);
                Object obj = get(c);
                String objId = f.getSharedID(obj);
                if (objId != null) {
                    if (buf.length() > 0) {
                        buf.append(SemiColon);
                    }
                    try {
                        buf.append(URLEncoder.encode(factoryId, I18NUtil.EncodingUTF_8));
                        buf.append(Pipe);
                        buf.append(URLEncoder.encode(objId, I18NUtil.EncodingUTF_8));
                    }
                    catch (UnsupportedEncodingException e) {
                        Assert.that(false, "UTF-8 must be supported");
                    }
                }
            }
        }
        return Base64.encode(buf.toString());
    }

    public void initializeSuiteData (AWRequestContext requestContext)
    {
        String returnUrl = requestContext.formValueForKey(ReturnUrlParam);
        if (!StringUtil.nullOrEmptyOrBlankString(returnUrl)) {
            returnUrl = Base64.decode(returnUrl);
            _returnUrl =  returnUrl.split(SemiColon);

        }
        String suiteData = requestContext.formValueForKey(SuiteDataParam);
        if (!StringUtil.nullOrEmptyOrBlankString(suiteData)) {
            suiteData = Base64.decode(suiteData);
            String[] pairs = suiteData.split(SemiColon);
            for (String pair : pairs) {
                String[] kv = pair.split("\\|");
                Assert.that(kv.length == 2,
                            "Suite data key-value pair had %s " +
                            "members instead of 2", kv.length);
                try {
                    String factoryId = URLDecoder.decode(
                        kv[0],
                        I18NUtil.EncodingUTF_8
                        );
                    String objId = URLDecoder.decode(
                        kv[1],
                        I18NUtil.EncodingUTF_8
                        );
                    TestContextObjectFactory f = factoryForId(factoryId);
                    Assert.assertNonFatal(f != null,
                                          "No factory found for ID '%s', object '%s'", factoryId, objId);
                    boolean handled = false;
                    if (f != null) {
                        Object obj = f.reconstituteObject(requestContext, objId);
                        Assert.assertNonFatal(obj != null,
                                              "Failed to reconstitute object with factory '%s', object '%s'", factoryId, objId);
                        if (obj != null) {
                            put(obj);
                            handled = true;
                        }
                    }

                    if (!handled) {
                        // if we're not able to reconstitute the oject, put it
                        // into _unhandledObjectList. It will be included
                        // in the query string and passed along when the API
                        // getSuiteData() is called. Note that we need to use a
                        // list here since two classes can refer to the same factory.
                        _unhandledObjectList.add(pair);
                    }
                }
                catch (UnsupportedEncodingException e) {
                    Assert.that(false, "UTF-8 must be supported");
                }
            }
        }
    }

    public static String getEncodedReturnUrl (String displayName, String url)
    {
        StringBuilder buf = new StringBuilder();
        buf.append(displayName);
        buf.append(SemiColon);
        buf.append(url);
        return Base64.encode(buf.toString());

    }

    public String getEncodedReturnUrl ()
    {
        return getEncodedReturnUrl(
                getReturnUrlName(),
                getReturnUrl()
                );
    }

    public String getReturnUrlName ()
    {
        return _returnUrl != null ? _returnUrl[0] : null;
    }

    public String getReturnUrl ()
    {
        return _returnUrl != null ? _returnUrl[1] : null;
    }
}
