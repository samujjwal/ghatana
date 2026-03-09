#!/bin/bash
set -e

# Create the new directory structure
mkdir -p proto/dcmaar/v1

# Move proto files to the new location
for file in proto/*.proto; do
  if [ -f "$file" ]; then
    filename=$(basename "$file")
    echo "Processing $filename..."
    
    # Skip validate.proto as it's a dependency
    if [ "$filename" == "validate.proto" ]; then
      continue
    fi
    
    # Create a temporary file for the modified content
    tmpfile=$(mktemp)
    
    # Update the package declaration and add necessary options
    sed -E '
      s/^package dcmaar.v1;/package dcmaar.v1;\n\noption go_package = "github.com\/samujjwal\/dcmaar\/proto\/gen\/go\/dcmaar\/v1;dcmaarv1";\noption java_multiple_files = true;\noption java_outer_classname = "${filename%.*}";\noption java_package = "com.dcmaar.v1";\noption csharp_namespace = "Dcmaar.V1";\noption objc_class_prefix = "DCX";\noption php_namespace = "Dcmaar\\V1";\noption ruby_package = "Dcmaar::V1";/;
      
      # Fix enum naming
      /^enum [A-Za-z]+ {/ {
        :a
        N
        /}/!ba
        s/\([A-Za-z0-9_]+\)\([[:space:]]*=[[:space:]]*[0-9]+\)/\U\1\2/g
      }
      
      # Add _UNSPECIFIED to zero value enums
      s/^([[:space:]]+)([A-Z_]+)[[:space:]]*=[[:space:]]*0;.*/\1\2_UNSPECIFIED = 0;  
      \1\2 = 1;  # TODO: Update this value to be unique/\
      
      # Fix RPC request/response types
      s/rpc \([A-Za-z]+\)\(([A-Za-z]+\.v1\.)?([A-Za-z]+)\) returns \(([A-Za-z]+\.v1\.)?([A-Za-z]+)\)/rpc \1(\2\3Request) returns (\4\5Response)/g
    ' "$file" > "$tmpfile"
    
    # Move the file to the new location
    mv "$tmpfile" "proto/dcmaar/v1/$filename"
  
done

echo "Proto files have been reorganized. Please review the changes and update the RPC method signatures and enum values as needed."
