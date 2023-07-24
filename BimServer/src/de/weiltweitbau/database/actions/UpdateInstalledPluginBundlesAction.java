package de.weiltweitbau.database.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.bimserver.BimServer;
import org.bimserver.BimserverDatabaseException;
import org.bimserver.database.BimserverLockConflictException;
import org.bimserver.database.DatabaseSession;
import org.bimserver.database.actions.PluginBundleDatabaseAction;
import org.bimserver.database.actions.UpdatePluginBundle;
import org.bimserver.interfaces.objects.SPluginBundle;
import org.bimserver.interfaces.objects.SPluginBundleVersion;
import org.bimserver.models.log.AccessMethod;
import org.bimserver.models.store.PluginBundleVersion;
import org.bimserver.plugins.GitHubPluginRepository;
import org.bimserver.plugins.PluginBundle;
import org.bimserver.plugins.PluginBundleIdentifier;
import org.bimserver.plugins.PluginLocation;
import org.bimserver.shared.exceptions.ServerException;
import org.bimserver.shared.exceptions.UserException;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.weiltweitbau.WwbConstants;

public class UpdateInstalledPluginBundlesAction extends PluginBundleDatabaseAction<ObjectNode> {
	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(UpdateInstalledPluginBundlesAction.class);
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final BimServer bimServer;
	private DefaultArtifactVersion bimserverVersion;
	private Map<PluginBundleIdentifier, PluginLocation<?>> repositoryKnownLocation;
	private ThreadPoolExecutor threadPoolExecutor;
	
	private ArrayNode errors;
	private ArrayNode updated;
	private ArrayNode alreadyUpToDate;

	public UpdateInstalledPluginBundlesAction(BimServer bimServer, DatabaseSession databaseSession, AccessMethod accessMethod) {
		super(databaseSession, accessMethod);
		this.bimServer = bimServer;
	}

	@Override
	public ObjectNode execute()
			throws UserException, BimserverLockConflictException, BimserverDatabaseException, ServerException {

		errors = OBJECT_MAPPER.createArrayNode();
		updated = OBJECT_MAPPER.createArrayNode();
		alreadyUpToDate = OBJECT_MAPPER.createArrayNode();
		
		bimserverVersion = new DefaultArtifactVersion(WwbConstants.BIM_SERVER_VERSION);
		getKnownLocations();

		List<SPluginBundleVersion> bundleVersionsForUpdate = collectUpdatablePluginBundles();
		
		updateBundles(bundleVersionsForUpdate);
		
		ObjectNode result = OBJECT_MAPPER.createObjectNode();
		result.set("errors", errors);
		result.set("updated", updated);
		result.set("alreadyUpToDate", alreadyUpToDate);

		return result;
	}
	
	private void updateBundles(List<SPluginBundleVersion> bundleVersionsForUpdate) {
		for(SPluginBundleVersion bundleVersion : bundleVersionsForUpdate) {
			updateBundle(bundleVersion);
		}
	}
	
	private void updateBundle(SPluginBundleVersion bundleVersion) {
		try {
			String repository = bundleVersion.getRepository();
			String groupid = bundleVersion.getGroupId();
			String artifactid = bundleVersion.getArtifactId();
			String version = bundleVersion.getVersion();
			UpdatePluginBundle updateAction = new UpdatePluginBundle(getDatabaseSession(), getAccessMethod(), bimServer,
					repository, groupid, artifactid, version);
			updateAction.execute();
			
			addToResultList(updated, bundleVersion);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			addToResultList(errors, bundleVersion);
		}
	}

	private Map<PluginBundleIdentifier, PluginLocation<?>> getKnownLocations() {
		GitHubPluginRepository repository = new GitHubPluginRepository(bimServer.getMavenPluginRepository(),
				bimServer.getServerSettingsCache().getServerSettings().getServiceRepositoryUrl());

		repositoryKnownLocation = new HashMap<>();
		for (PluginLocation<?> pluginLocation : repository.listPluginLocations()) {
			repositoryKnownLocation.put(pluginLocation.getPluginIdentifier(), pluginLocation);
		}

		return repositoryKnownLocation;
	}

	private List<SPluginBundleVersion> collectUpdatablePluginBundles() throws BimserverDatabaseException {
		List<SPluginBundleVersion> bundleVersionsForUpdate = Collections.synchronizedList(new ArrayList<>());

		threadPoolExecutor = new ThreadPoolExecutor(4, 32, 1L, TimeUnit.HOURS, new ArrayBlockingQueue<>(100));

		for (PluginBundle currentlyInstalledPluginBundle : bimServer.getPluginBundleManager().getPluginBundles()) {
			addUpdatablePluginBundle(currentlyInstalledPluginBundle, bundleVersionsForUpdate);
		}

		threadPoolExecutor.shutdown();

		try {
			threadPoolExecutor.awaitTermination(1, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			LOGGER.error(e.getMessage(), e);
		}

		return bundleVersionsForUpdate;
	}

	private void addUpdatablePluginBundle(PluginBundle currentlyInstalledPluginBundle,
			List<SPluginBundleVersion> bundleVersionsForUpdate) throws BimserverDatabaseException {
		SPluginBundleVersion installedVersion = getInstalledPluginVersion(currentlyInstalledPluginBundle);

		PluginBundleIdentifier pluginBundleIdentifier = new PluginBundleIdentifier(installedVersion.getGroupId(),
				installedVersion.getArtifactId());
		PluginLocation<?> pluginLocation = repositoryKnownLocation.get(pluginBundleIdentifier);

		threadPoolExecutor.submit(new Runnable() {
			@Override
			public void run() {
				SPluginBundle sPluginBundle = processPluginLocation(pluginLocation, true, bimserverVersion);

				if (sPluginBundle == null) {
					return;
				}
				
				SPluginBundleVersion latest = getLatestVersion(sPluginBundle.getAvailableVersions());
				if(latest == null || latest.getVersion().equals(installedVersion.getVersion())) {
					addToResultList(alreadyUpToDate, installedVersion);
					return;
				}
				
				bundleVersionsForUpdate.add(latest);
			}
		});
	}

	private SPluginBundleVersion getLatestVersion(List<SPluginBundleVersion> versions) {
		SPluginBundleVersion latest = null;
		
		for(SPluginBundleVersion version : versions) {
			if(version.getVersion().endsWith("-SNAPSHOT")) {
				continue;
			}
			
			if(latest == null) {
				latest = version;
			}
			
			List<Integer> aLatest = split(latest.getVersion());
			List<Integer> aCurrent = split(version.getVersion());

			for (int i = 0; i < aLatest.size(); i++) {
				if (aLatest.get(i) == aCurrent.get(i)) {
					continue;
				} else if(aCurrent.get(i) > aLatest.get(i)){
					latest = version;
					break;
				}
			}
		}

		return latest;
	}
	
	private List<Integer> split(String in) {
		List<Integer> result = new ArrayList<>();
		for (String s : in.split("\\.")) {
			result.add(Integer.parseInt(s));
		}
		return result;
	}

	private SPluginBundleVersion getInstalledPluginVersion(PluginBundle currentlyInstalledPluginBundle)
			throws BimserverDatabaseException {
		SPluginBundleVersion installedVersion = currentlyInstalledPluginBundle.getPluginBundleVersion();

		for (PluginBundleVersion pluginBundleVersion : getDatabaseSession().getAll(PluginBundleVersion.class)) {
			if (pluginBundleVersion.getArtifactId().equals(installedVersion.getArtifactId())
					&& pluginBundleVersion.getGroupId().equals(installedVersion.getGroupId())
					&& pluginBundleVersion.getVersion().equals(installedVersion.getVersion())) {
				installedVersion.setOid(pluginBundleVersion.getOid());
			}
		}

		return installedVersion;
	}
	
	private void addToResultList(ArrayNode list, SPluginBundleVersion version) {
		try {
			list.add(bimServer.getJsonHandler().getJsonConverter().toJson(version));
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}
}
