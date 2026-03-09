
import { getConfigLoader } from '../services/config-loader.service.js';
import * as path from 'path';
import { fileURLToPath } from 'url';

async function verifyConfig() {
    console.log('Verifying Config Loader...');
    
    const configLoader = getConfigLoader();
    console.log('Config Loader initialized.');

    try {
        console.log('Loading Org Config...');
        const orgConfig = await configLoader.loadOrgConfig();
        console.log('Org Config Loaded:', orgConfig ? 'Success' : 'Failed');
        
        if (orgConfig) {
            console.log(`Loaded ${orgConfig.departments.length} departments.`);
            console.log(`Loaded ${orgConfig.agents.length} agents.`);
            
            if (orgConfig.departments.length > 0) {
                console.log('First Department:', orgConfig.departments[0].name);
            }
        }
        
        console.log('Loading Departments directly...');
        const departments = await configLoader.loadAllDepartments();
        console.log(`Loaded ${departments.length} departments directly.`);
        
    } catch (error) {
        console.error('Error verifying config:', error);
    }
}

verifyConfig();
