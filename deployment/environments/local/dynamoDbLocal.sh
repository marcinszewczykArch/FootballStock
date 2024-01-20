#!/bin/sh

echo "Starting script..."

echo "Checking if port 8000 is already allocated"

curl http://localhost:8000 >/dev/null 2>/dev/null
res=$?

if  [ $res = "0" ]; then
    printf "\033[0;31mPort 8000 is already allocated\033[0m\n"
else
    printf "\033[0;32mPort 8000 is available\033[0m\n"
    printf "starting docker image amazon/dynamodb-local at port 8000"
    dockerContainer=$(docker run -d -p 8000:8000 amazon/dynamodb-local -jar DynamoDBLocal.jar -inMemory -sharedDb)

    condition=$(curl http://localhost:8000 2>/dev/null)
    status=$?

    until [ $status = "0" ]; do
        sleep 1
        printf "."
        condition=$(curl http://localhost:8000 2>/dev/null)
        status=$?
    done;
    echo
    echo "docker container [$dockerContainer] is running"
fi

echo "creating playerProfileTable..."
t=$(aws dynamodb create-table --cli-input-json file://tables/playerProfileTable.json --endpoint-url http://localhost:8000 2>&1)
if  [ $? = "0" ]; then
    printf "\033[0;32mdone!\033[0m\n"
else
    printf "\033[0;31m$t\033[0m\n"
fi

echo "creating userGameStateTable..."
t=$(aws dynamodb create-table --cli-input-json file://tables/userGameStateTable.json --endpoint-url http://localhost:8000 2>&1)
if  [ $? = "0" ]; then
    printf "\033[0;32mdone!\033[0m\n"
else
    printf "\033[0;31m$t\033[0m\n"
fi

echo "creating eventTable..."
t=$(aws dynamodb create-table --cli-input-json file://tables/eventTable.json --endpoint-url http://localhost:8000 2>&1)
if  [ $? = "0" ]; then
    printf "\033[0;32mdone!\033[0m\n"
else
    printf "\033[0;31m$t\033[0m\n"
fi

#add some initial data to the tables (user, players, events)
echo "uploading playerProfiles to playerProfileTable..."
for file in players/*.json; do
  echo "loading player from file: $file"

  source="Transfermarkt"
  playerId=$(echo $file | cut -d'/' -f 2 | cut -d'.' -f 1)
  json=$(<$file)
  jsonStr=$(echo $json | jq -R)
  body='{"source": {"S": "'"$source"'"}, "playerId": {"N": "'"$playerId"'"}, "json": {"S": '"$jsonStr"'}}'

  echo "${body}" | jq

  aws dynamodb put-item --table-name PlayerProfile --item "${body}" --endpoint-url http://localhost:8000
done


#check created tables
tables=$(aws dynamodb list-tables --endpoint-url http://localhost:8000)
echo "Created tables check: $tables"

#check numbers of records in PlayerProfile Table
PlayerProfileTable=$(aws dynamodb scan --table-name PlayerProfile --endpoint-url http://localhost:8000)
PlayerProfileTableCount=$(echo $PlayerProfileTable | jq -r '.Count')
echo "PlayerProfile Table Count check: $PlayerProfileTableCount"

#kill container at the end
docker ps
docker kill $dockerContainer