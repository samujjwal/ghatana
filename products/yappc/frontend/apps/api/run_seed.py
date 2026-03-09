import subprocess
import os

try:
    result = subprocess.run(
        ["npx", "tsx", "prisma/seed.ts"],
        cwd="/home/samujjwal/Developments/ghatana/products/yappc/app-creator/apps/api",
        capture_output=True,
        text=True,
        check=False
    )
    
    with open("/home/samujjwal/Developments/ghatana/products/yappc/app-creator/apps/api/seed_output_python.txt", "w") as f:
        f.write("STDOUT:\n")
        f.write(result.stdout)
        f.write("\nSTDERR:\n")
        f.write(result.stderr)
        
    print("Execution finished. Return code:", result.returncode)

except Exception as e:
    with open("/home/samujjwal/Developments/ghatana/products/yappc/app-creator/apps/api/seed_output_python.txt", "w") as f:
        f.write(f"EXCEPTION: {str(e)}")
    print("Execution failed with exception")
