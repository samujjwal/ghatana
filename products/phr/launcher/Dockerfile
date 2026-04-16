FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY . .

ENV GRADLE_OPTS="-Xms256m -Xmx512m -Dfile.encoding=UTF-8"

RUN chmod +x gradlew && ./gradlew :products:phr:launcher:installDist \
	-x test \
	--no-daemon \
	--no-configuration-cache \
	--max-workers=2 \
	-Dorg.gradle.parallel=false \
	-Dorg.gradle.caching=false \
	-Dorg.gradle.jvmargs="-Xms512m -Xmx2g -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC -Dfile.encoding=UTF-8"

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY --from=build /workspace/products/phr/launcher/build/install/launcher /app

ENTRYPOINT ["/app/bin/launcher"]