package tyk.example;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

import org.lognet.springboot.grpc.GRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import coprocess.CoprocessMiniRequestObject.MiniRequestObject;
import coprocess.CoprocessObject;
import coprocess.CoprocessReturnOverrides.ReturnOverrides;
import coprocess.CoprocessSessionState.SessionState;
import coprocess.DispatcherGrpc;
import io.grpc.BindableService;

@Configuration
@EnableWebMvc
@GRpcService
public class TykPluginDispatcher extends DispatcherGrpc.DispatcherImplBase
		implements BindableService, WebMvcConfigurer {

	private static final String TYK_AUTHCHECK_HOOK = "MyAuthCheck";
	private static final String TYK_POST_HOOK = "MyPostMiddleware";
	private static final String TYK_PRE_HOOK = "MyPreMiddleware";
	
	private static final String HTTP_HEADER_COOKIE = "Cookie";
	private static final String HTTP_HEADER_LOCATION = "Location";
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	public static ConcurrentHashMap<String, HashMap<String, String>> tempSessionStorage = null;
	
	public static final String CUSTOM_HEADER_VERSION = "x-custom-version";
	public static final String CUSTOM_HEADER_TENANT_ID = "x-custom-tenant-id";
	
	@Value("${login.url}")
	private String loginUrl;

	@PostConstruct
	public void createDefaultEntity() {
		tempSessionStorage = new ConcurrentHashMap<String, HashMap<String, String>>();
		{
			// ff5c2e57-b13f-43d9-b72b-247a544a904a
			String dummySessionId = "ff5c2e57-b13f-43d9-b72b-247a544a904a";
			HashMap<String, String> sessionMap = new HashMap<String, String>();
			sessionMap.put(CUSTOM_HEADER_VERSION, "1.0.0");
			sessionMap.put(CUSTOM_HEADER_TENANT_ID, dummySessionId);
			

			tempSessionStorage.put(dummySessionId, sessionMap);
		}
	}
	
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/bundles/**").addResourceLocations("classpath:/bundles/");
	}

	@Override
	public void dispatch(CoprocessObject.Object request,
			io.grpc.stub.StreamObserver<CoprocessObject.Object> responseObserver) {
		logger.trace("Entering dispatch method. request: {}", request);

		CoprocessObject.Object modifiedRequest = null;

		switch (request.getHookName()) {
		case TYK_PRE_HOOK:
			logger.trace("entering the hook: {}", TYK_PRE_HOOK);
			break;
		case TYK_POST_HOOK:
			logger.trace("entering the hook: {}", TYK_POST_HOOK);
			break;
		case TYK_AUTHCHECK_HOOK:
			logger.trace("entering the hook: {}", TYK_AUTHCHECK_HOOK);
			modifiedRequest = this.doHeaderInjection(request);
			break;
		default:
			logger.error("hook name is not matched: {}", request.getHookName());
		}

		// Return the modified request (if the transformation was done):
		if (modifiedRequest == null) {
			logger.trace("no request is created. responding the empty request...");
			modifiedRequest = request.toBuilder().build();
		}
		
		responseObserver.onNext(modifiedRequest);
		responseObserver.onCompleted();
		logger.trace("Leaving dispatch method");
	}

	private String getSessionId(CoprocessObject.Object request) {
		logger.trace("Incoming headers of request: \n{}", request.getRequest().getHeadersMap());

		String cookie = request.getRequest().getHeadersOrDefault(HTTP_HEADER_COOKIE, "");
		logger.trace("Cookie: {}", cookie);

		if (cookie != null && cookie.length() > 0) {
			String[] tinyCookies = cookie.split(";");
			logger.trace("tinyCookies: {}", Arrays.asList(tinyCookies));
			for (String tinyCookie : tinyCookies) {
				String[] sessionIdCandidate = tinyCookie.split("=");
				logger.trace("sessionIdCandidate: {}", Arrays.asList(sessionIdCandidate));
				if ("JSESSIONID".equals(sessionIdCandidate[0].trim())) {
					logger.trace("Matched JSESSIONID: {}", sessionIdCandidate[1]);
					return sessionIdCandidate[1];
				}
			}
		}

		return null;
	}
	
	private CoprocessObject.Object doForwardToLogin(CoprocessObject.Object.Builder builder) {
		//redirect the request to login URL
		ReturnOverrides retOverrides = builder.getRequestBuilder()
				.getReturnOverridesBuilder()
				.setResponseCode(301)
				.putHeaders(HTTP_HEADER_LOCATION, loginUrl)
				.build();
		
		 MiniRequestObject miniReq = builder.getRequestBuilder().setReturnOverrides(retOverrides).build();
		 return builder.setRequest(miniReq).build();
	}
	
	CoprocessObject.Object doHeaderInjection(CoprocessObject.Object request) {
		logger.trace("Entering doHeaderInjection");
		CoprocessObject.Object.Builder builder = request.toBuilder();

		String sessionId = this.getSessionId(request);
		logger.trace("sessionId: {}", sessionId);

		if (sessionId == null) {
			logger.error("Unable to get session id. request: {}", request);
			return this.doForwardToLogin(builder);
		}

		HashMap<String, String> session = tempSessionStorage.get(sessionId);
		logger.trace("Session: {}", session);

		if (session == null) {
			logger.error("Unable to get session from cache storage. sessionId: {}", sessionId);
			return this.doForwardToLogin(builder);
		}

		String tenantId = session.get(CUSTOM_HEADER_TENANT_ID);
		String pver = session.get(CUSTOM_HEADER_VERSION);

		Map<String, String> metaMap = new HashMap<String, String>();
		metaMap.put(CUSTOM_HEADER_TENANT_ID, tenantId);
		metaMap.put(CUSTOM_HEADER_VERSION, pver);
		
		MiniRequestObject requestObj = builder.getRequestBuilder()
				.putHeaders(CUSTOM_HEADER_TENANT_ID, tenantId)
				.putHeaders(CUSTOM_HEADER_VERSION, pver)
				
				.putSetHeaders(CUSTOM_HEADER_TENANT_ID, tenantId)
				.putSetHeaders(CUSTOM_HEADER_VERSION, pver)
				
				.build();
		
		SessionState sessionState = builder.getSessionBuilder()
				.setLastUpdated(new Date().getTime()+ "")
				.build();
		
		CoprocessObject.Object returnedObject = builder.setRequest(requestObj).setSession(sessionState).putAllMetadata(metaMap).putAllSpec(metaMap).build();
		logger.trace("returnedObject: {}", returnedObject);

		logger.trace("Leaving doHeaderInjection");
		return returnedObject;
	}

}