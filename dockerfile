# Use an appropriate base image with JDK 17 installed
FROM maven:3.8.5-openjdk-17 AS build

# Install essential tools
RUN apt-get update && apt-get install -y \
    curl \
    iputils-ping \
    net-tools \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
	
# Set the working directory in the container
WORKDIR /app

# Copy the pom.xml and any other necessary files first
COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw ./

# Install dependencies (this layer will be cached if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline

# Copy the rest of the application code
COPY src ./src

# Build the application
RUN ./mvnw clean install

# Use a lightweight image to run the application
FROM openjdk:17-jdk-slim

# Set the working directory in the new image
WORKDIR /app

# Copy the built JAR file from the build stage
COPY --from=build /app/target/dfs-0.0.1-SNAPSHOT.jar ./dfsnode.jar

# Command to run the application
ENTRYPOINT ["java", "-jar", "dfsnode.jar"]
