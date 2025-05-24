# Stage 1: Define the base runtime environment
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Set timezone if needed (optional, but good practice for consistency)
RUN apk add --no-cache tzdata &&     cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime &&     echo "Asia/Shanghai" > /etc/timezone

# Add bash if your startup script or operations require it (as in reference Dockerfile)
RUN apk add --no-cache bash

# This directory will contain the staged application (bin, lib, etc.)
# The contents will be copied from the SBT stage task output
COPY target/universal/stage /app/

# Define the default command to run the application.
# This assumes your sbt-native-packager setup produces a launch script at /app/bin/<project-name>
# Replace <project-name> with the actual name if known, or it might be 'app' based on reference.
# Also, ensure the config paths are correct if they are baked into the launch script
# or need to be passed as arguments.
# The reference CMD was: ["/bin/bash", "/server/bin/app", "-Dconfig.file=/server/config/application.conf", "-Dlogback.configurationFile=/server/config/logback.xml"]
# We will adapt this. Assuming the app name from sbt build is 'app'.
CMD ["/bin/bash", "/app/bin/app", "-Dconfig.file=/app/conf/application.conf", "-Dlogback.configurationFile=/app/conf/logback.xml"]

# Expose the port the application listens on (e.g., 8080).
# This should match the application's configuration.
EXPOSE 8080
