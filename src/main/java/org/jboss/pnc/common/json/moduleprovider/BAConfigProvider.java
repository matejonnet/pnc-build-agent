package org.jboss.pnc.common.json.moduleprovider;

import java.util.ArrayList;
import java.util.List;

import org.jboss.pnc.buildagent.moduleconfig.BuildAgentModuleConfig;
import org.jboss.pnc.common.json.AbstractModuleConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;

public class BAConfigProvider <T extends AbstractModuleConfig> implements ConfigProvider<T> {
    
    private List<ProviderNameType<T>> moduleConfigs;
    private Class<T> type;
    
    public BAConfigProvider(Class<T> type) {
      this.type = type;  
      moduleConfigs = new ArrayList<>();  
      moduleConfigs.add(new ProviderNameType(BuildAgentModuleConfig.class,"build-agent-config"));
    }
    
    /* (non-Javadoc)
     * @see org.jboss.pnc.common.json.moduleprovider.ConfigProviderN#registerProvider(com.fasterxml.jackson.databind.ObjectMapper)
     */
    @Override
    public void registerProvider(ObjectMapper mapper) {
        for (ProviderNameType<T> providerNameType : moduleConfigs) {
            mapper.registerSubtypes(new NamedType(providerNameType.getType(), providerNameType.getTypeName()));
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.pnc.common.json.moduleprovider.ConfigProviderN#getModuleConfigs()
     */
    @Override
    public List<ProviderNameType<T>> getModuleConfigs() {
        return moduleConfigs;
    }
    
    /* (non-Javadoc)
     * @see org.jboss.pnc.common.json.moduleprovider.ConfigProviderN#addModuleConfig(org.jboss.pnc.common.json.moduleprovider.ProviderNameType)
     */
    @Override
    public void addModuleConfig(ProviderNameType<T> providerNameType) {
        this.moduleConfigs.add(providerNameType);
    }

    public Class<T> getType() {
        return type;
    }
    
}
