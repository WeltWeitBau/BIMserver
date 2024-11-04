package de.weiltweitbau.database.actions;

import org.bimserver.BimServer;
import org.bimserver.database.DatabaseSession;
import org.bimserver.interfaces.objects.SUser;
import org.bimserver.models.log.AccessMethod;
import org.bimserver.webservices.authorization.Authorization;

public class WwbClashDetectionParameters {
	private BimServer bimServer;
	private AccessMethod accessMethod;
	private DatabaseSession session;
	private SUser user;
	private Authorization authorization;
	private long serializerOid;
	
	private long roid1;
	private long roid2;
	private String rules;
	
	public WwbClashDetectionParameters(long roid1,  long roid2,  String strRules,
			BimServer bimServer, AccessMethod accessMethod, DatabaseSession session, SUser user,
			Authorization authorization, long serializerOid) {
		this.roid1 = roid1;
		this.roid2 = roid2;
		this.rules = strRules;
		
		this.bimServer = bimServer;
		this.accessMethod = accessMethod;
		this.session = session;
		this.user = user;
		this.authorization = authorization;
		this.serializerOid = serializerOid;
	}
	
	public BimServer getBimServer() {
		return bimServer;
	}
	
	public void setSession(DatabaseSession session) {
		this.session = session;
	}

	public DatabaseSession getSession() {
		return session;
	}

	public SUser getUser() {
		return user;
	}

	public AccessMethod getAccessMethod() {
		return accessMethod;
	}
	
	public Authorization getAuthorization() {
		return authorization;
	}
	
	public long getSerializerOid() {
		return serializerOid;
	}
	
	public long getRoid1() {
		return roid1;
	}
	
	public long getRoid2() {
		return roid2;
	}
	
	public String getRules() {
		return rules;
	}
}
