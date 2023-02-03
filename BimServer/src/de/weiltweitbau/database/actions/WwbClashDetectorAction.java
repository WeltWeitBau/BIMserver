package de.weiltweitbau.database.actions;

import org.bimserver.BimServer;
import org.bimserver.BimserverDatabaseException;
import org.bimserver.database.BimserverLockConflictException;
import org.bimserver.database.DatabaseSession;
import org.bimserver.database.OldQuery;
import org.bimserver.database.actions.BimDatabaseAction;
import org.bimserver.database.actions.DownloadDatabaseAction;
import org.bimserver.emf.IfcModelInterface;
import org.bimserver.emf.ModelMetaData;
import org.bimserver.interfaces.SConverter;
import org.bimserver.models.geometry.Bounds;
import org.bimserver.models.log.AccessMethod;
import org.bimserver.models.store.ConcreteRevision;
import org.bimserver.models.store.Revision;
import org.bimserver.shared.exceptions.ServerException;
import org.bimserver.shared.exceptions.UserException;
import org.bimserver.webservices.authorization.Authorization;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.weiltweitbau.database.actions.clashes.ClashDetectionResults;
import de.weiltweitbau.database.actions.clashes.ClashDetector;
import de.weiltweitbau.database.actions.clashes.ClashDetectorRules;

public class WwbClashDetectorAction extends BimDatabaseAction<ObjectNode> {
	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(WwbClashDetectorAction.class);
	
	private final long roid1;
	private final long roid2;
	
	private final String rules;

	private final BimServer bimServer;
	private Authorization authorization;
	private long serializerOid;

	public WwbClashDetectorAction(BimServer bimServer, DatabaseSession databaseSession, AccessMethod accessMethod,
			Authorization authorization, long serializerOid, long roid1, long roid2, String rules) {
		super(databaseSession, accessMethod);
		this.bimServer = bimServer;
		this.authorization = authorization;
		this.serializerOid = serializerOid;
		this.roid1 = roid1;
		this.roid2 = roid2;
		this.rules = rules;
	}

	@Override
	public ObjectNode execute()
			throws UserException, BimserverLockConflictException, BimserverDatabaseException, ServerException {
		IfcModelInterface model1 = new DownloadDatabaseAction(bimServer, getDatabaseSession(), getAccessMethod(), roid1,
				-1, serializerOid, authorization).execute();
		IfcModelInterface model2 = model1;

		try {
			setBounds(model1, roid1);
			
			if(roid1 != roid2) {
				model2 = new DownloadDatabaseAction(bimServer, getDatabaseSession(), getAccessMethod(), roid2,
						-1, serializerOid, authorization).execute();
				
				setBounds(model2, roid2);
			}
			
			ClashDetectorRules clashDetectionRules = new ObjectMapper().readValue(rules, ClashDetectorRules.class);
			
			ClashDetector clashDetector = new ClashDetector(model1, model2, clashDetectionRules);
			ClashDetectionResults results =  clashDetector.findClashes();
			
			return results.toJson();
		} catch (Exception e) {
			LOGGER.error("", e);
			throw new UserException(e);
		}
	}

	private void setBounds(IfcModelInterface model, long roid) throws BimserverDatabaseException {
		Revision revision = getDatabaseSession().get(roid, OldQuery.getDefault());
		ConcreteRevision lastConcreteRevision = revision.getLastConcreteRevision();
		Bounds bounds = lastConcreteRevision.getBounds();
		ModelMetaData modelMetaData = model.getModelMetaData();
		
		SConverter converter = bimServer.getSConverter();		
		
		modelMetaData.setMinBounds(converter.convertToSObject(bounds.getMin()));
		modelMetaData.setMaxBounds(converter.convertToSObject(bounds.getMax()));
	}
}
