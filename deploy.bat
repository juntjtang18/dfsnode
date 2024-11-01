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

rem Deploy containers with port mappings and volumes
echo Deploying containers...
for /L %%i in (1, 1, 5) do (
    docker run -d --network %NETWORK_NAME% --name dfs-node-%%i -e CONTAINER_NAME=dfs-node-%%i -e HOST_PORT=808%%i -e META_NODE_URL=http://dfs-meta-node:8080 -e RUNTIME_MODE=PRODUCT -p 808%%i:8081 -v dfs-node-%%i-data:/data dfs-node
)

echo Deployment completed!
endlocal
