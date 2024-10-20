@echo off
setlocal

set NETWORK_NAME=mybridge

rem Check if the network '%NETWORK_NAME%' exists
docker network inspect %NETWORK_NAME% >nul 2>&1

if %errorlevel% neq 0 (
    echo Network '%NETWORK_NAME%' does not exist. Creating it...
    docker network create %NETWORK_NAME%
) else (
    echo Network '%NETWORK_NAME%' already exists.
)

rem Stop and remove existing containers
for /L %%i in (1, 1, 5) do (
    docker stop dfs-node-%%i >nul 2>&1
    docker rm dfs-node-%%i >nul 2>&1
)

rem Build the image
echo Building Docker image...
set DOCKER_BUILDKIT=0
docker build --network %NETWORK_NAME% -t dfs-node .

rem Deploy containers with port mappings
echo Deploying containers...
docker run -d --network %NETWORK_NAME% --name dfs-node-1 -e CONTAINER_NAME=dfs-node-1 -e HOST_PORT=8081 -e META_NODE_URL=http://dfs-meta-node:8080 -e RUNTIME_MODE=PRODUCT -p 8081:8081 dfs-node
docker run -d --network %NETWORK_NAME% --name dfs-node-2 -e CONTAINER_NAME=dfs-node-2 -e HOST_PORT=8082 -e META_NODE_URL=http://dfs-meta-node:8080 -e RUNTIME_MODE=PRODUCT -p 8082:8081 dfs-node
docker run -d --network %NETWORK_NAME% --name dfs-node-3 -e CONTAINER_NAME=dfs-node-3 -e HOST_PORT=8083 -e META_NODE_URL=http://dfs-meta-node:8080 -e RUNTIME_MODE=PRODUCT -p 8083:8081 dfs-node
docker run -d --network %NETWORK_NAME% --name dfs-node-4 -e CONTAINER_NAME=dfs-node-4 -e HOST_PORT=8084 -e META_NODE_URL=http://dfs-meta-node:8080 -e RUNTIME_MODE=PRODUCT -p 8084:8081 dfs-node
docker run -d --network %NETWORK_NAME% --name dfs-node-5 -e CONTAINER_NAME=dfs-node-5 -e HOST_PORT=8085 -e META_NODE_URL=http://dfs-meta-node:8080 -e RUNTIME_MODE=PRODUCT -p 8085:8081 dfs-node



echo Deployment completed!
endlocal
