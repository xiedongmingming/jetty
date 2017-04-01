package org.eclipse.jetty.security;

import java.io.IOException;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.security.authentication.DeferredAuthentication;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandler.Context;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public abstract class SecurityHandler extends HandlerWrapper implements Authenticator.AuthConfiguration {
    private static final Logger LOG = Log.getLogger(SecurityHandler.class);

    private boolean _checkWelcomeFiles = false;
    private Authenticator _authenticator;
    private Authenticator.Factory _authenticatorFactory=new DefaultAuthenticatorFactory();
    private String _realmName;
    private String _authMethod;
    private final Map<String,String> _initParameters=new HashMap<String,String>();
    private LoginService _loginService;
    private IdentityService _identityService;
    private boolean _renewSession=true;

	protected SecurityHandler() {
        addBean(_authenticatorFactory);
    }

    @Override
	public IdentityService getIdentityService() {
        return _identityService;
    }

	public void setIdentityService(IdentityService identityService) {
		if (isStarted()) {
            throw new IllegalStateException("Started");
		}
        updateBean(_identityService,identityService);
        _identityService = identityService;
    }
    @Override
	public LoginService getLoginService() {
        return _loginService;
    }

	public void setLoginService(LoginService loginService) {
		if (isStarted()) {
            throw new IllegalStateException("Started");
		}
        updateBean(_loginService,loginService);
        _loginService = loginService;
    }

	public Authenticator getAuthenticator() {
        return _authenticator;
    }

	public void setAuthenticator(Authenticator authenticator) {
		if (isStarted()) {
            throw new IllegalStateException("Started");
		}
        updateBean(_authenticator,authenticator);
        _authenticator = authenticator;
		if (_authenticator != null) {
            _authMethod=_authenticator.getAuthMethod();
		}
    }

	public Authenticator.Factory getAuthenticatorFactory() {
        return _authenticatorFactory;
    }

	public void setAuthenticatorFactory(Authenticator.Factory authenticatorFactory) {
		if (isRunning()) {
            throw new IllegalStateException("running");
		}
        updateBean(_authenticatorFactory,authenticatorFactory);
        _authenticatorFactory = authenticatorFactory;
    }
    @Override
	public String getRealmName() {
        return _realmName;
    }

	public void setRealmName(String realmName) {
		if (isRunning()) {
            throw new IllegalStateException("running");
		}
        _realmName = realmName;
    }
    @Override
	public String getAuthMethod() {
        return _authMethod;
    }

	public void setAuthMethod(String authMethod) {
		if (isRunning()) {
            throw new IllegalStateException("running");
		}
        _authMethod = authMethod;
    }

	public boolean isCheckWelcomeFiles() {
        return _checkWelcomeFiles;
    }

	public void setCheckWelcomeFiles(boolean authenticateWelcomeFiles) {
		if (isRunning()) {
            throw new IllegalStateException("running");
		}
        _checkWelcomeFiles = authenticateWelcomeFiles;
    }
    @Override
	public String getInitParameter(String key) {
        return _initParameters.get(key);
    }
    @Override
	public Set<String> getInitParameterNames() {
        return _initParameters.keySet();
    }

	public String setInitParameter(String key, String value) {
		if (isRunning()) {
            throw new IllegalStateException("running");
		}
        return _initParameters.put(key,value);
    }

	protected LoginService findLoginService() throws Exception {
        Collection<LoginService> list = getServer().getBeans(LoginService.class);
        LoginService service = null;
        String realm=getRealmName();
		if (realm != null) {
            for (LoginService s : list)
				if (s.getName() != null && s.getName().equals(realm)) {
                    service=s;
                    break;
                }
		} else if (list.size() == 1) {
			service = list.iterator().next();
        }
        return service;
    }

	protected IdentityService findIdentityService() {
        return getServer().getBean(IdentityService.class);
    }
    @Override
	protected void doStart() throws Exception {
        // copy security init parameters
        ContextHandler.Context context =ContextHandler.getCurrentContext();
		if (context != null) {
            Enumeration<String> names=context.getInitParameterNames();
			while (names != null && names.hasMoreElements()) {
                String name =names.nextElement();
				if (name.startsWith("org.eclipse.jetty.security.") && getInitParameter(name) == null) {
					setInitParameter(name, context.getInitParameter(name));
				}
            }
        }

        // complicated resolution of login and identity service to handle
        // many different ways these can be constructed and injected.

        if (_loginService==null)
        {
            setLoginService(findLoginService());
            if (_loginService!=null)
                unmanage(_loginService);
        }
        
        if (_identityService==null)
        {
            if (_loginService!=null)
                setIdentityService(_loginService.getIdentityService());

            if (_identityService==null)
                setIdentityService(findIdentityService());

            if (_identityService==null)
            {
                if (_realmName!=null)
                { 
                    setIdentityService(new DefaultIdentityService());
                    manage(_identityService);
                }
            }
            else
                unmanage(_identityService);
        }

        if (_loginService!=null)
        {
            if (_loginService.getIdentityService()==null)
                _loginService.setIdentityService(_identityService);
            else if (_loginService.getIdentityService()!=_identityService)
                throw new IllegalStateException("LoginService has different IdentityService to "+this);
        }

        Authenticator.Factory authenticatorFactory = getAuthenticatorFactory();
        if (_authenticator==null && authenticatorFactory!=null && _identityService!=null)
            setAuthenticator(authenticatorFactory.getAuthenticator(getServer(),ContextHandler.getCurrentContext(),this, _identityService, _loginService));

        if (_authenticator!=null)
            _authenticator.setConfiguration(this);
        else if (_realmName!=null)
        {
            LOG.warn("No Authenticator for "+this);
            throw new IllegalStateException("No Authenticator");
        }

        super.doStart();
    }

    @Override
    /* ------------------------------------------------------------ */
    protected void doStop() throws Exception
    {
        //if we discovered the services (rather than had them explicitly configured), remove them.
        if (!isManaged(_identityService))
        {
            removeBean(_identityService);
            _identityService = null;   
        }
        
        if (!isManaged(_loginService))
        {
            removeBean(_loginService);
            _loginService=null;
        }
        
        super.doStop();
    }

    /* ------------------------------------------------------------ */
    protected boolean checkSecurity(Request request)
    {
        switch(request.getDispatcherType())
        {
            case REQUEST:
            case ASYNC:
                return true;
            case FORWARD:
                if (isCheckWelcomeFiles() && request.getAttribute("org.eclipse.jetty.server.welcome") != null)
                {
                    request.removeAttribute("org.eclipse.jetty.server.welcome");
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.security.Authenticator.AuthConfiguration#isSessionRenewedOnAuthentication()
     */
    @Override
    public boolean isSessionRenewedOnAuthentication()
    {
        return _renewSession;
    }

    /* ------------------------------------------------------------ */
    /** Set renew the session on Authentication.
     * <p>
     * If set to true, then on authentication, the session associated with a reqeuest is invalidated and replaced with a new session.
     * @param renew true to renew the authentication on session
     * @see org.eclipse.jetty.security.Authenticator.AuthConfiguration#isSessionRenewedOnAuthentication()
     */
    public void setSessionRenewedOnAuthentication(boolean renew)
    {
        _renewSession=renew;
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.jetty.server.Handler#handle(java.lang.String,
     *      javax.servlet.http.HttpServletRequest,
     *      javax.servlet.http.HttpServletResponse, int)
     */
    @Override
    public void handle(String pathInContext, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        final Response base_response = baseRequest.getResponse();
        final Handler handler=getHandler();

        if (handler==null)
            return;

        final Authenticator authenticator = _authenticator;

        if (checkSecurity(baseRequest))
        {
            //See Servlet Spec 3.1 sec 13.6.3
            if (authenticator != null)
                authenticator.prepareRequest(baseRequest);
            
            RoleInfo roleInfo = prepareConstraintInfo(pathInContext, baseRequest);

            // Check data constraints
            if (!checkUserDataPermissions(pathInContext, baseRequest, base_response, roleInfo))
            {
                if (!baseRequest.isHandled())
                {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    baseRequest.setHandled(true);
                }
                return;
            }

            // is Auth mandatory?
            boolean isAuthMandatory =
                isAuthMandatory(baseRequest, base_response, roleInfo);

            if (isAuthMandatory && authenticator==null)
            {
                LOG.warn("No authenticator for: "+roleInfo);
                if (!baseRequest.isHandled())
                {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    baseRequest.setHandled(true);
                }
                return;
            }

            // check authentication
            Object previousIdentity = null;
            try
            {
                Authentication authentication = baseRequest.getAuthentication();
                if (authentication==null || authentication==Authentication.NOT_CHECKED)
                    authentication=authenticator==null?Authentication.UNAUTHENTICATED:authenticator.validateRequest(request, response, isAuthMandatory);

                if (authentication instanceof Authentication.Wrapped)
                {
                    request=((Authentication.Wrapped)authentication).getHttpServletRequest();
                    response=((Authentication.Wrapped)authentication).getHttpServletResponse();
                }

                if (authentication instanceof Authentication.ResponseSent)
                {
                    baseRequest.setHandled(true);
                }
                else if (authentication instanceof Authentication.User)
                {
                    Authentication.User userAuth = (Authentication.User)authentication;
                    baseRequest.setAuthentication(authentication);
                    if (_identityService!=null)
                        previousIdentity = _identityService.associate(userAuth.getUserIdentity());

                    if (isAuthMandatory)
                    {
                        boolean authorized=checkWebResourcePermissions(pathInContext, baseRequest, base_response, roleInfo, userAuth.getUserIdentity());
                        if (!authorized)
                        {
                            response.sendError(HttpServletResponse.SC_FORBIDDEN, "!role");
                            baseRequest.setHandled(true);
                            return;
                        }
                    }

                    handler.handle(pathInContext, baseRequest, request, response);
                    if (authenticator!=null)
                        authenticator.secureResponse(request, response, isAuthMandatory, userAuth);
                }
                else if (authentication instanceof Authentication.Deferred)
                {
                    DeferredAuthentication deferred= (DeferredAuthentication)authentication;
                    baseRequest.setAuthentication(authentication);

                    try
                    {
                        handler.handle(pathInContext, baseRequest, request, response);
                    }
                    finally
                    {
                        previousIdentity = deferred.getPreviousAssociation();
                    }

                    if (authenticator!=null)
                    {
                        Authentication auth=baseRequest.getAuthentication();
                        if (auth instanceof Authentication.User)
                        {
                            Authentication.User userAuth = (Authentication.User)auth;
                            authenticator.secureResponse(request, response, isAuthMandatory, userAuth);
                        }
                        else
                            authenticator.secureResponse(request, response, isAuthMandatory, null);
                    }
                }
                else
                {
                    baseRequest.setAuthentication(authentication);
                    if (_identityService!=null)
                        previousIdentity = _identityService.associate(null);
                    handler.handle(pathInContext, baseRequest, request, response);
                    if (authenticator!=null)
                        authenticator.secureResponse(request, response, isAuthMandatory, null);
                }
            }
            catch (ServerAuthException e)
            {
                // jaspi 3.8.3 send HTTP 500 internal server error, with message
                // from AuthException
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            }
            finally
            {
                if (_identityService!=null)
                    _identityService.disassociate(previousIdentity);
            }
        }
        else
            handler.handle(pathInContext, baseRequest, request, response);
    }


    /* ------------------------------------------------------------ */
    public static SecurityHandler getCurrentSecurityHandler()
    {
        Context context = ContextHandler.getCurrentContext();
        if (context==null)
            return null;

        return context.getContextHandler().getChildHandlerByClass(SecurityHandler.class);
    }

    /* ------------------------------------------------------------ */
    public void logout(Authentication.User user)
    {
        LOG.debug("logout {}",user);
        LoginService login_service=getLoginService();
        if (login_service!=null)
        {
            login_service.logout(user.getUserIdentity());
        }

        IdentityService identity_service=getIdentityService();
        if (identity_service!=null)
        {
            // TODO recover previous from threadlocal (or similar)
            Object previous=null;
            identity_service.disassociate(previous);
        }
    }

    /* ------------------------------------------------------------ */
    protected abstract RoleInfo prepareConstraintInfo(String pathInContext, Request request);

    /* ------------------------------------------------------------ */
    protected abstract boolean checkUserDataPermissions(String pathInContext, Request request, Response response, RoleInfo constraintInfo) throws IOException;

    /* ------------------------------------------------------------ */
    protected abstract boolean isAuthMandatory(Request baseRequest, Response base_response, Object constraintInfo);

    /* ------------------------------------------------------------ */
    protected abstract boolean checkWebResourcePermissions(String pathInContext, Request request, Response response, Object constraintInfo,
                                                           UserIdentity userIdentity) throws IOException;


    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    public class NotChecked implements Principal
    {
        @Override
        public String getName()
        {
            return null;
        }

        @Override
        public String toString()
        {
            return "NOT CHECKED";
        }

        public SecurityHandler getSecurityHandler()
        {
            return SecurityHandler.this;
        }
    }


    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    public static final Principal __NO_USER = new Principal()
    {
        @Override
        public String getName()
        {
            return null;
        }

        @Override
        public String toString()
        {
            return "No User";
        }
    };

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /**
     * Nobody user. The Nobody UserPrincipal is used to indicate a partial state
     * of authentication. A request with a Nobody UserPrincipal will be allowed
     * past all authentication constraints - but will not be considered an
     * authenticated request. It can be used by Authenticators such as
     * FormAuthenticator to allow access to logon and error pages within an
     * authenticated URI tree.
     */
    public static final Principal __NOBODY = new Principal()
    {
        @Override
        public String getName()
        {
            return "Nobody";
        }

        @Override
        public String toString()
        {
            return getName();
        }
    };

}
