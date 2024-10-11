@echo off
setlocal

rem Stop and remove existing containers
for /L %%i in (1, 1, 3) do (
    docker stop dfs-node-%%i
    docker rm dfs-node-%%i
)

rem Build the image
docker build -t dfs-node .

rem Deploy containers with port mappings
docker run -d --name dfs-node-1 -e HOST_PORT=8081 -p 8081:8081 dfs-node
docker run -d --name dfs-node-2 -e HOST_PORT=8082 -p 8082:8081 dfs-node
docker run -d --name dfs-node-3 -e HOST_PORT=8083 -p 8083:8081 dfs-node

echo "Deployment completed!"
endlocal
