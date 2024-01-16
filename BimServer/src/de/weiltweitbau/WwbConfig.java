package de.weiltweitbau;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.bimserver.BimServerConfig;
import org.slf4j.LoggerFactory;

public class WwbConfig {
	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(WwbConfig.class);
	
	private static final String TRUE = "true";
	
	private static Properties properties = null;
	private static long lastModified = 0;
	
	public static boolean readBooleanValue(String key, boolean defaultValue, BimServerConfig config) {
		String value = getProperty(key, config);
		return value == null ? defaultValue : value.equals(TRUE);
	}
	
	private static String getProperty(String key, BimServerConfig config) {
		readWwbProperties(config);
		
		if(properties == null) {
			return null;
		}
		
		return properties.getProperty(key);
	}

	public static void readWwbProperties(BimServerConfig config) {
		try {
			Path propertiesFile = config.getHomeDir().resolve("wwb.properties");

			if (!Files.exists(propertiesFile)) {
				return;
			}
			
			long modified = Files.getLastModifiedTime(propertiesFile).toMillis();
			
			if(modified <= lastModified) {
				return;
			}

			try (InputStream inputStream = Files.newInputStream(propertiesFile)) {
				properties = new Properties();
				properties.load(inputStream);
			}
			
			lastModified = modified;
		} catch (Exception e) {
			LOGGER.error("Could not read wwb.properties!", e);
		}
	}
}
