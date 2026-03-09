/**
 * Simulation & Visualization API Test Suite
 * 
 * Comprehensive tests to validate all simulation and visualization endpoints.
 * Run these tests to ensure the API is working correctly.
 * 
 * @doc.type utility
 * @doc.purpose Test and validate simulation/visualization API endpoints
 * @doc.layer product
 */

import {
    projectileMotionSimulation,
    projectileMotionVisualization,
    waterMoleculeSimulation,
    waterMoleculeVisualization,
    cellDivisionSimulation,
    cellDivisionVisualization,
    allExamples
} from './simulationExamples';

/**
 * Test configuration
 */
const API_BASE_URL = 'http://localhost:3200';
const ADMIN_API_PREFIX = '/admin/api/v1/content/db';

// These should be actual IDs from your database
let TEST_DOMAIN_ID = '';
let TEST_CONCEPT_ID = '';

/**
 * Helper function to make API requests
 */
async function apiRequest(
    method: 'GET' | 'POST' | 'DELETE' | 'PATCH',
    path: string,
    body?: any
) {
    const url = `${API_BASE_URL}${path}`;
    const options: RequestInit = {
        method,
        headers: {
            'Content-Type': 'application/json',
        },
    };

    if (body) {
        options.body = JSON.stringify(body);
    }

    const response = await fetch(url, options);
    const data = await response.json();

    return {
        status: response.status,
        ok: response.ok,
        data,
    };
}

/**
 * Test: Create Simulation
 */
export async function testCreateSimulation() {
    console.log('\n📝 TEST: Create Simulation');
    console.log('─'.repeat(50));

    try {
        const response = await apiRequest(
            'POST',
            `${ADMIN_API_PREFIX}/domains/${TEST_DOMAIN_ID}/concepts/${TEST_CONCEPT_ID}/simulation`,
            {
                type: projectileMotionSimulation.type,
                manifest: projectileMotionSimulation.manifest,
                estimatedTimeMinutes: projectileMotionSimulation.estimatedTimeMinutes,
                interactivityLevel: projectileMotionSimulation.interactivityLevel,
                purpose: projectileMotionSimulation.purpose,
                previewConfig: projectileMotionSimulation.previewConfig,
            }
        );

        if (response.ok) {
            console.log('✅ Create Simulation: PASS');
            console.log('Response:', JSON.stringify(response.data, null, 2));
            return response.data;
        } else {
            console.log('❌ Create Simulation: FAIL');
            console.log('Status:', response.status);
            console.log('Error:', response.data);
            return null;
        }
    } catch (error) {
        console.log('❌ Create Simulation: ERROR');
        console.log('Error:', error);
        return null;
    }
}

/**
 * Test: Get Simulation
 */
export async function testGetSimulation() {
    console.log('\n📖 TEST: Get Simulation');
    console.log('─'.repeat(50));

    try {
        const response = await apiRequest(
            'GET',
            `${ADMIN_API_PREFIX}/domains/${TEST_DOMAIN_ID}/concepts/${TEST_CONCEPT_ID}/simulation`
        );

        if (response.ok) {
            console.log('✅ Get Simulation: PASS');
            console.log('Response:', JSON.stringify(response.data, null, 2));
            return response.data;
        } else if (response.status === 404) {
            console.log('⚠️  Get Simulation: NOT FOUND (404)');
            console.log('This is expected if no simulation exists yet');
            return null;
        } else {
            console.log('❌ Get Simulation: FAIL');
            console.log('Status:', response.status);
            console.log('Error:', response.data);
            return null;
        }
    } catch (error) {
        console.log('❌ Get Simulation: ERROR');
        console.log('Error:', error);
        return null;
    }
}

/**
 * Test: Create Visualization
 */
export async function testCreateVisualization() {
    console.log('\n🎨 TEST: Create Visualization');
    console.log('─'.repeat(50));

    try {
        const response = await apiRequest(
            'POST',
            `${ADMIN_API_PREFIX}/domains/${TEST_DOMAIN_ID}/concepts/${TEST_CONCEPT_ID}/visualization`,
            {
                type: projectileMotionVisualization.type,
                config: projectileMotionVisualization.config,
                dataSource: projectileMotionVisualization.dataSource,
            }
        );

        if (response.ok) {
            console.log('✅ Create Visualization: PASS');
            console.log('Response:', JSON.stringify(response.data, null, 2));
            return response.data;
        } else {
            console.log('❌ Create Visualization: FAIL');
            console.log('Status:', response.status);
            console.log('Error:', response.data);
            return null;
        }
    } catch (error) {
        console.log('❌ Create Visualization: ERROR');
        console.log('Error:', error);
        return null;
    }
}

/**
 * Test: Get Visualization
 */
export async function testGetVisualization() {
    console.log('\n📊 TEST: Get Visualization');
    console.log('─'.repeat(50));

    try {
        const response = await apiRequest(
            'GET',
            `${ADMIN_API_PREFIX}/domains/${TEST_DOMAIN_ID}/concepts/${TEST_CONCEPT_ID}/visualization`
        );

        if (response.ok) {
            console.log('✅ Get Visualization: PASS');
            console.log('Response:', JSON.stringify(response.data, null, 2));
            return response.data;
        } else if (response.status === 404) {
            console.log('⚠️  Get Visualization: NOT FOUND (404)');
            console.log('This is expected if no visualization exists yet');
            return null;
        } else {
            console.log('❌ Get Visualization: FAIL');
            console.log('Status:', response.status);
            console.log('Error:', response.data);
            return null;
        }
    } catch (error) {
        console.log('❌ Get Visualization: ERROR');
        console.log('Error:', error);
        return null;
    }
}

/**
 * Test: Get Domain with Simulations/Visualizations
 */
export async function testGetDomainWithContent() {
    console.log('\n🏢 TEST: Get Domain with Content');
    console.log('─'.repeat(50));

    try {
        const response = await apiRequest(
            'GET',
            `${ADMIN_API_PREFIX}/domains/${TEST_DOMAIN_ID}`
        );

        if (response.ok) {
            console.log('✅ Get Domain: PASS');

            // Check if concepts include simulations/visualizations
            if (response.data.concepts && response.data.concepts.length > 0) {
                const conceptWithContent = response.data.concepts.find(
                    (c: any) => c.simulation || c.visualization
                );

                if (conceptWithContent) {
                    console.log('✅ Domain contains concepts with simulations/visualizations');
                    console.log('Sample concept with content:');
                    console.log(JSON.stringify(conceptWithContent, null, 2));
                } else {
                    console.log('⚠️  No concepts with simulations/visualizations found');
                }
            }

            return response.data;
        } else {
            console.log('❌ Get Domain: FAIL');
            console.log('Status:', response.status);
            console.log('Error:', response.data);
            return null;
        }
    } catch (error) {
        console.log('❌ Get Domain: ERROR');
        console.log('Error:', error);
        return null;
    }
}

/**
 * Test: Update Simulation
 */
export async function testUpdateSimulation() {
    console.log('\n🔄 TEST: Update Simulation (Upsert)');
    console.log('─'.repeat(50));

    try {
        const updatedManifest = {
            ...projectileMotionSimulation.manifest,
            initialVelocity: 60, // Changed
            angle: 30, // Changed
            title: 'Updated Projectile Motion Simulation'
        };

        const response = await apiRequest(
            'POST', // Same endpoint for upsert
            `${ADMIN_API_PREFIX}/domains/${TEST_DOMAIN_ID}/concepts/${TEST_CONCEPT_ID}/simulation`,
            {
                type: projectileMotionSimulation.type,
                manifest: updatedManifest,
                estimatedTimeMinutes: 25, // Changed
                interactivityLevel: 'high',
                purpose: 'Updated purpose',
                previewConfig: projectileMotionSimulation.previewConfig,
            }
        );

        if (response.ok) {
            console.log('✅ Update Simulation: PASS');
            console.log('Response:', JSON.stringify(response.data, null, 2));
            return response.data;
        } else {
            console.log('❌ Update Simulation: FAIL');
            console.log('Status:', response.status);
            console.log('Error:', response.data);
            return null;
        }
    } catch (error) {
        console.log('❌ Update Simulation: ERROR');
        console.log('Error:', error);
        return null;
    }
}

/**
 * Test: Delete Simulation
 */
export async function testDeleteSimulation() {
    console.log('\n🗑️  TEST: Delete Simulation');
    console.log('─'.repeat(50));

    try {
        const response = await apiRequest(
            'DELETE',
            `${ADMIN_API_PREFIX}/domains/${TEST_DOMAIN_ID}/concepts/${TEST_CONCEPT_ID}/simulation`
        );

        if (response.status === 204 || response.ok) {
            console.log('✅ Delete Simulation: PASS');
            return true;
        } else if (response.status === 404) {
            console.log('⚠️  Delete Simulation: NOT FOUND (404)');
            return null;
        } else {
            console.log('❌ Delete Simulation: FAIL');
            console.log('Status:', response.status);
            console.log('Error:', response.data);
            return false;
        }
    } catch (error) {
        console.log('❌ Delete Simulation: ERROR');
        console.log('Error:', error);
        return false;
    }
}

/**
 * Test: Delete Visualization
 */
export async function testDeleteVisualization() {
    console.log('\n🗑️  TEST: Delete Visualization');
    console.log('─'.repeat(50));

    try {
        const response = await apiRequest(
            'DELETE',
            `${ADMIN_API_PREFIX}/domains/${TEST_DOMAIN_ID}/concepts/${TEST_CONCEPT_ID}/visualization`
        );

        if (response.status === 204 || response.ok) {
            console.log('✅ Delete Visualization: PASS');
            return true;
        } else if (response.status === 404) {
            console.log('⚠️  Delete Visualization: NOT FOUND (404)');
            return null;
        } else {
            console.log('❌ Delete Visualization: FAIL');
            console.log('Status:', response.status);
            console.log('Error:', response.data);
            return false;
        }
    } catch (error) {
        console.log('❌ Delete Visualization: ERROR');
        console.log('Error:', error);
        return false;
    }
}

/**
 * Test: Multiple Simulation Types
 */
export async function testMultipleSimulationTypes() {
    console.log('\n🔬 TEST: Multiple Simulation Types');
    console.log('─'.repeat(50));

    const tests = allExamples.slice(0, 3); // Test first 3 examples
    const results = [];

    for (const example of tests) {
        console.log(`\nTesting: ${example.name}`);

        try {
            const response = await apiRequest(
                'POST',
                `${ADMIN_API_PREFIX}/domains/${TEST_DOMAIN_ID}/concepts/${TEST_CONCEPT_ID}/simulation`,
                {
                    type: example.simulation.type,
                    manifest: example.simulation.manifest,
                    estimatedTimeMinutes: example.simulation.estimatedTimeMinutes,
                    interactivityLevel: example.simulation.interactivityLevel,
                    purpose: example.simulation.purpose,
                    previewConfig: example.simulation.previewConfig,
                }
            );

            if (response.ok) {
                console.log(`✅ ${example.name}: PASS`);
                results.push({ name: example.name, success: true });
            } else {
                console.log(`❌ ${example.name}: FAIL`);
                results.push({ name: example.name, success: false });
            }
        } catch (error) {
            console.log(`❌ ${example.name}: ERROR`);
            results.push({ name: example.name, success: false });
        }
    }

    return results;
}

/**
 * Run all tests
 */
export async function runAllTests(domainId: string, conceptId: string) {
    TEST_DOMAIN_ID = domainId;
    TEST_CONCEPT_ID = conceptId;

    console.log('\n');
    console.log('╔════════════════════════════════════════════════════════╗');
    console.log('║     SIMULATION & VISUALIZATION API TEST SUITE          ║');
    console.log('╚════════════════════════════════════════════════════════╝');
    console.log(`Domain ID: ${TEST_DOMAIN_ID}`);
    console.log(`Concept ID: ${TEST_CONCEPT_ID}`);

    const results = {
        createSimulation: await testCreateSimulation(),
        getSimulation: await testGetSimulation(),
        createVisualization: await testCreateVisualization(),
        getVisualization: await testGetVisualization(),
        getDomainWithContent: await testGetDomainWithContent(),
        updateSimulation: await testUpdateSimulation(),
        multipleTypes: await testMultipleSimulationTypes(),
        deleteSimulation: await testDeleteSimulation(),
        deleteVisualization: await testDeleteVisualization(),
    };

    console.log('\n');
    console.log('╔════════════════════════════════════════════════════════╗');
    console.log('║                    TEST SUMMARY                        ║');
    console.log('╚════════════════════════════════════════════════════════╝');

    const passCount = Object.values(results).filter(r => r !== null && r !== false).length;
    const totalTests = Object.keys(results).length;

    console.log(`Passed: ${passCount}/${totalTests}`);
    console.log('');

    return results;
}

/**
 * Manual test instructions
 */
export const manualTestInstructions = `
MANUAL TEST INSTRUCTIONS
========================

1. Set Test IDs:
   - Open your database and find a DOMAIN_ID and CONCEPT_ID
   - Update TEST_DOMAIN_ID and TEST_CONCEPT_ID in this file

2. Run Tests:
   - Call: runAllTests(domainId, conceptId)
   - Check console for results

3. Verify Endpoints:
   - All endpoints should return 200/201/204 status
   - Responses should match expected structure
   - No errors in API logs

4. Expected Responses:

   CREATE SIMULATION (201):
   {
     "id": "cuid",
     "conceptId": "concept_id",
     "type": "physics-2D",
     "manifest": {...},
     "status": "DRAFT",
     "version": 1
   }

   GET SIMULATION (200):
   {
     "id": "cuid",
     "conceptId": "concept_id",
     ...same structure...
   }

   DELETE SIMULATION (204):
   No content in response

   CREATE/GET VISUALIZATION:
   Similar structure to simulation

5. Check Database:
   - SELECT * FROM "SimulationDefinition" WHERE "conceptId" = 'concept_id';
   - SELECT * FROM "VisualizationDefinition" WHERE "conceptId" = 'concept_id';
`;
