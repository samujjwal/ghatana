import os
import re

column_map = {
    '"tenant_id"': 'DataCloudColumnNames.TENANT_ID',
    '"name"': 'DataCloudColumnNames.NAME',
    '"label"': 'DataCloudColumnNames.LABEL',
    '"description"': 'DataCloudColumnNames.DESCRIPTION',
    '"record_type"': 'DataCloudColumnNames.RECORD_TYPE',
    '"fields"': 'DataCloudColumnNames.FIELDS',
    '"validation_schema"': 'DataCloudColumnNames.VALIDATION_SCHEMA',
    '"storage_profile"': 'DataCloudColumnNames.STORAGE_PROFILE',
    '"storage_config"': 'DataCloudColumnNames.STORAGE_CONFIG',
    '"event_config"': 'DataCloudColumnNames.EVENT_CONFIG',
    '"retention_policy"': 'DataCloudColumnNames.RETENTION_POLICY',
    '"permissions"': 'DataCloudColumnNames.PERMISSIONS',
    '"schema_version"': 'DataCloudColumnNames.SCHEMA_VERSION',
    '"version"': 'DataCloudColumnNames.VERSION',
    '"active"': 'DataCloudColumnNames.ACTIVE',
    '"created_at"': 'DataCloudColumnNames.CREATED_AT',
    '"created_by"': 'DataCloudColumnNames.CREATED_BY',
    '"updated_at"': 'DataCloudColumnNames.UPDATED_AT',
    '"updated_by"': 'DataCloudColumnNames.UPDATED_BY',
    '"metadata"': 'DataCloudColumnNames.METADATA',
    '"entity_data"': 'DataCloudColumnNames.ENTITY_DATA',
    '"collection_id"': 'DataCloudColumnNames.COLLECTION_ID',
    '"status"': 'DataCloudColumnNames.STATUS'
}

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    original = content
    for old, new in column_map.items():
        content = content.replace(f'name = {old}', f'name = {new}')

    if original != content:
        if 'import com.ghatana.datacloud.entity.DataCloudColumnNames' not in content:
            lines = content.split('\n')
            import_idx = -1
            package_idx = -1
            for i, line in enumerate(lines):
                if line.startswith('package '):
                    package_idx = i
                if line.startswith('import '):
                    import_idx = i
                    break
            if import_idx != -1:
                lines.insert(import_idx, 'import com.ghatana.datacloud.entity.DataCloudColumnNames;')
            elif package_idx != -1:
                lines.insert(package_idx + 1, '\nimport com.ghatana.datacloud.entity.DataCloudColumnNames;')
            content = '\n'.join(lines)
            
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"Updated {filepath}")

for root, dirs, files in os.walk('products/data-cloud/platform-entity'):
    for file in files:
        if file.endswith('.java') and file != 'DataCloudColumnNames.java':
            process_file(os.path.join(root, file))
