package de.weiltweitbau.database.actions;

import java.util.HashSet;
import java.util.Set;

import org.bimserver.BimserverDatabaseException;
import org.bimserver.database.DatabaseSession;
import org.bimserver.database.OperationType;
import org.bimserver.database.ProgressHandler;
import org.bimserver.interfaces.objects.SProgressTopicType;
import org.bimserver.longaction.LongAction;
import org.bimserver.models.store.ActionState;
import org.bimserver.shared.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class WwbLongClashDetectorAction extends LongAction<WwbClashDetectionParameters> {
	private static final Logger LOGGER = LoggerFactory.getLogger(WwbClashDetectorAction.class);

	private WwbClashDetectionParameters params;
	private Set<Long> roids;
	
	ObjectNode result = null;

	public WwbLongClashDetectorAction(WwbClashDetectionParameters params) {
		super(params.getBimServer(), params.getUser().getUsername(), params.getUser().getName(), params.getAuthorization());
		
		this.params = params;
		
		setProgressTopic(getBimServer().getNotificationsManager().createProgressTopic(SProgressTopicType.RUNNING_SERVICE, "Clash Detector"));
		changeActionState(ActionState.STARTED, "Done preparing", -1);
		
		roids = new HashSet<>();
		roids.add(params.getRoid1());
		roids.add(params.getRoid2());
	}

	@Override
	public String getDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void execute() {
		try (DatabaseSession session = getBimServer().getDatabase().createSession(OperationType.READ_ONLY)) {
			params.setSession(session);
			
			result = executeAction();
			
			changeActionState(ActionState.FINISHED, "Finished clash detection", 100);
		} catch (Throwable e) {
			LOGGER.error("Could not detect clashes!", e);
			changeActionState(ActionState.AS_ERROR, "Could not detect clashes!", 100);
		}
	}
	
	public ObjectNode executeAction() throws ServiceException, BimserverDatabaseException {
		ClashDetectionProgressHandler progressHandler = new ClashDetectionProgressHandler();
		WwbClashDetectorAction action = new WwbClashDetectorAction(params, progressHandler);
		return action.getDatabaseSession().executeAndCommitAction(action, new ClashDetectionProgressHandler());
	}
	
	public ObjectNode getResult() {
		return result;
	}
	
	public class ClashDetectionProgressHandler implements ProgressHandler {
		public void readGeometries() {
			updateProgress("read geometries", -1);
		}

		@Override
		public void progress(int current, int max) {
			updateProgress("detecting clashes", (current * 100) / max);
		}

		@Override
		public void retry(int count) {
			// we don't do that kind of stuff
		}
	}
}
