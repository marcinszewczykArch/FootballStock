#!/bin/sh

echo "Starting script..."

echo "creating playerProfileTable..."
t=$(aws dynamodb create-table --cli-input-json file://tables/playerProfileTable.json 2>&1)
if  [ $? = "0" ]; then
    printf "\033[0;32mdone!\033[0m\n"
else
    printf "\033[0;31m$t\033[0m\n"
fi

echo "creating userGameStateTable..."
t=$(aws dynamodb create-table --cli-input-json file://tables/userGameStateTable.json 2>&1)
if  [ $? = "0" ]; then
    printf "\033[0;32mdone!\033[0m\n"
else
    printf "\033[0;31m$t\033[0m\n"
fi

echo "creating eventTable..."
t=$(aws dynamodb create-table --cli-input-json file://tables/eventTable.json 2>&1)
if  [ $? = "0" ]; then
    printf "\033[0;32mdone!\033[0m\n"
else
    printf "\033[0;31m$t\033[0m\n"
fi

echo "creating clubProfileTable..."
t=$(aws dynamodb create-table --cli-input-json file://tables/clubProfileTable.json 2>&1)
if  [ $? = "0" ]; then
    printf "\033[0;32mdone!\033[0m\n"
else
    printf "\033[0;31m$t\033[0m\n"
fi

echo "creating clubPlayersTable..."
t=$(aws dynamodb create-table --cli-input-json file://tables/clubPlayersTable.json 2>&1)
if  [ $? = "0" ]; then
    printf "\033[0;32mdone!\033[0m\n"
else
    printf "\033[0;31m$t\033[0m\n"
fi

echo "creating loginTable..."
t=$(aws dynamodb create-table --cli-input-json file://tables/loginTable.json 2>&1)
if  [ $? = "0" ]; then
    printf "\033[0;32mdone!\033[0m\n"
else
    printf "\033[0;31m$t\033[0m\n"
fi

echo "creating tokenTable..."
t=$(aws dynamodb create-table --cli-input-json file://tables/tokenTable.json 2>&1)
if  [ $? = "0" ]; then
    printf "\033[0;32mdone!\033[0m\n"
else
    printf "\033[0;31m$t\033[0m\n"
fi

#check created tables
tables=$(aws dynamodb list-tables)
echo "Created tables check: $tables"

#add some initial data to the tables (user, players, events, clubs, login, token)
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
aws dynamodb put-item --table-name UserGameState --item "${body}"

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
aws dynamodb put-item --table-name Event --item "${body}"

echo "uploading logging data for TESTUSER to Login Table..."
body='{
          "json": {
              "S": "{  \"user\" : {      \"value\" : \"TESTUSER\"    },    \"hash\" : \"$2a$10$/aEl5KiiVLLmjEq7fL/wvODk8GInIM.FFe4Ekt3kgzaauSVMfBWqG\",    \"email\" : \"testuser@gmail.com\",    \"role\" : \"ADMIN\"    }"
          },
          "user": {
              "S": "TESTUSER"
          }
        }'
echo "${body}" | jq
aws dynamodb put-item --table-name Login --item "${body}"
