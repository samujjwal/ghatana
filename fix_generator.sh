#!/bin/bash
FILE="/Users/samujjwal/Development/ghatana/products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/PlatformContentGenerator.java"
export FILE
sed -i '' 's/request\.topic()/request.getTopic()/g' "$FILE"
sed -i '' 's/request\.gradeLevel()/request.getGradeLevel()/g' "$FILE"
sed -i '' 's/request\.domain()/request.getDomain()/g' "$FILE"
sed -i '' 's/request\.maxClaims()/request.getMaxClaims()/g' "$FILE"
sed -i '' 's/request\.claims()/request.getClaims()/g' "$FILE"

sed -i '' 's/claimsResponse\.claims()/claimsResponse.getClaims()/g' "$FILE"
sed -i '' 's/examples\.examples()/examples.getExamples()/g' "$FILE"
sed -i '' 's/simulations\.simulations()/simulations.getSimulations()/g' "$FILE"
sed -i '' 's/animations\.animations()/animations.getAnimations()/g' "$FILE"
sed -i '' 's/assessments\.assessments()/assessments.getAssessments()/g' "$FILE"

sed -i '' 's/examples\.validation()/examples.getValidation()/g' "$FILE"
sed -i '' 's/simulations\.validation()/simulations.getValidation()/g' "$FILE"
sed -i '' 's/animations\.validation()/animations.getValidation()/g' "$FILE"
sed -i '' 's/assessments\.validation()/assessments.getValidation()/g' "$FILE"

