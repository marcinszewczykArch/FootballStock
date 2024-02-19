#!/bin/sh

echo "Starting script..."

echo "Checking if port 4000 is already allocated"

curl http://localhost:4000 >/dev/null 2>/dev/null
res=$?

if  [ $res = "0" ]; then
    printf "\033[0;31mPort 8000 is already allocated - try to kill amazon/dynamodb-local container!\033[0m\n"
    docker stop $(docker ps -q --filter ancestor=amazon/dynamodb-local )
    echo "docker container [amazon/dynamodb-local] killed with status $?"
fi

printf "\033[0;32mPort 8000 is available\033[0m\n"
printf "starting docker image amazon/dynamodb-local at port 8000"
dockerContainer=$(docker run -d -p 4000:8000 amazon/dynamodb-local -jar DynamoDBLocal.jar -inMemory -sharedDb)

condition=$(curl http://localhost:4000 2>/dev/null)
status=$?

until [ $status = "0" ]; do
    sleep 1
    printf "."
    condition=$(curl http://localhost:4000 2>/dev/null)
    status=$?
done;
echo
echo "docker container [$dockerContainer] is running"

echo "creating playerProfileTable..."
t=$(aws dynamodb create-table --cli-input-json file://tables/playerProfileTable.json --endpoint-url http://localhost:4000 2>&1)
if  [ $? = "0" ]; then
    printf "\033[0;32mdone!\033[0m\n"
else
    printf "\033[0;31m$t\033[0m\n"
fi

echo "creating userGameStateTable..."
t=$(aws dynamodb create-table --cli-input-json file://tables/userGameStateTable.json --endpoint-url http://localhost:4000 2>&1)
if  [ $? = "0" ]; then
    printf "\033[0;32mdone!\033[0m\n"
else
    printf "\033[0;31m$t\033[0m\n"
fi

echo "creating eventTable..."
t=$(aws dynamodb create-table --cli-input-json file://tables/eventTable.json --endpoint-url http://localhost:4000 2>&1)
if  [ $? = "0" ]; then
    printf "\033[0;32mdone!\033[0m\n"
else
    printf "\033[0;31m$t\033[0m\n"
fi

echo "creating clubProfileTable..."
t=$(aws dynamodb create-table --cli-input-json file://tables/clubProfileTable.json --endpoint-url http://localhost:4000 2>&1)
if  [ $? = "0" ]; then
    printf "\033[0;32mdone!\033[0m\n"
else
    printf "\033[0;31m$t\033[0m\n"
fi

echo "creating clubPlayersTable..."
t=$(aws dynamodb create-table --cli-input-json file://tables/clubPlayersTable.json --endpoint-url http://localhost:4000 2>&1)
if  [ $? = "0" ]; then
    printf "\033[0;32mdone!\033[0m\n"
else
    printf "\033[0;31m$t\033[0m\n"
fi

#check created tables
tables=$(aws dynamodb list-tables --endpoint-url http://localhost:4000)
echo "Created tables check: $tables"


#add some initial data to the tables (user, players, events, clubs)
echo "uploading sample player profiles to PlayerProfile Table..."
for file in players/*.json; do
  echo "loading player from file: $file"

  source="Transfermarkt"
  playerId=$(echo $file | cut -d'/' -f 2 | cut -d'.' -f 1)
  json=$(<$file)
  jsonStr=$(echo $json | jq -R)
  body='{"source": {"S": "'"$source"'"}, "playerId": {"N": "'"$playerId"'"}, "json": {"S": '"$jsonStr"'}}'

  echo "${body}" | jq

  aws dynamodb put-item --table-name PlayerProfile --item "${body}" --endpoint-url http://localhost:4000
done

#check numbers of records in PlayerProfile Table
PlayerProfileTable=$(aws dynamodb scan --table-name PlayerProfile --endpoint-url http://localhost:4000)
PlayerProfileTableCount=$(echo $PlayerProfileTable | jq -r '.Count')
echo "PlayerProfile Table Count check: $PlayerProfileTableCount"

echo "uploading TESTUSER to UserGameState Table..."
body='{
        "json": {
            "S": "{  \"user\" : {    \"value\" : \"TESTUSER\"  },  \"wishlist\" : [      ],  \"portfolio\" : [      ],  \"money\" : 1000000,  \"updatedAt\" : \"2024-01-21T18:04:21.899614Z\"}"
        },
        "user": {
            "S": "TESTUSER"
        },
        "updatedAt": {
            "S": "2024-01-21T18:04:21.899614Z"
        }
      }'
echo "${body}" | jq
aws dynamodb put-item --table-name UserGameState --item "${body}" --endpoint-url http://localhost:4000

#check numbers of records in UserGameState Table
UserGameStateTable=$(aws dynamodb scan --table-name UserGameState --endpoint-url http://localhost:4000)
UserGameStateTableCount=$(echo $UserGameStateTable | jq -r '.Count')
echo "UserGameState Table Count check: $UserGameStateTableCount"

echo "uploading InitializeGameEvent for TESTUSER to Event Table..."
body='{
          "json": {
              "S": "{  \"InitializeGameEvent\" : {    \"value\" : 1000000,    \"user\" : {      \"value\" : \"TESTUSER\"    },    \"timestamp\" : \"2024-01-21T18:04:21.899614Z\"  }}"
          },
          "eventName": {
              "S": "INITIALIZE_GAME"
          },
          "eventId": {
              "S": "91c20002-f51c-41d5-8808-89809bc9a75b"
          },
          "user": {
              "S": "TESTUSER"
          }
        }'
echo "${body}" | jq
aws dynamodb put-item --table-name Event --item "${body}" --endpoint-url http://localhost:4000

#check numbers of records in Event Table
EventTable=$(aws dynamodb scan --table-name Event --endpoint-url http://localhost:4000)
EventTableCount=$(echo $EventTable | jq -r '.Count')
echo "Event Table Count check: $EventTableCount"

#kill container at the end
#docker ps
#docker kill $dockerContainer
