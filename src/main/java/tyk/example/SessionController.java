package tyk.example;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {
	
	@GetMapping("/session/{id}")
	public SessionVo getSessionCache(@PathVariable String id) {
		HashMap<String, String> session= TykPluginDispatcher.tempSessionStorage.get(id);
		return this.convert(id, session);
	}
	
	private SessionVo convert(String id, HashMap<String, String> session) {
		SessionVo vo = new SessionVo();
		vo.setSessionId(id);
		vo.setSessionMap(session);
		return vo;
	}
	
	class SessionVo implements Serializable{

		private static final long serialVersionUID = -24099301373091674L;
		
		private String sessionId;
		private Map<String, String> sessionMap;
		
		public String getSessionId() {
			return sessionId;
		}
		public void setSessionId(String sessionId) {
			this.sessionId = sessionId;
		}
		public Map<String, String> getSessionMap() {
			return sessionMap;
		}
		public void setSessionMap(Map<String, String> sessionMap) {
			this.sessionMap = sessionMap;
		}

	}
}
