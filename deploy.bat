@echo off
echo "Building Docker image..."
docker build -t dfs-app .

echo "Stopping and Removing existing containers..."
docker stop dfs-app-8081
docker rm dfs-app-8081
docker stop dfs-app-8082
docker rm dfs-app-8082
docker stop dfs-app-8083
docker rm dfs-app-8083

echo "Deploying new containers..."
docker run -d -p 8081:8081 --name dfs-app-8081 dfs-app
docker run -d -p 8082:8081 --name dfs-app-8082 dfs-app
docker run -d -p 8083:8081 --name dfs-app-8083 dfs-app

echo "Deployment completed!"
