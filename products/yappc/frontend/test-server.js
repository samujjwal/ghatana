const http = require('http');
const fs = require('fs');
const path = require('path');

const CONFIG_BASE_PATH = path.join(__dirname, '..', '..', '..', 'config', 'yappc');

// Simple YAML parser for basic structures
const parseYaml = (content) => {
    try {
        // Very basic YAML parsing - just extract basic key-value pairs and arrays
        const lines = content.split('\n');
        const result = {};
        let currentKey = null;
        let currentArray = [];
        let inArray = false;

        for (let line of lines) {
            line = line.trim();
            if (!line || line.startsWith('#')) continue;

            if (line.includes(':') && !line.startsWith('-')) {
                if (inArray && currentKey) {
                    result[currentKey] = currentArray;
                    currentArray = [];
                    inArray = false;
                }

                const [key, ...valueParts] = line.split(':');
                currentKey = key.trim();
                const value = valueParts.join(':').trim();

                if (value === '' || value === '[]') {
                    inArray = true;
                    currentArray = [];
                } else if (value.startsWith('[') && value.endsWith(']')) {
                    result[currentKey] = JSON.parse(value);
                } else if (value) {
                    result[currentKey] = value.replace(/['"]/g, '');
                }
            } else if (line.startsWith('-') && inArray) {
                const item = line.substring(1).trim();
                try {
                    currentArray.push(JSON.parse(item));
                } catch {
                    currentArray.push(item.replace(/['"]/g, ''));
                }
            }
        }

        if (inArray && currentKey) {
            result[currentKey] = currentArray;
        }

        return result;
    } catch (error) {
        console.error('YAML parsing error:', error);
        return null;
    }
};

// Helper to load YAML files
const loadYamlFile = (filename) => {
    try {
        const filePath = path.join(CONFIG_BASE_PATH, filename);
        const fileContent = fs.readFileSync(filePath, 'utf8');
        return parseYaml(fileContent);
    } catch (error) {
        console.error(`Error loading ${filename}:`, error);
        return null;
    }
};

// HTTP server
const server = http.createServer((req, res) => {
    // Add CORS headers
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    const url = new URL(req.url, `http://${req.headers.host}`);
    const pathname = url.pathname;

    res.setHeader('Content-Type', 'application/json');

    if (pathname === '/api/config/domains') {
        const domains = loadYamlFile('domains.yaml');
        if (domains) {
            res.writeHead(200);
            res.end(JSON.stringify(domains.domains || []));
        } else {
            res.writeHead(500);
            res.end(JSON.stringify({ error: 'Failed to load domains' }));
        }
    } else if (pathname.startsWith('/api/config/domains/')) {
        const id = pathname.split('/').pop();
        const domains = loadYamlFile('domains.yaml');
        if (domains) {
            const domain = domains.domains?.find(d => d.id === id);
            if (domain) {
                res.writeHead(200);
                res.end(JSON.stringify(domain));
            } else {
                res.writeHead(404);
                res.end(JSON.stringify({ error: 'Domain not found' }));
            }
        } else {
            res.writeHead(500);
            res.end(JSON.stringify({ error: 'Failed to load domains' }));
        }
    } else if (pathname === '/api/config/workflows') {
        const workflows = loadYamlFile('workflows.yaml');
        if (workflows) {
            res.writeHead(200);
            res.end(JSON.stringify(workflows.workflows || []));
        } else {
            res.writeHead(500);
            res.end(JSON.stringify({ error: 'Failed to load workflows' }));
        }
    } else if (pathname.startsWith('/api/config/workflows/')) {
        const id = pathname.split('/').pop();
        const workflows = loadYamlFile('workflows.yaml');
        if (workflows) {
            const workflow = workflows.workflows?.find(w => w.id === id);
            if (workflow) {
                res.writeHead(200);
                res.end(JSON.stringify(workflow));
            } else {
                res.writeHead(404);
                res.end(JSON.stringify({ error: 'Workflow not found' }));
            }
        } else {
            res.writeHead(500);
            res.end(JSON.stringify({ error: 'Failed to load workflows' }));
        }
    } else if (pathname === '/api/config/lifecycle') {
        const lifecycle = loadYamlFile('lifecycle.yaml');
        if (lifecycle) {
            res.writeHead(200);
            res.end(JSON.stringify(lifecycle));
        } else {
            res.writeHead(500);
            res.end(JSON.stringify({ error: 'Failed to load lifecycle config' }));
        }
    } else if (pathname === '/api/config/agents') {
        const agents = loadYamlFile('agents.yaml');
        if (agents) {
            res.writeHead(200);
            res.end(JSON.stringify(agents));
        } else {
            res.writeHead(500);
            res.end(JSON.stringify({ error: 'Failed to load agent capabilities' }));
        }
    } else if (pathname === '/api/config/tasks') {
        const domains = loadYamlFile('domains.yaml');
        const workflows = loadYamlFile('workflows.yaml');

        let allTasks = [];

        // Extract tasks from domains
        if (domains && domains.domains) {
            domains.domains.forEach(domain => {
                if (domain.tasks) {
                    allTasks = allTasks.concat(domain.tasks.map(task => ({
                        ...task,
                        source: 'domain',
                        domainId: domain.id,
                        domainName: domain.name
                    })));
                }
            });
        }

        // Extract tasks from workflows
        if (workflows && workflows.workflows) {
            workflows.workflows.forEach(workflow => {
                if (workflow.phases) {
                    workflow.phases.forEach(phase => {
                        if (phase.tasks) {
                            allTasks = allTasks.concat(phase.tasks.map(task => ({
                                ...task,
                                source: 'workflow',
                                workflowId: workflow.id,
                                workflowName: workflow.name,
                                phaseId: phase.id,
                                phaseName: phase.name
                            })));
                        }
                    });
                }
            });
        }

        res.writeHead(200);
        res.end(JSON.stringify(allTasks));
    } else {
        res.writeHead(404);
        res.end(JSON.stringify({ error: 'Not found' }));
    }
});

// Start server
const PORT = process.env.PORT || 8080;
server.listen(PORT, () => {
    console.log(`Test config server running on port ${PORT}`);
    console.log(`Config base path: ${CONFIG_BASE_PATH}`);
});