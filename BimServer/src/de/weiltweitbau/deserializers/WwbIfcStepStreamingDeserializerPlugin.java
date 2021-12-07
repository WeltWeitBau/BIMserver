package de.weiltweitbau.deserializers;

import java.util.Set;
import java.util.TreeSet;

import org.bimserver.emf.Schema;
import org.bimserver.ifc.step.deserializer.IfcStepStreamingDeserializerPlugin;
import org.bimserver.plugins.PluginConfiguration;
import org.bimserver.plugins.deserializers.StreamingDeserializer;

public class WwbIfcStepStreamingDeserializerPlugin extends IfcStepStreamingDeserializerPlugin {
	
	@Override
	public StreamingDeserializer createDeserializer(PluginConfiguration pluginConfiguration) {
		return new WwbIfcStepStreamingDeserializer();
	}
	
	@Override
	public Set<Schema> getSupportedSchemas() {
		TreeSet<Schema> schemas = new TreeSet<Schema>();
		schemas.add(Schema.IFC2X3TC1);
		schemas.add(Schema.IFC4);
		return schemas;
	}
}